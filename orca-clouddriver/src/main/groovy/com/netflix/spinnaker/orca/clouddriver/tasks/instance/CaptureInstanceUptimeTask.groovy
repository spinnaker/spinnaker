/*
 * Copyright 2016 Netflix, Inc.
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

import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.RetryableTask
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.commands.InstanceUptimeCommand
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class CaptureInstanceUptimeTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  long backoffPeriod = 15000
  long timeout = 300000

  @Autowired(required = false)
  InstanceUptimeCommand instanceUptimeCommand;

  @Autowired
  CloudDriverService cloudDriverService

  @Override
  TaskResult execute(StageExecution stage) {
    if (!instanceUptimeCommand) {
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context([instanceUptimes: [:]]).build()
    }

    def cloudProvider = getCloudProvider(stage)
    def region = stage.context.region as String
    def account = (stage.context.account ?: stage.context.credentials) as String

    def instanceUptimes = stage.context.instanceIds.inject([:]) { Map accumulator, String instanceId ->
      def instance = cloudDriverService.getInstance(account, region, instanceId)
      try {
        accumulator[instanceId] = instanceUptimeCommand.uptime(cloudProvider, instance).seconds
      } catch (Exception e) {
        log.warn("Unable to capture uptime (instanceId: ${instanceId}), reason: ${e.message}")
      }

      return accumulator
    }

    TaskResult.builder(ExecutionStatus.SUCCEEDED).context([
      "instanceUptimes": instanceUptimes
    ]).build()
  }
}
