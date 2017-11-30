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

import java.util.function.Function
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForRequiredInstancesDownTask
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.beans.factory.annotation.Value

@Component
class WaitForClusterDisableTask extends AbstractWaitForClusterWideClouddriverTask implements CloudProviderAware {

  @Value('${tasks.disableClusterMinTimeMillis:90000}')
  private int MINIMUM_WAIT_TIME_MS

  private final Map<String, String> healthProviderNamesByPlatform

  @Autowired
  WaitForRequiredInstancesDownTask waitForRequiredInstancesDownTask

  @Autowired
  public WaitForClusterDisableTask(Collection<ServerGroupCreator> serverGroupCreators) {
    healthProviderNamesByPlatform = serverGroupCreators.findAll { serverGroupCreator ->
      serverGroupCreator.healthProviderName.isPresent()
    }.collectEntries { serverGroupCreator ->
      return [(serverGroupCreator.cloudProvider): serverGroupCreator.healthProviderName.orElse(null)]
    }
  }

  @Override
  TaskResult execute(Stage stage) {
    def taskResult = super.execute(stage)

    def duration = System.currentTimeMillis() - stage.startTime
    if (taskResult.status == ExecutionStatus.SUCCEEDED && duration < MINIMUM_WAIT_TIME_MS) {
      // wait at least MINIMUM_WAIT_TIME to account for any necessary connection draining to occur
      return new TaskResult(ExecutionStatus.RUNNING, taskResult.context, taskResult.outputs)
    }

    return taskResult
  }

  @Override
  boolean isServerGroupOperationInProgress(Stage stage,
                                           List<Map> interestingHealthProviderNames,
                                           Optional<TargetServerGroup> serverGroup) {
    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      return false
    }

    // Assume a missing server group is disabled.
    boolean isDisabled = serverGroup.map({ it.disabled } as Function<TargetServerGroup, Boolean>).orElse(true)

    // If the server group shows as disabled, we don't need to do anything special w.r.t. interestingHealthProviderNames.
    if (isDisabled) {
      return false
    } else {
      def targetServerGroup = serverGroup.get()
      if (stage.context.desiredPercentage) {
        // TODO(lwander) investigate if the non-desiredPercentage case code can be dropped below in favor of this
        return !waitForRequiredInstancesDownTask.hasSucceeded(stage, targetServerGroup as Map, targetServerGroup.getInstances(), interestingHealthProviderNames)
      }

      // The operation can be considered complete if it was requested to only consider the platform health.
      def platformHealthType = targetServerGroup.instances.collect { instance ->
        HealthHelper.findPlatformHealth(instance.health)
      }?.find {
        it.type
      }?.type

      if (!platformHealthType) {
        platformHealthType = healthProviderNamesByPlatform[getCloudProvider(stage)]
      }

      return !(platformHealthType && interestingHealthProviderNames == [platformHealthType])
    }
  }
}
