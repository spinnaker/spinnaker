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

  protected TaskResult missingClusterResult() {
    DefaultTaskResult.SUCCEEDED
  }

  protected TaskResult emptyClusterResult() {
    DefaultTaskResult.SUCCEEDED
  }

  boolean isServerGroupOperationInProgress(List<Map> currentServerGroups, DeployServerGroup deployServerGroup) {
    isServerGroupOperationInProgress(Optional.ofNullable(currentServerGroups.find { it.region == deployServerGroup.region && it.name == deployServerGroup.name }))
  }

  abstract boolean isServerGroupOperationInProgress(Optional<Map> serverGroup)

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

    def config = stage.mapTo(AbstractClusterWideClouddriverTask.ClusterSelection)
    def names = Names.parseName(config.cluster)

    Optional<Map> cluster = oortHelper.getCluster(names.app, config.credentials, config.cluster, config.cloudProvider)
    if (!cluster.isPresent()) {
      return missingClusterResult()
    }

    def serverGroups = cluster.get().serverGroups

    if (!serverGroups) {
      return emptyClusterResult()
    }

    List<DeployServerGroup> stillRemaining = remainingDeployServerGroups.findAll(this.&isServerGroupOperationInProgress.curry(serverGroups))

    if (stillRemaining) {
      return new DefaultTaskResult(ExecutionStatus.RUNNING, [remainingDeployServerGroups: stillRemaining])
    }

    return DefaultTaskResult.SUCCEEDED
  }
}
