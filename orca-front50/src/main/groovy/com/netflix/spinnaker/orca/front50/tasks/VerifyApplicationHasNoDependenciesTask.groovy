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

package com.netflix.spinnaker.orca.front50.tasks

import groovy.util.logging.Slf4j
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.DefaultTaskResult
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.front50.model.Application
import com.netflix.spinnaker.orca.mort.MortService
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Stage
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
    def account = (stage.context.account as String).toLowerCase()

    def existingDependencyTypes = []
    try {
      def oortResult = getOortResult(application.name as String)
      if (oortResult && oortResult.clusters[account]) {
        if (oortResult.clusters[account]*."loadBalancers".flatten()) {
          existingDependencyTypes << "load balancers"
        }

        if (oortResult.clusters[account]*."serverGroups".flatten()) {
          existingDependencyTypes << "server groups"
        }
      }

      def mortResults = getMortResults(application.name as String, "securityGroups")
      if (mortResults.find {
        it.application.equalsIgnoreCase(application.name) && it.account == account
      }) {
        existingDependencyTypes << "security groups"
      }
    } catch (RetrofitError e) {
      if (e.response.status != 404) {
        def resp = e.response
        def exception = [statusCode: resp.status, operation: stage.tasks[-1].name, url: resp.url, reason: resp.reason]
        try {
          exception.details = e.getBodyAs(Map) as Map
        } catch (ignored) {
        }

        return new DefaultTaskResult(ExecutionStatus.TERMINAL, [exception: exception])
      }
    }

    if (!existingDependencyTypes) {
      return new DefaultTaskResult(ExecutionStatus.SUCCEEDED)
    }

    return new DefaultTaskResult(ExecutionStatus.TERMINAL, [exception: [
        details: [
            error: "Application has outstanding dependencies",
            errors: existingDependencyTypes.collect {
              "Application is associated with one or more ${it}" as String
            }
        ]
    ]])
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
