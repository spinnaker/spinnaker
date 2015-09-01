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
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class WaitForTerminatedInstancesTask implements RetryableTask {
  long backoffPeriod = 1000
  long timeout = 3600000

  @Autowired
  OortService oortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    List<String> instanceIds = stage.context."terminate.instance.ids"

    if (!instanceIds || !instanceIds.size()) {
      return new DefaultTaskResult(ExecutionStatus.FAILED)
    }
    def notAllTerminated = instanceIds.find { String instanceId ->
      def response = oortService.getSearchResults(instanceId, "serverGroupInstances", "aws")
      if (response.status != 200) {
        return true
      }
      def searchResult = objectMapper.readValue(response.body.in().text, List)
      if (!searchResult || searchResult.size() != 1) {
        return true
      }
      Map searchResultSet = (Map) searchResult[0]
      if (searchResultSet.totalMatches != 0) {
        return true
      }
      return false
    }

    def status = notAllTerminated ? ExecutionStatus.RUNNING : ExecutionStatus.SUCCEEDED

    new DefaultTaskResult(status)
  }
}
