/*
 * Copyright 2017 Schibsted ASA.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.webhook.tasks

import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.Task
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException

@Slf4j
@Component
class MonitorWebhookTask implements Task {

  @Autowired
  WebhookService webhookService

  static requiredParameters = ["statusEndpoint", "statusJsonPath"]

  @Override
  TaskResult execute(Stage stage) {
    def missing = requiredParameters.findAll { !stage.context.get(it) }
    if (!missing.empty) {
      throw new IllegalStateException("Missing required parameter${missing.size() > 1 ? 's' : ''} '${missing.join('\', \'')}'")
    }

    String statusEndpoint = stage.context.statusEndpoint
    String statusJsonPath = stage.context.statusJsonPath
    String progressJsonPath = stage.context.progressJsonPath
    String successStatuses = stage.context.successStatuses
    String canceledStatuses = stage.context.canceledStatuses
    String terminalStatuses = stage.context.terminalStatuses
    def customHeaders = stage.context.customHeaders

    def response
    try {
      response = webhookService.getStatus(statusEndpoint, customHeaders)
    } catch (HttpStatusCodeException  e) {
      def statusCode = e.getStatusCode()
      if (statusCode.is5xxServerError() || statusCode.value() == 429) {
        log.warn("error getting webhook status from ${statusEndpoint}, will retry", e)
        return new TaskResult(ExecutionStatus.RUNNING)
      }
      throw e
    }

    def result
    def responsePayload = [
      webhook: [
        monitor: [
          body: response.body,
          statusCode: response.statusCode
        ]
      ],
      buildInfo: response.body, // TODO: deprecated
      deprecationWarning: "All webhook information will be moved beneath the key 'webhook', " +
        "and the keys 'statusCode', 'buildInfo', 'statusEndpoint' and 'error' will be removed. Please migrate today."
    ]
    try {
      result = JsonPath.read(response.body, statusJsonPath)
    } catch (PathNotFoundException e) {
      responsePayload.webhook.monitor << [error: e.message]
      return new TaskResult(ExecutionStatus.TERMINAL, responsePayload)
    }
    if (!(result instanceof String || result instanceof Number || result instanceof Boolean)) {
      responsePayload.webhook.monitor << [error: "The json path '${statusJsonPath}' did not resolve to a single value", resolvedValue: result]
      return new TaskResult(ExecutionStatus.TERMINAL, responsePayload)
    }

    if (progressJsonPath) {
      def progress
      try {
        progress = JsonPath.read(response.body, progressJsonPath)
      } catch (PathNotFoundException e) {
        responsePayload.webhook.monitor << [error: e.message]
        return new TaskResult(ExecutionStatus.TERMINAL, responsePayload)
      }
      if (!(progress instanceof String)) {
        responsePayload.webhook.monitor << [error: "The json path '${progressJsonPath}' did not resolve to a String value", resolvedValue: progress]
        return new TaskResult(ExecutionStatus.TERMINAL, responsePayload)
      }
      if (progress) {
        responsePayload << [progressMessage: progress] // TODO: deprecated
        responsePayload.webhook.monitor << [progressMessage: progress]
      }
    }

    def statusMap = createStatusMap(successStatuses, canceledStatuses, terminalStatuses)

    if (result instanceof Number) {
      def status = result == 100 ? ExecutionStatus.SUCCEEDED : ExecutionStatus.RUNNING
      responsePayload << [percentComplete: result] // TODO: deprecated
      responsePayload.webhook.monitor << [percentComplete: result]
      return new TaskResult(status, responsePayload)
    } else if (statusMap.containsKey(result.toString().toUpperCase())) {
      return new TaskResult(statusMap[result.toString().toUpperCase()], responsePayload)
    }

    return new TaskResult(ExecutionStatus.RUNNING, response ? responsePayload : [:])
  }

  private static Map<String, ExecutionStatus> createStatusMap(String successStatuses, String canceledStatuses, String terminalStatuses) {
    def statusMap = [:]
    statusMap << mapStatuses(successStatuses, ExecutionStatus.SUCCEEDED)
    if (canceledStatuses) {
      statusMap << mapStatuses(canceledStatuses, ExecutionStatus.CANCELED)
    }
    if (terminalStatuses) {
      statusMap << mapStatuses(terminalStatuses, ExecutionStatus.TERMINAL)
    }
    return statusMap
  }

  private static Map<String, ExecutionStatus> mapStatuses(String statuses, ExecutionStatus status) {
    statuses.split(",").collectEntries { [(it.trim().toUpperCase()): status] }
  }
}
