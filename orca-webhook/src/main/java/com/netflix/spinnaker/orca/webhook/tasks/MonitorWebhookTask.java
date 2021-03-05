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

package com.netflix.spinnaker.orca.webhook.tasks;

import com.google.common.base.Strings;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.netflix.spinnaker.orca.api.pipeline.OverridableTimeoutRetryableTask;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.webhook.config.WebhookProperties;
import com.netflix.spinnaker.orca.webhook.pipeline.WebhookStage;
import com.netflix.spinnaker.orca.webhook.service.WebhookService;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;

@Slf4j
@Component
public class MonitorWebhookTask implements OverridableTimeoutRetryableTask {
  private static final String JSON_PATH_NOT_FOUND_ERR_FMT =
      "Unable to parse %s: JSON property '%s' not found in response body";

  private final long backoffPeriod = TimeUnit.SECONDS.toMillis(1);
  private final long timeout = TimeUnit.HOURS.toMillis(1);

  private final WebhookService webhookService;
  private final WebhookProperties webhookProperties;

  @Autowired
  public MonitorWebhookTask(WebhookService webhookService, WebhookProperties webhookProperties) {
    this.webhookService = webhookService;
    this.webhookProperties = webhookProperties;
  }

  @Override
  public long getBackoffPeriod() {
    return backoffPeriod;
  }

  @Override
  public long getTimeout() {
    return timeout;
  }

  @Override
  public long getDynamicBackoffPeriod(StageExecution stage, Duration taskDuration) {
    if (taskDuration.toMillis() > TimeUnit.MINUTES.toMillis(1)) {
      // task has been running > 1min, drop retry interval to every 15 sec
      return Math.max(backoffPeriod, TimeUnit.SECONDS.toMillis(15));
    }

    return backoffPeriod;
  }

  @Override
  public TaskResult execute(StageExecution stage) {
    WebhookStage.StageData stageData = stage.mapTo(WebhookStage.StageData.class);

    if (StringUtils.isBlank(stageData.statusEndpoint) && !stageData.monitorOnly) {
      throw new IllegalStateException(
          "Missing required parameter: statusEndpoint = " + stageData.statusEndpoint);
    }

    if (stageData.monitorOnly && StringUtils.isAllBlank(stageData.statusEndpoint, stageData.url)) {
      throw new IllegalStateException(
          "Missing required parameter. Either webhook url or statusEndpoint are required");
    }

    // Preserve the responses we got from createWebhookTask, but reset the monitor subkey as we will
    // overwrite it new data
    Map<String, Object> webhook =
        (Map<String, Object>) stage.getContext().getOrDefault("webhook", new HashMap<>());
    Map<String, Object> monitor = new HashMap<>();
    webhook.put("monitor", monitor);

    Map<String, Object> originalResponse = new HashMap<>();
    originalResponse.put("webhook", webhook);

    ResponseEntity<Object> response;
    try {
      response = webhookService.getWebhookStatus(stage);
      log.debug(
          "Received status code {} from status endpoint {} in execution {} in stage {}",
          response.getStatusCode(),
          stageData.statusEndpoint,
          stage.getExecution().getId(),
          stage.getId());
    } catch (HttpStatusCodeException e) {
      var statusCode = e.getStatusCode();
      var statusValue = statusCode.value();

      boolean shouldRetry =
          statusCode.is5xxServerError()
              || webhookProperties.getDefaultRetryStatusCodes().contains(statusValue)
              || ((stageData.retryStatusCodes != null)
                  && (stageData.retryStatusCodes.contains(statusValue)));

      if (shouldRetry) {
        log.warn(
            "Failed to get webhook status from {} with statusCode={}, will retry",
            stageData.statusEndpoint,
            statusValue,
            e);
        return TaskResult.ofStatus(ExecutionStatus.RUNNING);
      }

      String errorMessage =
          "an exception occurred in webhook monitor to ${stageData.statusEndpoint}: ${e}";
      log.error(errorMessage, e);
      monitor.put("error", errorMessage);
      Optional.ofNullable(e.getResponseHeaders())
          .filter(it -> !it.isEmpty())
          .map(HttpHeaders::toSingleValueMap)
          .ifPresent(it -> monitor.put("headers", it));

      return TaskResult.builder(ExecutionStatus.TERMINAL).context(originalResponse).build();
    } catch (Exception e) {
      if (e instanceof UnknownHostException || e.getCause() instanceof UnknownHostException) {
        log.warn(
            "name resolution failure in webhook for pipeline {} to {}, will retry.",
            stage.getExecution().getId(),
            stageData.statusEndpoint,
            e);
        return TaskResult.ofStatus(ExecutionStatus.RUNNING);
      }
      if (e instanceof SocketTimeoutException || e.getCause() instanceof SocketTimeoutException) {
        log.warn("Socket timeout when polling {}, will retry.", stageData.statusEndpoint, e);
        return TaskResult.ofStatus(ExecutionStatus.RUNNING);
      }

      String errorMessage =
          String.format(
              "an exception occurred in webhook monitor to %s: %s", stageData.statusEndpoint, e);
      log.error(errorMessage, e);
      monitor.put("error", errorMessage);
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(originalResponse).build();
    }

    var responsePayload = originalResponse;
    monitor.putAll(
        Map.of(
            "body", response.getBody(),
            "statusCode", response.getStatusCode(),
            "statusCodeValue", response.getStatusCode().value()));

    if (!response.getHeaders().isEmpty()) {
      monitor.put("headers", response.getHeaders().toSingleValueMap());
    }

    if (Strings.isNullOrEmpty(stageData.statusJsonPath)) {
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(originalResponse).build();
    }

    Object result;
    try {
      result = JsonPath.read(response.getBody(), stageData.statusJsonPath);
    } catch (PathNotFoundException e) {
      monitor.put(
          "error", String.format(JSON_PATH_NOT_FOUND_ERR_FMT, "status", stageData.statusJsonPath));
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(originalResponse).build();
    }
    if (!(result instanceof String || result instanceof Number || result instanceof Boolean)) {
      monitor.putAll(
          Map.of(
              "error",
              String.format(
                  "The json path '%s' did not resolve to a single value", stageData.statusJsonPath),
              "resolvedValue",
              result));
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(originalResponse).build();
    }

    if (StringUtils.isNotEmpty(stageData.progressJsonPath)) {
      Object progress;
      try {
        progress = JsonPath.read(response.getBody(), stageData.progressJsonPath);
      } catch (PathNotFoundException e) {
        monitor.put(
            "error",
            String.format(JSON_PATH_NOT_FOUND_ERR_FMT, "progress", stageData.statusJsonPath));
        return TaskResult.builder(ExecutionStatus.TERMINAL).context(responsePayload).build();
      }
      if (!(progress instanceof String)) {
        monitor.put(
            "error",
            String.format(
                "The json path '%s' did not resolve to a String value",
                stageData.progressJsonPath));
        monitor.put("resolvedValue", progress);
        return TaskResult.builder(ExecutionStatus.TERMINAL).context(responsePayload).build();
      }
      if (progress != null) {
        monitor.put("progressMessage", progress);
      }
    }

    var statusMap =
        createStatusMap(
            stageData.successStatuses, stageData.canceledStatuses, stageData.terminalStatuses);

    if (result instanceof Number) {
      var status = result.equals(100) ? ExecutionStatus.SUCCEEDED : ExecutionStatus.RUNNING;
      monitor.put("percentComplete", result);
      return TaskResult.builder(status).context(responsePayload).build();
    } else if (statusMap.containsKey(result.toString().toUpperCase())) {
      return TaskResult.builder(statusMap.get(result.toString().toUpperCase()))
          .context(responsePayload)
          .build();
    }

    return TaskResult.builder(ExecutionStatus.RUNNING).context(responsePayload).build();
  }

  @Override
  public void onCancel(@Nonnull StageExecution stage) {
    webhookService.cancelWebhook(stage);
  }

  private static Map<String, ExecutionStatus> createStatusMap(
      String successStatuses, String canceledStatuses, String terminalStatuses) {
    Map<String, ExecutionStatus> statusMap = new HashMap<>();
    statusMap.putAll(mapStatuses(successStatuses, ExecutionStatus.SUCCEEDED));
    if (StringUtils.isNotEmpty(canceledStatuses)) {
      statusMap.putAll(mapStatuses(canceledStatuses, ExecutionStatus.CANCELED));
    }
    if (StringUtils.isNotEmpty(terminalStatuses)) {
      statusMap.putAll(mapStatuses(terminalStatuses, ExecutionStatus.TERMINAL));
    }
    return statusMap;
  }

  private static Map<String, ExecutionStatus> mapStatuses(String statuses, ExecutionStatus status) {
    return Arrays.stream(statuses.split(","))
        .map(String::trim)
        .map(String::toUpperCase)
        .collect(Collectors.toMap(Function.identity(), ignore -> status));
  }
}
