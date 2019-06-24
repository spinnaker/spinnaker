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

import com.google.common.base.Strings
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.OverridableTimeoutRetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException

import java.time.Duration
import java.util.concurrent.TimeUnit

@Slf4j
@Component
class MonitorWebhookTask implements OverridableTimeoutRetryableTask {

  long backoffPeriod = TimeUnit.SECONDS.toMillis(1)
  long timeout = TimeUnit.HOURS.toMillis(1)
  private static final String JSON_PATH_NOT_FOUND_ERR_FMT = "Unable to parse %s: JSON property '%s' not found in response body"

  @Override
  long getDynamicBackoffPeriod(Stage stage, Duration taskDuration) {
    if (taskDuration.toMillis() > TimeUnit.MINUTES.toMillis(1)) {
      // task has been running > 1min, drop retry interval to every 15 sec
      return Math.max(backoffPeriod, TimeUnit.SECONDS.toMillis(15))
    }

    return backoffPeriod
  }

  @Autowired
  WebhookService webhookService

  @Override
  TaskResult execute(Stage stage) {
    StageData stageData = stage.mapTo(StageData)

    if (Strings.isNullOrEmpty(stageData.statusEndpoint) || Strings.isNullOrEmpty(stageData.statusJsonPath)) {
      throw new IllegalStateException(
        "Missing required parameter(s): statusEndpoint = ${stageData.statusEndpoint}, statusJsonPath = ${stageData.statusJsonPath}")
    }

    def response
    try {
      response = webhookService.getStatus(stageData.statusEndpoint, stageData.customHeaders)
      log.debug(
        "Received status code {} from status endpoint {} in execution {} in stage {}",
        response.statusCode,
        stageData.statusEndpoint,
        stage.execution.id,
        stage.id
      )
    } catch (IllegalArgumentException e) {
      if (e.cause instanceof UnknownHostException) {
        log.warn("name resolution failure in webhook for pipeline ${stage.execution.id} to ${stageData.statusEndpoint}, will retry.", e)
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }

      String errorMessage = "an exception occurred in webhook monitor to ${stageData.statusEndpoint}: ${e}"
      log.error(errorMessage, e)
      Map<String, ?> outputs = [webhook: [monitor: [:]]]
      outputs.webhook.monitor << [error: errorMessage]
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(outputs).build()
    } catch (HttpStatusCodeException  e) {
      def statusCode = e.getStatusCode()
      def statusValue = statusCode.value()

      boolean shouldRetry = statusCode.is5xxServerError() ||
                            (statusValue == 429) ||
                            ((stageData.retryStatusCodes != null) && (stageData.retryStatusCodes.contains(statusValue)))

      if (shouldRetry) {
        log.warn("Failed to get webhook status from ${stageData.statusEndpoint} with statusCode=${statusCode.value()}, will retry", e)
        return TaskResult.ofStatus(ExecutionStatus.RUNNING)
      }

      String errorMessage = "an exception occurred in webhook monitor to ${stageData.statusEndpoint}: ${e}"
      log.error(errorMessage, e)
      Map<String, ?> outputs = [webhook: [monitor: [:]]]
      outputs.webhook.monitor << [error: errorMessage]
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(outputs).build()
    }

    def result
    def responsePayload = [
      webhook: [
        monitor: [
          body: response.body,
          statusCode: response.statusCode,
          statusCodeValue: response.statusCode.value()
        ]
      ],
      buildInfo: response.body, // TODO: deprecated
      deprecationWarning: "All webhook information will be moved beneath the key 'webhook', " +
        "and the keys 'statusCode', 'buildInfo', 'statusEndpoint' and 'error' will be removed. Please migrate today."
    ]
    try {
      result = JsonPath.read(response.body, stageData.statusJsonPath)
    } catch (PathNotFoundException e) {
      responsePayload.webhook.monitor << [error: String.format(JSON_PATH_NOT_FOUND_ERR_FMT, "status", stageData.statusJsonPath)]
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(responsePayload).build()
    }
    if (!(result instanceof String || result instanceof Number || result instanceof Boolean)) {
      responsePayload.webhook.monitor << [error: "The json path '${stageData.statusJsonPath}' did not resolve to a single value", resolvedValue: result]
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(responsePayload).build()
    }

    if (stageData.progressJsonPath) {
      def progress
      try {
        progress = JsonPath.read(response.body, stageData.progressJsonPath)
      } catch (PathNotFoundException e) {
        responsePayload.webhook.monitor << [error: String.format(JSON_PATH_NOT_FOUND_ERR_FMT, "progress", stageData.statusJsonPath)]
        return TaskResult.builder(ExecutionStatus.TERMINAL).context(responsePayload).build()
      }
      if (!(progress instanceof String)) {
        responsePayload.webhook.monitor << [error: "The json path '${stageData.progressJsonPath}' did not resolve to a String value", resolvedValue: progress]
        return TaskResult.builder(ExecutionStatus.TERMINAL).context(responsePayload).build()
      }
      if (progress) {
        responsePayload << [progressMessage: progress] // TODO: deprecated
        responsePayload.webhook.monitor << [progressMessage: progress]
      }
    }

    def statusMap = createStatusMap(stageData.successStatuses, stageData.canceledStatuses, stageData.terminalStatuses)

    if (result instanceof Number) {
      def status = result == 100 ? ExecutionStatus.SUCCEEDED : ExecutionStatus.RUNNING
      responsePayload << [percentComplete: result] // TODO: deprecated
      responsePayload.webhook.monitor << [percentComplete: result]
      return TaskResult.builder(status).context(responsePayload).build()
    } else if (statusMap.containsKey(result.toString().toUpperCase())) {
      return TaskResult.builder(statusMap[result.toString().toUpperCase()]).context(responsePayload).build()
    }

    return TaskResult.builder(ExecutionStatus.RUNNING).context(response ? responsePayload : [:]).build()
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

  private static class StageData {
    public String statusEndpoint
    public String statusJsonPath
    public String progressJsonPath
    public String successStatuses
    public String canceledStatuses
    public String terminalStatuses
    public Object customHeaders
    public List<Integer> retryStatusCodes
  }
}
