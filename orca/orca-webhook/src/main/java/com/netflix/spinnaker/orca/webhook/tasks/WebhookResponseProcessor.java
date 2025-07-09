/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.spinnaker.orca.webhook.tasks;

import static java.lang.String.format;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.netflix.spinnaker.kork.exceptions.SystemException;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties;
import com.netflix.spinnaker.orca.webhook.pipeline.WebhookStage;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.client.HttpStatusCodeException;

/** Processes responses and errors from webhook stage executions. */
public class WebhookResponseProcessor {

  private final Logger log = LoggerFactory.getLogger(getClass());
  private final ObjectMapper objectMapper;
  private final WebhookStage.StageData stageData;
  private final WebhookProperties webhookProperties;
  private final StageExecution stageExecution;
  private final String executionId;

  public WebhookResponseProcessor(
      ObjectMapper objectMapper,
      StageExecution stageExecution,
      WebhookProperties webhookProperties) {
    this.objectMapper = objectMapper;
    this.webhookProperties = webhookProperties;
    this.stageExecution = stageExecution;
    this.stageData = stageExecution.mapTo(WebhookStage.StageData.class);
    this.executionId = stageExecution.getExecution().getId();
  }

  public TaskResult process(ResponseEntity<Object> response, Exception exceptionReceived) {
    // Process exception if received.
    if (exceptionReceived != null) {
      if (exceptionReceived instanceof HttpStatusCodeException) {
        return processReceivedHttpStatusException((HttpStatusCodeException) exceptionReceived);
      } else {
        return processClientException(exceptionReceived);
      }
    }

    // Process received response.
    if (response != null) {
      return processResponse(response);
    }

    throw new SystemException("No response or exception is provided to process.");
  }

  private TaskResult processReceivedHttpStatusException(HttpStatusCodeException e) {
    var webhookOutput = new WebhookStage.WebhookResponseStageData();

    webhookOutput.setStatusCode(HttpStatus.valueOf(e.getStatusCode().value()));
    webhookOutput.setStatusCodeValue(e.getStatusCode().value());
    if (e.getResponseHeaders() != null) {
      webhookOutput.setHeaders(e.getResponseHeaders().toSingleValueMap());
    }
    if (!StringUtils.isEmpty(e.getResponseBodyAsString())) {
      webhookOutput.setBody(processResponseBodyAsJson(e.getResponseBodyAsString()));
    }
    TaskResult result =
        processReceivedFailureStatusCode(
            HttpStatus.valueOf(e.getStatusCode().value()), webhookOutput);
    log.warn(webhookOutput.getError(), e);
    return result;
  }

  private TaskResult processReceivedFailureStatusCode(
      HttpStatus status, WebhookStage.WebhookResponseStageData webHookOutput) {
    String errorMessage;
    ExecutionStatus executionStatus;
    // Fail fast status check, retry status check or fail permanently
    if (stageData.failFastStatusCodes != null
        && stageData.failFastStatusCodes.contains(status.value())) {
      errorMessage =
          format(
              "Received status code %s, which is configured to fail fast, terminating stage for pipeline %s to %s",
              status.value(), executionId, stageData.url);
      executionStatus = ExecutionStatus.TERMINAL;
    } else if (status.is5xxServerError()
        || webhookProperties.getDefaultRetryStatusCodes().contains(status.value())
        || (stageData.getWebhookRetryStatusCodes() != null
            && stageData.getWebhookRetryStatusCodes().contains(status.value()))) {
      errorMessage =
          format(
              "Error submitting webhook for pipeline %s to %s with status code %s, will retry.",
              executionId, stageData.url, status.value());
      executionStatus = ExecutionStatus.RUNNING;
    } else {
      errorMessage =
          format(
              "Error submitting webhook for pipeline %s to %s with status code %s.",
              executionId, stageData.url, status.value());
      executionStatus = ExecutionStatus.TERMINAL;
    }

    webHookOutput.setError(errorMessage);
    return TaskResult.builder(executionStatus).context(Map.of("webhook", webHookOutput)).build();
  }

  private TaskResult processClientException(Exception e) {
    String errorMessage;
    ExecutionStatus executionStatus;
    if (e instanceof UnknownHostException || e.getCause() instanceof UnknownHostException) {
      errorMessage =
          format(
              "Remote host resolution failure in webhook for pipeline %s to %s, will retry.",
              executionId, stageData.url);
      executionStatus = ExecutionStatus.RUNNING;
    } else if (stageData.method == HttpMethod.GET
        && (e instanceof SocketTimeoutException
            || e.getCause() instanceof SocketTimeoutException)) {
      errorMessage =
          format(
              "Socket timeout in webhook on GET request for pipeline %s to %s, will retry.",
              executionId, stageData.url);
      executionStatus = ExecutionStatus.RUNNING;
    } else {
      errorMessage =
          format(
              "An exception occurred for pipeline %s performing a request to %s. %s",
              executionId, stageData.url, e.toString());
      executionStatus = ExecutionStatus.TERMINAL;
    }
    var webhookOutput = new WebhookStage.WebhookResponseStageData();
    webhookOutput.setError(errorMessage);
    log.warn(errorMessage, e);
    return TaskResult.builder(executionStatus).context(Map.of("webhook", webhookOutput)).build();
  }

  private TaskResult processResponse(ResponseEntity response) {
    Map<String, Object> stageOutput = new HashMap<>();
    var webhookOutput = new WebhookStage.WebhookResponseStageData();
    stageOutput.put("webhook", webhookOutput);
    webhookOutput.setStatusCode(HttpStatus.valueOf(response.getStatusCode().value()));
    webhookOutput.setStatusCodeValue(response.getStatusCode().value());

    if (response.getBody() != null) {
      webhookOutput.setBody(response.getBody());
    }
    if (!response.getHeaders().isEmpty()) {
      webhookOutput.setHeaders(response.getHeaders().toSingleValueMap());
    }
    HttpStatus status = HttpStatus.valueOf(response.getStatusCode().value());

    if (status.is2xxSuccessful() || status.is3xxRedirection()) {

      // Retrieve status check url
      if (stageData.isWaitForCompletion()) {
        String statusUrl;
        try {
          statusUrl = new WebhookStatusCheckUrlRetriever().getStatusCheckUrl(response, stageData);
        } catch (Exception e) {
          webhookOutput.setError("Exception while resolving status check URL: " + e.getMessage());
          log.error("Exception received while determining status check url", e);
          return TaskResult.builder(ExecutionStatus.TERMINAL).context(stageOutput).build();
        }

        if (StringUtils.isEmpty(statusUrl)) {
          webhookOutput.setError(
              "The status URL couldn't be resolved, but 'Wait for completion' is configured");
          return TaskResult.builder(ExecutionStatus.TERMINAL).context(stageOutput).build();
        }
        stageData.setStatusEndpoint(statusUrl);
        stageExecution
            .getContext()
            .put("statusEndpoint", statusUrl); // TODO: can this be eliminated?

        webhookOutput.setStatusEndpoint(statusUrl);
        return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(stageOutput).build();
      }

      // Retrieve artifacts
      if (stageExecution.getContext().containsKey("expectedArtifacts")
          && !((List) stageExecution.getContext().get("expectedArtifacts")).isEmpty()) {
        try {
          stageOutput.put("artifacts", JsonPath.parse(response.getBody()).read("artifacts"));
        } catch (Exception e) {
          webhookOutput.setError(
              "Expected artifacts in webhook response couldn't be parsed: " + e.toString());
          return TaskResult.builder(ExecutionStatus.TERMINAL).context(stageOutput).build();
        }
      }
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(stageOutput).build();
    } else {
      webhookOutput.setError("The webhook request failed");
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(stageOutput).build();
    }
  }

  private Object processResponseBodyAsJson(String responseBody) {
    Object body = null;
    try {
      if (responseBody.startsWith("{")) {
        body = objectMapper.readValue(responseBody, Map.class);
      } else if (responseBody.startsWith("[")) {
        body = objectMapper.readValue(responseBody, List.class);
      }
    } catch (JsonProcessingException ex) {
      // Just leave body as string, probs not JSON
      log.warn("Failed to parse webhook payload as JSON", ex);
    }
    return body != null ? body : responseBody;
  }
}
