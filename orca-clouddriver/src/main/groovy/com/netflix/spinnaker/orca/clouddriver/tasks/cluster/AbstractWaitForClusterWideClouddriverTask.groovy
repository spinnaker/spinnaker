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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.cluster.AbstractClusterWideClouddriverOperationStage.ClusterSelection
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Canonical
import groovy.transform.ToString
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value

@Slf4j
abstract class AbstractWaitForClusterWideClouddriverTask extends AbstractCloudProviderAwareTask implements OverridableTimeoutRetryableTask {
  @Override
  public long getBackoffPeriod() { 10000 }

  @Value('${tasks.waitForClusterTimeoutMillis:1800000}')
  public long defaultTimeout

  @Override
  public long getTimeout() {
    return this.defaultTimeout
  }

  @Autowired
  OortHelper oortHelper

  protected TaskResult missingClusterResult(Stage stage,
                                            ClusterSelection clusterSelection) {
    throw new IllegalStateException("no cluster details found for $clusterSelection")
  }

  protected TaskResult emptyClusterResult(Stage stage,
                                          ClusterSelection clusterSelection,
                                          Map cluster) {
    throw new IllegalStateException("no server groups found in cluster $clusterSelection")
  }

  boolean isServerGroupOperationInProgress(Stage stage,
                                           List<TargetServerGroup> currentServerGroups,
                                           List<Map> interestingHealthProviderNames,
                                           DeployServerGroup deployServerGroup) {
    def matchingServerGroups = Optional.ofNullable(currentServerGroups.find {
      // Possible issue here for GCE if multiple server groups are named the same in
      // different zones but with the same region. However, this is not allowable by
      // Spinnaker constraints, so we're accepting the risk.
      log.info "currentServerGroup.region: $it.region, currentServerGroup.name: $it.name"
      log.info " deployServerGroup.region: $it.region,  deployServerGroup.name: $it.name"
      def isMatch = it.region == deployServerGroup.region && it.name == deployServerGroup.name
      log.info "is match? $isMatch"
      isMatch
    })
    log.info "Server groups matching $deployServerGroup : $matchingServerGroups"
    isServerGroupOperationInProgress(stage, interestingHealthProviderNames, matchingServerGroups)
  }

  abstract boolean isServerGroupOperationInProgress(Stage stage,
                                                    List<Map> interestingHealthProviderNames,
                                                    Optional<TargetServerGroup> serverGroup)

  @Canonical
  @ToString(includeNames = true, includePackage = false)
  static class DeployServerGroup {
    String region
    String name
  }

  static class RemainingDeployServerGroups {
    List<DeployServerGroup> remainingDeployServerGroups = []
  }

  @Override
  TaskResult execute(Stage stage) {
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

    Optional<Map> cluster = oortHelper.getCluster(clusterSelection.getApplication(), clusterSelection.credentials, clusterSelection.cluster, clusterSelection.cloudProvider)
    if (!cluster.isPresent()) {
      return missingClusterResult(stage, clusterSelection)
    }

    def serverGroups = cluster.get().serverGroups.collect { new TargetServerGroup(it) }
    log.info "Pipeline ${stage.execution?.id} found server groups ${serverGroups.collect { it.region + "->" + it.name }}"
    log.info "Pipeline ${stage.execution?.id} is looking for ${remainingDeployServerGroups.collect { it.region + "->" + it.name }}"

    if (!serverGroups) {
      return emptyClusterResult(stage, clusterSelection, cluster.get())
    }

    List<String> healthProviderTypesToCheck = stage.context.interestingHealthProviderNames as List<String>
    List<DeployServerGroup> stillRemaining = remainingDeployServerGroups.findAll(this.&isServerGroupOperationInProgress.curry(stage, serverGroups, healthProviderTypesToCheck))

    if (stillRemaining) {
      log.info "Pipeline ${stage.execution?.id} still has ${stillRemaining.collect { it.region + "->" + it.name }}"
      return new TaskResult(ExecutionStatus.RUNNING, [remainingDeployServerGroups: stillRemaining])
    }

    log.info "Pipeline ${stage.execution?.id} no server groups remain"
    return TaskResult.SUCCEEDED
  }
}
