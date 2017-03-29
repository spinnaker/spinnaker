/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.kato.tasks.quip

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Client

@Component
class InstanceHealthCheckTask extends AbstractQuipTask implements RetryableTask  {
  @Autowired ObjectMapper objectMapper

  long backoffPeriod = 10000
  long timeout = 3600000 // 60min

  @Autowired
  OortHelper oortHelper

  @Autowired
  Client retrofitClient

  @Override
  TaskResult execute(Stage stage) {
    Map stageOutputs = [:]
    def instances = stage.context?.instances

    ExecutionStatus executionStatus = ExecutionStatus.SUCCEEDED
    //skipped instances
    if (!instances) {
      return new TaskResult(ExecutionStatus.SUCCEEDED)
    }
    // verify instance list, package, and version are in the context
    if(instances) {
      // trigger patch on target server
      for (instanceEntry in instances) {
        def instance = instanceEntry.value
        if (!instance.healthCheckUrl || instance.healthCheckUrl.isEmpty()) {
          // ask kato for a refreshed version of the instance info
          instances = oortHelper.getInstancesForCluster(stage.context, null, true, false)
          stageOutputs << [instances: instances]
          return new TaskResult(ExecutionStatus.RUNNING, stageOutputs)
        }

        URL healthCheckUrl = new URL(instance.healthCheckUrl)
        def host = instance.privateIpAddress ?: healthCheckUrl.host
        def instanceService = createInstanceService("http://${host}:${healthCheckUrl.port}")
        try { // keep trying until we get a 200 or time out
          instanceService.healthCheck(healthCheckUrl.path.substring(1))
        } catch(RetrofitError e) {
          executionStatus = ExecutionStatus.RUNNING
        }
      }
    } else {
      throw new RuntimeException("one or more required parameters are missing : instances")
    }
    return new TaskResult(executionStatus, stageOutputs)
  }
}
