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
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError
import retrofit.client.Client

@Component
class VerifyQuipTask extends AbstractQuipTask implements Task {

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  Client retrofitClient

  @Override
  TaskResult execute(Stage stage) {
    String cluster = stage.context?.clusterName
    String region = stage.context?.region
    String account = stage.context?.account
    Map instances = stage.context?.instances
    ArrayList healthProviders = stage.context?.healthProviders
    Map stageOutputs = [:]
    ExecutionStatus executionStatus = ExecutionStatus.SUCCEEDED
    if (cluster && region && account && healthProviders != null && instances) {
      stageOutputs.put("interestingHealthProviderNames", healthProviders) // for waitForUpInstanceHealthTask

      if(!checkInstancesForQuip(instances)) {
        throw new RuntimeException("quip is not running on all instances : ${instances}")
      }
    } else {
      throw new RuntimeException("one or more of these parameters is missing : cluster || region || account || healthProviders")
    }
    return TaskResult.builder(executionStatus).context(stageOutputs).build()
  }

  private boolean checkInstancesForQuip(Map instances) {
    // verify that /tasks endpoint responds
    boolean allInstancesHaveQuip = true
    instances.each { String key, Map valueMap ->
      String hostName = valueMap.privateIpAddress ?: valueMap.hostName
      def instanceService = createInstanceService("http://${hostName}:5050")
      try {
        instanceService.listTasks()
      } catch(RetrofitError e) {
        allInstancesHaveQuip = false
      }
    }
    return allInstancesHaveQuip
  }
}
