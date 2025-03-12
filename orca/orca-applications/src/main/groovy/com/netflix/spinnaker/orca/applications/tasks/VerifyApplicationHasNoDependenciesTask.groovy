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
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.Task
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.MortService
import com.netflix.spinnaker.orca.front50.model.Application
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import javax.annotation.Nonnull

@Component
@Slf4j
class VerifyApplicationHasNoDependenciesTask implements Task {
  @Autowired
  CloudDriverService cloudDriverService

  @Autowired
  MortService mortService

  @Autowired
  ObjectMapper objectMapper

  @Nonnull
  @Override
  TaskResult execute(@Nonnull StageExecution stage) {
    def application = objectMapper.convertValue(stage.context.application, Application)

    def existingDependencyTypes = []
    try {
      def oortResult = cloudDriverService.getApplication(application.name as String)
      if (oortResult && oortResult.clusters) {
        existingDependencyTypes << "clusters"
      }

      def mortResults = getMortResults(application.name as String, "securityGroups")
      if (mortResults.find {
        it.application.equalsIgnoreCase(application.name)
      }) {
        existingDependencyTypes << "security groups"
      }
    } catch (SpinnakerHttpException e) {
      if (e.getResponseCode() != 404) {
        def exception = [statusCode: e.getResponseCode(), operation: stage.tasks[-1].name, url: e.getUrl(), reason: e.getReason()]
        exception.details = e.getResponseBody()
        return TaskResult.builder(ExecutionStatus.TERMINAL).context([exception: exception]).build()
      }
    } catch (SpinnakerServerException e) {
      def exception = [operation: stage.tasks[-1].name, reason: e.message]
      return TaskResult.builder(ExecutionStatus.TERMINAL).context([exception: exception]).build()
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

  protected List<Map> getMortResults(String applicationName, String type) {
    def mortResults = mortService.getSearchResults(applicationName, type)
    return mortResults ? mortResults[0].results : []
  }
}
