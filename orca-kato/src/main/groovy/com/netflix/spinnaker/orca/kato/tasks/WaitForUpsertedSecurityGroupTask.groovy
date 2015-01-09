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

package com.netflix.spinnaker.orca.kato.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.mort.MortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

/**
 * Note: this is a bit buggy. It only checks if the security group has changed in any way,
 * not that the security group has changed to the requested state
 */
@Component
class WaitForUpsertedSecurityGroupTask implements RetryableTask {

  long backoffPeriod = 1000
  long timeout = 600000

  @Autowired
  MortService mortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    String account = stage.context.account
    String region = stage.context.region
    String name = stage.context.name
    String oldValue = stage.context."pre.response" ?: null

    if (!account || !region || !name) {
      return new DefaultTaskResult(ExecutionStatus.FAILED)
    }

    def mortResponse = mortService.getSecurityGroup(account, 'aws', name, region)

    def currentValue = UpsertSecurityGroupTask.parseCurrentValue(mortResponse)

    def changeDetected = currentValue != oldValue

    def status = changeDetected ? ExecutionStatus.SUCCEEDED : ExecutionStatus.RUNNING

    new DefaultTaskResult(status)
  }
}
