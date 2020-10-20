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
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.pipeline.servergroup.support.TargetServerGroup
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.ServerGroupCreator
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForRequiredInstancesDownTask
import com.netflix.spinnaker.orca.clouddriver.utils.CloudProviderAware
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.core.env.Environment
import org.springframework.stereotype.Component
import org.springframework.beans.factory.annotation.Value

@Component
class WaitForClusterDisableTask extends AbstractWaitForClusterWideClouddriverTask implements CloudProviderAware {
  // to make the logic more readable, we map true and false to words
  private static final boolean RUNNING = true
  private static final boolean COMPLETED = false
  static final String TOGGLE = "tasks.wait-for-cluster-disable.enforce-disabled-flag.enabled"

  @Value('${tasks.disable-cluster-min-time-millis:90000}')
  private int MINIMUM_WAIT_TIME_MS

  private final Map<String, String> healthProviderNamesByPlatform

  @Autowired
  WaitForRequiredInstancesDownTask waitForRequiredInstancesDownTask

  @Autowired
  Environment environment

  @Autowired
  public WaitForClusterDisableTask(Collection<ServerGroupCreator> serverGroupCreators) {
    healthProviderNamesByPlatform = serverGroupCreators.findAll { serverGroupCreator ->
      serverGroupCreator.healthProviderName.isPresent()
    }.collectEntries { serverGroupCreator ->
      return [(serverGroupCreator.cloudProvider): serverGroupCreator.healthProviderName.orElse(null)]
    }
  }

  @Override
  TaskResult execute(StageExecution stage) {
    def taskResult = super.execute(stage)

    def duration = System.currentTimeMillis() - stage.startTime
    if (stage.context['deploy.server.groups'] && taskResult.status == ExecutionStatus.SUCCEEDED && duration < MINIMUM_WAIT_TIME_MS) {
      // wait at least MINIMUM_WAIT_TIME to account for any necessary connection draining to occur if there were actually server groups
      return TaskResult.builder(ExecutionStatus.RUNNING).context(taskResult.context).outputs(taskResult.outputs).build()
    }

    return taskResult
  }

  @Override
  boolean isServerGroupOperationInProgress(StageExecution stage,
                                           List<Map> interestingHealthProviderNames,
                                           Optional<TargetServerGroup> serverGroup) {
    // null vs empty interestingHealthProviderNames do mean very different things to Spinnaker
    // a null value will result in Spinnaker waiting for discovery + platform, etc. whereas an empty will not wait for anything.
    if (interestingHealthProviderNames != null && interestingHealthProviderNames.isEmpty()) {
      return COMPLETED
    }

    if (!serverGroup.isPresent()) {
      return COMPLETED
    }

    // make sure that we wait for the disabled flag to be set
    def targetServerGroup = serverGroup.get()
    if (environment.getProperty(TOGGLE, Boolean, false) && !targetServerGroup.isDisabled()) {
      return RUNNING
    }

    // we want to make sure instances are down
    // to prevent downstream stages (e.g. scaleDownCluster) from having to deal with disabled-but-instances-up server groups
    // note that waitForRequiredInstancesDownTask knows how to deal with desiredPercentages, interestingHealthProviderNames, etc.
    return !waitForRequiredInstancesDownTask.hasSucceeded(stage, targetServerGroup as Map, targetServerGroup.getInstances(), interestingHealthProviderNames)
  }
}
