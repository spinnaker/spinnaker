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

import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonMappingException
import com.fasterxml.jackson.databind.ObjectMapper
import com.jayway.jsonpath.JsonPath
import com.jayway.jsonpath.PathNotFoundException
import com.jayway.jsonpath.internal.JsonContext
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.RetryableTask
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties
import com.netflix.spinnaker.orca.webhook.service.WebhookService
import groovy.util.logging.Slf4j
import org.apache.http.HttpHeaders
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.HttpMethod
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpStatusCodeException

@Slf4j
@Component
class CreateWebhookTask implements RetryableTask {

  long backoffPeriod = 10000
  long timeout = 300000

  @Autowired
  WebhookService webhookService

  @Override
  TaskResult execute(Stage stage) {
    Map<String, ?> outputs = [webhook: [:]]
    // TODO: The below parameter is deprecated and should be removed after some time
    Map<String, ?> outputsDeprecated = [deprecationWarning: "All webhook information will be moved beneath the key 'webhook', " +
      "and the keys 'statusCode', 'buildInfo', 'statusEndpoint' and 'error' will be removed. Please migrate today."]

    StageData stageData = stage.mapTo(StageData)

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
        }

        outputs.webhook << [body: body]
      }

      if ((stageData.failFastStatusCodes != null) &&
        (stageData.failFastStatusCodes.contains(statusCode.value()))) {
        String webhookMessage = "Received a status code configured to fail fast, terminating stage."
        outputs.webhook << [error: webhookMessage]

        return new TaskResult(ExecutionStatus.TERMINAL, outputs)
      }

      if (statusCode.is5xxServerError() || statusCode.value() == 429) {
        String errorMessage = "error submitting webhook for pipeline ${stage.execution.id} to ${stageData.url}, will retry."
        log.warn(errorMessage, e)

        outputs.webhook << [error: errorMessage]

        return new TaskResult(ExecutionStatus.RUNNING, outputs)
      }

      String errorMessage = "Error submitting webhook for pipeline ${stage.execution.id} to ${stageData.url} with status code ${statusCode}."
      outputs.webhook << [error: errorMessage]

      return new TaskResult(ExecutionStatus.TERMINAL, outputs)
    }

    def statusCode = response.statusCode

    outputs.webhook << [statusCode: statusCode, statusCodeValue: statusCode.value()]
    outputsDeprecated << [statusCode: statusCode]
    if (response.body) {
      outputs.webhook << [body: response.body]
      outputsDeprecated << [buildInfo: response.body]
    }

    if (statusCode.is2xxSuccessful() || statusCode.is3xxRedirection()) {
      if (stageData.waitForCompletion) {
        def statusUrl = null
        def statusUrlResolution = stageData.statusUrlResolution
        switch (statusUrlResolution) {
          case WebhookProperties.StatusUrlResolution.getMethod:
            statusUrl = stageData.url
            break
          case WebhookProperties.StatusUrlResolution.locationHeader:
            statusUrl = response.headers.getFirst(HttpHeaders.LOCATION)
            break
          case WebhookProperties.StatusUrlResolution.webhookResponse:
            try {
              JsonPath path = JsonPath.compile(stageData.statusUrlJsonPath as String)
              statusUrl = new JsonContext().parse(response.body).read(path)
            } catch (PathNotFoundException e) {
              outputs.webhook << [error: e.message]
              return new TaskResult(ExecutionStatus.TERMINAL, outputs)
            }
        }
        if (!statusUrl || !(statusUrl instanceof String)) {
          outputs.webhook << [
            error         : "The status URL couldn't be resolved, but 'Wait for completion' was checked",
            statusEndpoint: statusUrl
          ]
          return new TaskResult(ExecutionStatus.TERMINAL, outputs)
        }
        stage.context.statusEndpoint = statusUrl
        outputs.webhook << [statusEndpoint: statusUrl]
        return new TaskResult(ExecutionStatus.SUCCEEDED, outputsDeprecated + outputs)
      }
      if (stage.context.containsKey("expectedArtifacts") && !((List) stage.context.get("expectedArtifacts")).isEmpty()) {
        try {
          def artifacts = new JsonContext().parse(response.body).read("artifacts")
          outputs << [artifacts: artifacts]
        } catch (Exception e) {
          outputs.webhook << [error: "Expected artifacts in webhook response none were found"]
          return new TaskResult(ExecutionStatus.TERMINAL, outputs)
        }
      }
      return new TaskResult(ExecutionStatus.SUCCEEDED, outputsDeprecated + outputs)
    } else {
      outputs.webhook << [error: "The webhook request failed"]
      return new TaskResult(ExecutionStatus.TERMINAL, outputsDeprecated + outputs)
    }
  }

  private static class StageData {
    public String url
    public Object payload
    public Object customHeaders
    public List<Integer> failFastStatusCodes
    public Boolean waitForCompletion
    public WebhookProperties.StatusUrlResolution statusUrlResolution
    public String statusUrlJsonPath

    @JsonFormat(with = [JsonFormat.Feature.ACCEPT_CASE_INSENSITIVE_PROPERTIES])
    public HttpMethod method = HttpMethod.POST
  }
}
