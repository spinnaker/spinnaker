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

package com.netflix.spinnaker.orca.clouddriver.tasks

import com.netflix.frigga.Names
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.Location
import com.netflix.spinnaker.orca.clouddriver.pipeline.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.transform.Canonical
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractWaitForClusterWideClouddriverTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  @Override
  public long getBackoffPeriod() { 10000 }

  @Override
  public long getTimeout() { 1800000 }

  @Autowired
  OortHelper oortHelper

  protected TaskResult missingClusterResult(Stage stage,
                                            AbstractClusterWideClouddriverTask.ClusterSelection clusterSelection) {
    throw new IllegalStateException("no cluster details found for $clusterSelection")
  }

  protected TaskResult emptyClusterResult(Stage stage,
                                          AbstractClusterWideClouddriverTask.ClusterSelection clusterSelection,
                                          Map cluster) {
    throw new IllegalStateException("No ServerGroups found in cluster $clusterSelection")
  }

  boolean isServerGroupOperationInProgress(List<TargetServerGroup> currentServerGroups,
                                           List<Map> interestingHealthProviderNames,
                                           DeployServerGroup deployServerGroup) {
    isServerGroupOperationInProgress(interestingHealthProviderNames,
                                     Optional.ofNullable(currentServerGroups.find {
                                       // Possible issue here for GCE if multiple server groups are named the same in
                                       // different zones but with the same region. However, this is not allowable by
                                       // Spinnaker constraints, so we're accepting the risk.
                                       it.region == deployServerGroup.region && it.name == deployServerGroup.name
                                     }))
  }

  abstract boolean isServerGroupOperationInProgress(List<Map> interestingHealthProviderNames,
                                                    Optional<TargetServerGroup> serverGroup)

  @Canonical
  static class DeployServerGroup {
    String region
    String name
  }

  static class RemainingDeployServerGroups {
    List<DeployServerGroup> remainingDeployServerGroups = []
  }

  @Override
  TaskResult execute(Stage stage) {

    def clusterSelection = stage.mapTo(AbstractClusterWideClouddriverTask.ClusterSelection)

    List<DeployServerGroup> remainingDeployServerGroups = stage.mapTo(RemainingDeployServerGroups).remainingDeployServerGroups

    if (!remainingDeployServerGroups) {
      Map<String, List<String>> dsg = stage.context.'deploy.server.groups' as Map
      remainingDeployServerGroups = dsg?.collect { String region, List<String> groups ->
        groups?.collect { new DeployServerGroup(region, it) } ?: []
      }?.flatten() ?: []
    }

    if (!remainingDeployServerGroups) {
      return DefaultTaskResult.SUCCEEDED
    }

    def names = Names.parseName(clusterSelection.cluster)

    Optional<Map> cluster = oortHelper.getCluster(names.app, clusterSelection.credentials, clusterSelection.cluster, clusterSelection.cloudProvider)
    if (!cluster.isPresent()) {
      return missingClusterResult(stage, clusterSelection)
    }

    def serverGroups = cluster.get().serverGroups.collect { new TargetServerGroup(serverGroup: it) }

    if (!serverGroups) {
      return emptyClusterResult(stage, clusterSelection, cluster.get())
    }

    List<String> healthProviderTypesToCheck = stage.context.interestingHealthProviderNames as List<String>
    List<DeployServerGroup> stillRemaining = remainingDeployServerGroups.findAll(this.&isServerGroupOperationInProgress.curry(serverGroups, healthProviderTypesToCheck))

    if (stillRemaining) {
      return new DefaultTaskResult(ExecutionStatus.RUNNING, [remainingDeployServerGroups: stillRemaining])
    }

    return DefaultTaskResult.SUCCEEDED
  }
}
