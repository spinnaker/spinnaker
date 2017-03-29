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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.AbstractCloudProviderAwareTask
import com.netflix.spinnaker.orca.commands.InstanceUptimeCommand
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Slf4j
@Component
class VerifyInstanceUptimeTask extends AbstractCloudProviderAwareTask implements RetryableTask {
  long backoffPeriod = 30000
  long timeout = 600000

  @Autowired(required = false)
  InstanceUptimeCommand instanceUptimeCommand;

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    if (!instanceUptimeCommand || !stage.context.instanceUptimes) {
      return new TaskResult(ExecutionStatus.SUCCEEDED)
    }

    def cloudProvider = getCloudProvider(stage)
    def account = (stage.context.account ?: stage.context.credentials) as String
    def region = stage.context.region as String

    def instanceUptimes = stage.context.instanceUptimes as Map<String, Integer>
    def allInstancesHaveRebooted = instanceUptimes.every { String instanceId, int uptime ->
      def instance = getInstance(account, region, instanceId);

      try {
        InstanceUptimeCommand.InstanceUptimeResult result = instanceUptimeCommand.uptime(cloudProvider, instance)
        return result.seconds < uptime
      } catch (Exception e) {
        log.warn("Unable to determine uptime for ${instance.instanceId} via ${instanceUptimeCommand.class.simpleName}, reason: ${e.message}")
        return false
      }
    }

    return new TaskResult(allInstancesHaveRebooted ? ExecutionStatus.SUCCEEDED : ExecutionStatus.RUNNING)
  }

  protected Map getInstance(String account, String region, String instanceId) {
    def response = oortService.getInstance(account, region, instanceId)
    return objectMapper.readValue(response.body.in().text, Map)
  }
}
