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

package com.netflix.spinnaker.orca.kato.tasks.shrink

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
class WaitForShrunkenClusterTask implements RetryableTask {

  long backoffPeriod = 1000
  long timeout = 7200000

  @Autowired
  ObjectMapper mapper

  @Autowired
  OortService oortService

  @Override
  TaskResult execute(Stage stage) {
    def data = stage.mapTo(StageData)
    try {
      def existingAsgs = getExistingAsgs(data.application, data.credentials, data.clusterName, data.providerType)
      if (existingAsgs.every { it.instances.size() }) {
        return new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
      }
      return new DefaultTaskResult(ExecutionStatus.RUNNING)
    } catch (RetrofitError e) {
      return (e.response.status != 404) ? new DefaultTaskResult(ExecutionStatus.FAILED) :
        new DefaultTaskResult(ExecutionStatus.RUNNING)
    }
  }

  static class StageData {
    String application
    String clusterName
    String region
    String credentials
    String providerType = "aws"
  }

  private List<Map> getExistingAsgs(String app, String account, String cluster, String providerType) {
    try {
      def response = oortService.getCluster(app, account, cluster, providerType)
      def json = response.body.in().text
      def map = mapper.readValue(json, Map)
      map.serverGroups as List<Map>
    } catch (e) {
      null
    }
  }
}
