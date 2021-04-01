/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.utils.HealthHelper
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
@CompileStatic
class WaitForDownInstanceHealthTask implements OverridableTimeoutRetryableTask {
  long backoffPeriod = 5000
  long timeout = 3600000

  @Autowired
  CloudDriverService cloudDriverService

  @Override
  TaskResult execute(StageExecution stage) {
    InstanceHealthCheckInputs inputs = stage.mapTo(InstanceHealthCheckInputs)
    return process(inputs)
  }

  TaskResult process(InstanceHealthCheckInputs inputs) {
    List<String> healthProviderTypesToCheck = inputs.getInterestingHealthProviderNames()

    if (inputs.hasEmptyInterestingHealthProviders()) {
      return TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)
    }

    if (!inputs.hasInstanceIds()) {
      return TaskResult.ofStatus(ExecutionStatus.TERMINAL)
    }

    String region = inputs.getRegion()
    String account = inputs.accountToUse()

    def stillRunning = inputs.getInstanceIds().find {
      def instance = cloudDriverService.getInstance(account, region, it)
      return !HealthHelper.someAreDownAndNoneAreUp(instance, healthProviderTypesToCheck)
    }

    return TaskResult.ofStatus(stillRunning ? ExecutionStatus.RUNNING : ExecutionStatus.SUCCEEDED)
  }

  boolean hasSucceeded(Map instance, Collection<String> interestedHealthProviderNames) {
    return HealthHelper.someAreDownAndNoneAreUp(instance, interestedHealthProviderNames)
  }
}
