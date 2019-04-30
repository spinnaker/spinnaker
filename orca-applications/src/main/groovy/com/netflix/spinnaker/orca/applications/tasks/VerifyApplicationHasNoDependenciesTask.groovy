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

package com.netflix.spinnaker.orca.applications.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.pipeline.model.Stage
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import retrofit.RetrofitError

@Component
@Slf4j
class VerifyApplicationHasNoDependenciesTask implements Task {
  @Autowired
  OortService oortService

  @Autowired
  MortService mortService

  @Autowired
  ObjectMapper objectMapper

  @Override
  TaskResult execute(Stage stage) {
    def application = objectMapper.convertValue(stage.context.application, Application)

    def existingDependencyTypes = []
    try {
      def oortResult = getOortResult(application.name as String)
      if (oortResult && oortResult.clusters) {
        existingDependencyTypes << "clusters"
      }

      def mortResults = getMortResults(application.name as String, "securityGroups")
      if (mortResults.find {
        it.application.equalsIgnoreCase(application.name)
      }) {
        existingDependencyTypes << "security groups"
      }
    } catch (RetrofitError e) {
      if (!e.response) {
        def exception = [operation: stage.tasks[-1].name, reason: e.message]
        return TaskResult.builder(ExecutionStatus.TERMINAL).context([exception: exception]).build()
      } else if (e.response && e.response.status && e.response.status != 404) {
        def resp = e.response
        def exception = [statusCode: resp.status, operation: stage.tasks[-1].name, url: resp.url, reason: resp.reason]
        try {
          exception.details = e.getBodyAs(Map) as Map
        } catch (ignored) {
        }

        return TaskResult.builder(ExecutionStatus.TERMINAL).context([exception: exception]).build()
      }
    }

    if (!existingDependencyTypes) {
      return TaskResult.ofStatus(ExecutionStatus.SUCCEEDED)
    }

    return TaskResult.builder(ExecutionStatus.TERMINAL).context([exception: [
        details: [
            error: "Application has outstanding dependencies",
            errors: existingDependencyTypes.collect {
              "Application is associated with one or more ${it}" as String
            }
        ]
    ]]).build()
  }

  protected Map getOortResult(String applicationName) {
    def oortResponse = oortService.getApplication(applicationName)
    return objectMapper.readValue(oortResponse.body.in().text, Map)
  }

  protected List<Map> getMortResults(String applicationName, String type) {
    def mortResults = mortService.getSearchResults(applicationName, type)
    return mortResults ? mortResults[0].results : []
  }
}
