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

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.JsonPath
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties
import com.netflix.spinnaker.orca.webhook.pipeline.WebhookStage
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import groovy.util.logging.Slf4j
import org.apache.http.HttpHeaders
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException

import java.util.regex.Matcher
import java.util.regex.Pattern

@Slf4j
@Component
class CreateWebhookTask implements RetryableTask {

  private static final Pattern URL_SCHEME = Pattern.compile("(.*)://(.*)")

  long backoffPeriod = 10000
  long timeout = 300000

  @Autowired
  WebhookService webhookService

  @Override
  TaskResult execute(Stage stage) {
    Map<String, ?> outputs = [webhook: [:]]
    WebhookStage.StageData stageData = stage.mapTo(WebhookStage.StageData)

    def response
    try {
      response = webhookService.exchange(stageData.method, stageData.url, stageData.payload, stageData.customHeaders)
    } catch (HttpStatusCodeException e) {
      def statusCode = e.getStatusCode()

      outputs.webhook << [statusCode: statusCode, statusCodeValue: statusCode.value()]
      if (e.responseBodyAsString) {
        // Best effort parse of body in case it's JSON
        def body = e.responseBodyAsString
        try {
          ObjectMapper objectMapper = new ObjectMapper()

          if (body.startsWith("{")) {
            body = objectMapper.readValue(body, Map.class)
          } else if (body.startsWith("[")) {
            body = objectMapper.readValue(body, List.class)
          }
        } catch (JsonParseException | JsonMappingException ex) {
          // Just leave body as string, probs not JSON
          log.warn("Failed to parse webhook payload as JSON", ex)
        }

        outputs.webhook << [body: body]
      }

      if ((stageData.failFastStatusCodes != null) &&
        (stageData.failFastStatusCodes.contains(statusCode.value()))) {
        String webhookMessage = "Received a status code configured to fail fast, terminating stage."
        outputs.webhook << [error: webhookMessage]

        return TaskResult.builder(ExecutionStatus.TERMINAL).context(outputs).build()
      }

      if (statusCode.is5xxServerError() || statusCode.value() == 429) {
        String errorMessage = "error submitting webhook for pipeline ${stage.execution.id} to ${stageData.url}, will retry."
        log.warn(errorMessage, e)

        outputs.webhook << [error: errorMessage]

        return TaskResult.builder(ExecutionStatus.RUNNING).context(outputs).build()
      }

      String errorMessage = "Error submitting webhook for pipeline ${stage.execution.id} to ${stageData.url} with status code ${statusCode.value()}."
      outputs.webhook << [error: errorMessage]

      return TaskResult.builder(ExecutionStatus.TERMINAL).context(outputs).build()
    } catch (Exception e) {
      if (e instanceof UnknownHostException || e.cause instanceof UnknownHostException) {
        String errorMessage = "name resolution failure in webhook for pipeline ${stage.execution.id} to ${stageData.url}, will retry."
        log.warn(errorMessage, e)
        outputs.webhook << [error: errorMessage]
        return TaskResult.builder(ExecutionStatus.RUNNING).context(outputs).build()
      } else {
        String errorMessage = "an exception occurred in webhook to ${stageData.url}: ${e}"
        log.error(errorMessage, e)
        outputs.webhook << [error: errorMessage]
        return TaskResult.builder(ExecutionStatus.TERMINAL).context(outputs).build()
      }
    }

    def statusCode = response.statusCode

    outputs.webhook << [statusCode: statusCode, statusCodeValue: statusCode.value()]
    if (response.body) {
      outputs.webhook << [body: response.body]
    }

    if (statusCode.is2xxSuccessful() || statusCode.is3xxRedirection()) {
      if (stageData.waitForCompletion) {
        String statusUrl
        try {
          statusUrl = determineWebHookStatusCheckUrl(response, stageData)
        } catch (Exception e) {
          outputs.webhook << [error: 'Exception while resolving status check URL: ' + e.message]
          log.error('Exception received while determining status check url', e)
          return TaskResult.builder(ExecutionStatus.TERMINAL).context(outputs).build()
        }

        if (!statusUrl) {
          outputs.webhook << [
            error         : "The status URL couldn't be resolved, but 'Wait for completion' was checked",
            statusEndpoint: statusUrl
          ]
          return TaskResult.builder(ExecutionStatus.TERMINAL).context(outputs).build()
        }
        stage.context.statusEndpoint = statusUrl
        outputs.webhook << [statusEndpoint: statusUrl]
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
      }
      if (stage.context.containsKey("expectedArtifacts") && !((List) stage.context.get("expectedArtifacts")).isEmpty()) {
        try {
          def artifacts = JsonPath.parse(response.body).read("artifacts")
          outputs << [artifacts: artifacts]
        } catch (Exception e) {
          outputs.webhook << [error: "Expected artifacts in webhook response couldn't be parsed " + e.toString()]
          return TaskResult.builder(ExecutionStatus.TERMINAL).context(outputs).build()
        }
      }
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(outputs).build()
    } else {
      outputs.webhook << [error: "The webhook request failed"]
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(outputs).build()
    }
  }

  private String determineWebHookStatusCheckUrl(ResponseEntity response, WebhookStage.StageData stageData) {

    String statusCheckUrl

    switch (stageData.statusUrlResolution) {
      case WebhookProperties.StatusUrlResolution.getMethod:
        statusCheckUrl = stageData.url
        break
      case WebhookProperties.StatusUrlResolution.locationHeader:
        statusCheckUrl = response.headers.getFirst(HttpHeaders.LOCATION)
        break
      case WebhookProperties.StatusUrlResolution.webhookResponse:
        statusCheckUrl = JsonPath.compile(stageData.statusUrlJsonPath as String).read(response.body)
        break
    }
    log.info('Web hook status check url as resolved: {}', statusCheckUrl)

    // Preserve the protocol scheme of original webhook that was called, when calling for status check of a webhook.
    if (statusCheckUrl != stageData.url) {
      Matcher statusUrlMatcher = URL_SCHEME.matcher(statusCheckUrl)
      URI statusCheckUri = URI.create(statusCheckUrl).normalize()
      String statusCheckHost = statusCheckUri.getHost()

      URI webHookUri = URI.create(stageData.url).normalize()
      String webHookHost = webHookUri.getHost()
      if (webHookHost == statusCheckHost &&
          webHookUri.getScheme() != statusCheckUri.getScheme() && statusUrlMatcher.find()) {
        // Same hosts keep the original protocol scheme of the webhook that was originally set.
        statusCheckUrl = webHookUri.getScheme() + '://' + statusUrlMatcher.group(2)
        log.info('Adjusted Web hook status check url: {}', statusCheckUrl)
      }
    }

    return statusCheckUrl
  }

}
