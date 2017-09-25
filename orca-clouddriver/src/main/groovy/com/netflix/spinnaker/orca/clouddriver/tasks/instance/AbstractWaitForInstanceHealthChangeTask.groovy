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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired

abstract class AbstractWaitForInstanceHealthChangeTask implements OverridableTimeoutRetryableTask {
  long backoffPeriod = 5000
  long timeout = 3600000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    if (stage.context.interestingHealthProviderNames != null && ((List)stage.context.interestingHealthProviderNames).isEmpty()) {
      return new TaskResult(ExecutionStatus.SUCCEEDED)
    }

    String region = stage.context.region as String
    String account = (stage.context.account ?: stage.context.credentials) as String
    List<String> healthProviderTypesToCheck = stage.context.interestingHealthProviderNames as List<String>

    def instanceIds = getInstanceIds(stage)
    if (!instanceIds) {
      return new TaskResult(ExecutionStatus.TERMINAL)
    }

    def stillRunning = instanceIds.find {
      def instance = getInstance(account, region, it)
      return !hasSucceeded(instance, healthProviderTypesToCheck)
    }

    return new TaskResult(stillRunning ? ExecutionStatus.RUNNING : ExecutionStatus.SUCCEEDED)
  }

  protected List<String> getInstanceIds(Stage stage) {
    return (List<String>) stage.context.instanceIds
  }

  protected Map getInstance(String account, String region, String instanceId) {
    def response = oortService.getInstance(account, region, instanceId)
    return objectMapper.readValue(response.body.in().text, Map)
  }

  abstract boolean hasSucceeded(Map instance, Collection<String> interestedHealthProviderNames);
}
