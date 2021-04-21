/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.model.Cluster
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.AbstractClusterWideClouddriverOperationStage.ClusterSelection
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware

import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import groovy.transform.Canonical
import groovy.transform.ToString
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

abstract class AbstractWaitForClusterWideClouddriverTask implements CloudProviderAware, OverridableTimeoutRetryableTask {
  private Logger log = LoggerFactory.getLogger(getClass())

  @Override
  public long getBackoffPeriod() { 10000 }

  @Value('${tasks.wait-for-cluster-timeout-millis:1800000}')
  public long defaultTimeout

  @Override
  public long getTimeout() {
    return this.defaultTimeout
  }

  @Autowired
  CloudDriverService cloudDriverService

  protected TaskResult missingClusterResult(StageExecution stage,
                                            ClusterSelection clusterSelection) {
    throw new IllegalStateException("no cluster details found for $clusterSelection")
  }

  protected TaskResult emptyClusterResult(StageExecution stage,
                                          ClusterSelection clusterSelection,
                                          Cluster cluster) {
    throw new IllegalStateException("no server groups found in cluster $clusterSelection")
  }

  boolean isServerGroupOperationInProgress(StageExecution stage,
                                           List<TargetServerGroup> currentServerGroups,
                                           List<Map> interestingHealthProviderNames,
                                           DeployServerGroup deployServerGroup) {
    def matchingServerGroups = Optional.ofNullable(currentServerGroups.find {
      // Possible issue here for GCE if multiple server groups are named the same in
      // different zones but with the same region. However, this is not allowable by
      // Spinnaker constraints, so we're accepting the risk.
      def isMatch = it.region == deployServerGroup.region && it.name == deployServerGroup.name
      isMatch
    })

    log.info("Server groups matching $deployServerGroup : $matchingServerGroups")
    isServerGroupOperationInProgress(stage, interestingHealthProviderNames, matchingServerGroups)
  }

  abstract boolean isServerGroupOperationInProgress(StageExecution stage,
                                                    List<Map> interestingHealthProviderNames,
                                                    Optional<TargetServerGroup> serverGroup)

  @Canonical
  @ToString(includeNames = true, includePackage = false)
  static class DeployServerGroup {
    String region
    String name

    @Override
    String toString() {
      return "${region}->${name}"
    }
  }

  static class RemainingDeployServerGroups {
    List<DeployServerGroup> remainingDeployServerGroups = []
  }

  @Override
  TaskResult execute(StageExecution stage) {
    def clusterSelection = stage.mapTo(ClusterSelection)

    List<DeployServerGroup> remainingDeployServerGroups = stage.mapTo(RemainingDeployServerGroups).remainingDeployServerGroups

    if (!remainingDeployServerGroups) {
      Map<String, List<String>> dsg = stage.context.'deploy.server.groups' as Map
      remainingDeployServerGroups = dsg?.collect { String region, List<String> groups ->
        groups?.collect { new DeployServerGroup(region, it) } ?: []
      }?.flatten() ?: []
    }

    if (!remainingDeployServerGroups) {
      return TaskResult.SUCCEEDED
    }

    Optional<Cluster> cluster = cloudDriverService.maybeCluster(clusterSelection.getApplication(), clusterSelection.credentials, clusterSelection.cluster, clusterSelection.cloudProvider)
    if (!cluster.isPresent()) {
      return missingClusterResult(stage, clusterSelection)
    }

    def serverGroups = cluster.get().serverGroups.collect { new TargetServerGroup(it) }
    log.info "Pipeline ${stage.execution?.id} looking for server groups: $remainingDeployServerGroups found: $serverGroups"

    if (!serverGroups) {
      return emptyClusterResult(stage, clusterSelection, cluster.get())
    }

    List<String> healthProviderTypesToCheck = stage.context.interestingHealthProviderNames as List<String>
    List<DeployServerGroup> stillRemaining = remainingDeployServerGroups.findAll {
      isServerGroupOperationInProgress(stage, serverGroups, healthProviderTypesToCheck, it)
    }

    if (stillRemaining) {
      log.info "Pipeline ${stage.execution?.id} still has $stillRemaining"
      return TaskResult.builder(ExecutionStatus.RUNNING).context([remainingDeployServerGroups: stillRemaining]).build()
    }

    log.info "Pipeline ${stage.execution?.id} no server groups remain"
    return TaskResult.SUCCEEDED
  }
}
