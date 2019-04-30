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
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Client

@Component
class MonitorQuipTask extends AbstractQuipTask implements RetryableTask {
  @Autowired ObjectMapper objectMapper

  @Autowired Client retrofitClient

  long backoffPeriod = 10000
  long timeout = 1200000 // 20mins

  /**
   * TODO: make this more efficient by only polling the instances which are not done.
   * I'm not dealing with it now since I think this will be used on a small scale, 1-2 instances at a time
   */
  @Override
  TaskResult execute(Stage stage) {
    def result = TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)

    //we skipped instances that were up to date
    if (!stage.context.instances) {
      return result
    }

    if(!stage.context.taskIds) {
      throw new RuntimeException("missing taskIds")
    }

    stage.context?.instances.each {String key, Map valueMap ->
      String hostName = valueMap.privateIpAddress ?: valueMap.hostName
      def taskId = stage.context.taskIds.get(hostName)
      def instanceService = createInstanceService("http://${hostName}:5050")
      try {
        def instanceResponse = instanceService.listTask(taskId)
        def status = objectMapper.readValue(instanceResponse.body.in().text, Map).status
        if(status == "Successful") {
          // noop unless they all succeeded
        } else if(status == "Failed") {
          throw new RuntimeException("quip task failed for ${hostName} with a result of ${status}, see http://${hostName}:5050/tasks/${taskId}")
        } else if(status == "Running") {
          result = TaskResult.ofStatus(ExecutionStatus.RUNNING)
        } else {
          throw new RuntimeException("quip task failed for ${hostName} with a result of ${status}, see http://${hostName}:5050/tasks/${taskId}")
        }
      } catch(RetrofitError e) {
        result = TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }
    }
    return result
  }


}
