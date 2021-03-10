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
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
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

    Map<String, Object> context = new HashMap<>();
    // Preserve the responses we got from createWebhookTask, but reset the monitor subkey as we will
    // overwrite it with new data
    var webhook =
        Optional.ofNullable(stageData.getWebhook())
            .orElseGet(WebhookStage.WebhookResponseStageData::new);
    context.put("webhook", webhook);

    var oldMonitor = webhook.getMonitor();
    var monitor = new WebhookStage.WebhookMonitorResponseStageData();
    webhook.setMonitor(monitor);

    List<Integer> pastStatusCodes = new ArrayList<>();
    if (oldMonitor != null && oldMonitor.getPastStatusCodes() != null) {
      // continue copying forward in case of a non-http status code based retry
      pastStatusCodes.addAll(oldMonitor.getPastStatusCodes());
      monitor.setPastStatusCodes(pastStatusCodes);
    }

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

      if (shouldRetry(statusCode, stageData)) {
        log.warn(
            "Failed to get webhook status from {} with statusCode={}, will retry",
            stageData.statusEndpoint,
            statusCode.value(),
            e);
        pastStatusCodes.add(statusCode.value());
        monitor.setPastStatusCodes(pastStatusCodes); // in case it wasn't already copied over
        return TaskResult.builder(ExecutionStatus.RUNNING).context(context).build();
      }
      String errorMessage =
          String.format(
              "an exception occurred in webhook monitor to %s: %s", stageData.statusEndpoint, e);
      log.error(errorMessage, e);

      monitor.setError(errorMessage);
      Optional.ofNullable(e.getResponseHeaders())
          .filter(it -> !it.isEmpty())
          .map(HttpHeaders::toSingleValueMap)
          .ifPresent(monitor::setHeaders);

      return TaskResult.builder(ExecutionStatus.TERMINAL).context(context).build();
    } catch (Exception e) {
      if (e instanceof UnknownHostException || e.getCause() instanceof UnknownHostException) {
        log.warn(
            "name resolution failure in webhook for pipeline {} to {}, will retry.",
            stage.getExecution().getId(),
            stageData.statusEndpoint,
            e);
        return TaskResult.builder(ExecutionStatus.RUNNING).context(context).build();
      }
      if (e instanceof SocketTimeoutException || e.getCause() instanceof SocketTimeoutException) {
        log.warn("Socket timeout when polling {}, will retry.", stageData.statusEndpoint, e);
        return TaskResult.builder(ExecutionStatus.RUNNING).context(context).build();
      }

      String errorMessage =
          String.format(
              "an exception occurred in webhook monitor to %s: %s", stageData.statusEndpoint, e);
      log.error(errorMessage, e);
      monitor.setError(errorMessage);
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(context).build();
    }

    monitor.setBody(response.getBody());
    monitor.setStatusCode(response.getStatusCode());
    monitor.setStatusCodeValue(response.getStatusCode().value());

    if (!response.getHeaders().isEmpty()) {
      monitor.setHeaders(response.getHeaders().toSingleValueMap());
    }

    if (Strings.isNullOrEmpty(stageData.statusJsonPath)) {
      return TaskResult.builder(ExecutionStatus.SUCCEEDED).context(context).build();
    }

    Object result;
    try {
      result = JsonPath.read(response.getBody(), stageData.statusJsonPath);
    } catch (PathNotFoundException e) {
      monitor.setError(
          String.format(JSON_PATH_NOT_FOUND_ERR_FMT, "status", stageData.statusJsonPath));
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(context).build();
    }

    if (!(result instanceof String || result instanceof Number || result instanceof Boolean)) {
      monitor.setError(
          String.format(
              "The json path '%s' did not resolve to a single value", stageData.statusJsonPath));
      monitor.setResolvedValue(result);
      return TaskResult.builder(ExecutionStatus.TERMINAL).context(context).build();
    } else {
      if (StringUtils.isNotEmpty(stageData.progressJsonPath)) {
        Object progress;
        try {
          progress = JsonPath.read(response.getBody(), stageData.progressJsonPath);
        } catch (PathNotFoundException e) {
          monitor.setError(
              String.format(JSON_PATH_NOT_FOUND_ERR_FMT, "progress", stageData.statusJsonPath));
          return TaskResult.builder(ExecutionStatus.TERMINAL).context(context).build();
        }
        if (!(progress instanceof String)) {
          monitor.setError(
              String.format(
                  "The json path '%s' did not resolve to a String value",
                  stageData.progressJsonPath));
          monitor.setResolvedValue(progress);
          return TaskResult.builder(ExecutionStatus.TERMINAL).context(context).build();
        } else {
          monitor.setProgressMessage((String) progress);
        }
      }

      if (result instanceof Number) {
        var status = result.equals(100) ? ExecutionStatus.SUCCEEDED : ExecutionStatus.RUNNING;
        monitor.setPercentComplete((Number) result);
        return TaskResult.builder(status).context(context).build();
      } else {
        var statusMap =
            createStatusMap(
                stageData.successStatuses, stageData.canceledStatuses, stageData.terminalStatuses);
        ExecutionStatus status =
            statusMap.getOrDefault(result.toString().toUpperCase(), ExecutionStatus.RUNNING);
        return TaskResult.builder(status).context(context).build();
      }
    }
  }

  @Override
  public void onCancel(@Nonnull StageExecution stage) {
    webhookService.cancelWebhook(stage);
  }

  private boolean shouldRetry(HttpStatus statusCode, WebhookStage.StageData stageData) {
    int status = statusCode.value();
    var retries = stageData.getRetries();

    if (retries != null && retries.containsKey(status)) {
      // specific retry limit configured
      var retryConfig = retries.get(status);
      long attemptsWithStatus = countAttemptsWithStatus(status, stageData);

      return attemptsWithStatus < retryConfig.getMaxAttempts();
    }

    return (statusCode.is5xxServerError())
        || webhookProperties.getDefaultRetryStatusCodes().contains(status)
        || ((stageData.getRetryStatusCodes() != null)
            && (stageData.getRetryStatusCodes().contains(status)));
  }

  private long countAttemptsWithStatus(int status, WebhookStage.StageData stageData) {
    return Optional.ofNullable(stageData.getWebhook())
        .map(WebhookStage.WebhookResponseStageData::getMonitor)
        .map(WebhookStage.WebhookMonitorResponseStageData::getPastStatusCodes)
        .map(past -> past.stream().filter(it -> status == it).count())
        .orElse(0L);
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
