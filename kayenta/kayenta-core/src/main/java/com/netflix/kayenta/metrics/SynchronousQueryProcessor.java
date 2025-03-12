/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.kayenta.metrics;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerNetworkException;
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException;
import com.netflix.spinnaker.orca.api.pipeline.TaskResult;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SynchronousQueryProcessor {
  private final MetricsServiceRepository metricsServiceRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final Registry registry;
  private final MetricsRetryConfigurationProperties retryConfiguration;

  @Autowired
  public SynchronousQueryProcessor(
      MetricsServiceRepository metricsServiceRepository,
      StorageServiceRepository storageServiceRepository,
      Registry registry,
      MetricsRetryConfigurationProperties retryConfiguration) {
    this.metricsServiceRepository = metricsServiceRepository;
    this.storageServiceRepository = storageServiceRepository;
    this.registry = registry;
    this.retryConfiguration = retryConfiguration;
  }

  public String executeQuery(
      String metricsAccountName,
      String storageAccountName,
      CanaryConfig canaryConfig,
      int metricIndex,
      CanaryScope canaryScope)
      throws IOException {
    MetricsService metricsService = metricsServiceRepository.getRequiredOne(metricsAccountName);

    StorageService storageService = storageServiceRepository.getRequiredOne(storageAccountName);

    Id queryId =
        registry
            .createId("canary.telemetry.query")
            .withTag("metricsStore", metricsService.getType());

    CanaryMetricConfig canaryMetricConfig = canaryConfig.getMetrics().get(metricIndex);
    List<MetricSet> metricSetList = null;

    // TODO: retry mechanism should be extracted to separate class
    int retries = 0;
    boolean success = false;

    while (!success) {
      try {
        registry.counter(queryId.withTag("retries", retries + "")).increment();
        metricSetList =
            metricsService.queryMetrics(
                metricsAccountName, canaryConfig, canaryMetricConfig, canaryScope);
        success = true;
      } catch (SpinnakerServerException e) {
        boolean retryable = isRetryable(e);
        if (retryable) {
          retries++;
          if (retries >= retryConfiguration.getAttempts()) {
            throw e;
          }
          long backoffPeriod = getBackoffPeriodMs(retries);
          try {
            Thread.sleep(backoffPeriod);
          } catch (InterruptedException ignored) {
          }
          Object error;
          if (isNetworkError(e)) {
            error = e.getCause();
          } else {
            SpinnakerHttpException httpError = (SpinnakerHttpException) e;
            error = httpError.getResponseCode();
          }
          log.warn(
              "Got {} result when querying for metrics. Retrying request (current attempt: "
                  + "{}, max attempts: {}, last backoff period: {}ms)",
              error,
              retries,
              retryConfiguration.getAttempts(),
              backoffPeriod);
        } else {
          throw e;
        }
      } catch (IOException | UncheckedIOException | RetryableQueryException e) {
        retries++;
        if (retries >= retryConfiguration.getAttempts()) {
          throw e;
        }
        long backoffPeriod = getBackoffPeriodMs(retries);
        try {
          Thread.sleep(backoffPeriod);
        } catch (InterruptedException ignored) {
        }
        log.warn(
            "Got error when querying for metrics. Retrying request (current attempt: {}, max "
                + "attempts: {}, last backoff period: {}ms)",
            retries,
            retryConfiguration.getAttempts(),
            backoffPeriod,
            e);
      }
    }
    String metricSetListId = UUID.randomUUID() + "";

    storageService.storeObject(
        storageAccountName, ObjectType.METRIC_SET_LIST, metricSetListId, metricSetList);

    return metricSetListId;
  }

  private long getBackoffPeriodMs(int retryAttemptNumber) {
    // The retries range from 1..max, but we want the backoff periods to range from Math.pow(2,
    // 0)..Math.pow(2, max-1).
    return (long) Math.pow(2, (retryAttemptNumber - 1))
        * retryConfiguration.getBackoffPeriodMultiplierMs();
  }

  private boolean isRetryable(SpinnakerServerException e) {
    if (isNetworkError(e)) {
      return true;
    }
    SpinnakerHttpException httpError = (SpinnakerHttpException) e;
    HttpStatus responseStatus = HttpStatus.resolve(httpError.getResponseCode());
    if (responseStatus == null) {
      return false;
    }
    return retryConfiguration.getStatuses().contains(responseStatus)
        || retryConfiguration.getSeries().contains(responseStatus.series());
  }

  private boolean isNetworkError(SpinnakerServerException e) {
    return e instanceof SpinnakerNetworkException || (e.getCause() instanceof IOException);
  }

  public Map<String, ?> processQueryAndReturnMap(
      String metricsAccountName,
      String storageAccountName,
      CanaryConfig canaryConfig,
      CanaryMetricConfig canaryMetricConfig,
      int metricIndex,
      CanaryScope canaryScope,
      boolean dryRun)
      throws IOException {
    if (canaryConfig == null) {
      canaryConfig = CanaryConfig.builder().metric(canaryMetricConfig).build();
    }

    if (dryRun) {
      MetricsService metricsService = metricsServiceRepository.getRequiredOne(metricsAccountName);

      String query =
          metricsService.buildQuery(
              metricsAccountName, canaryConfig, canaryMetricConfig, canaryScope);

      return Collections.singletonMap("query", query);
    } else {
      String metricSetListId =
          executeQuery(
              metricsAccountName, storageAccountName, canaryConfig, metricIndex, canaryScope);

      return Collections.singletonMap("metricSetListId", metricSetListId);
    }
  }

  public TaskResult executeQueryAndProduceTaskResult(
      String metricsAccountName,
      String storageAccountName,
      CanaryConfig canaryConfig,
      int metricIndex,
      CanaryScope canaryScope) {
    try {
      Map<String, ?> outputs =
          processQueryAndReturnMap(
              metricsAccountName,
              storageAccountName,
              canaryConfig,
              null /* canaryMetricConfig */,
              metricIndex,
              canaryScope,
              false /* dryRun */);

      return TaskResult.builder(ExecutionStatus.SUCCEEDED).outputs(outputs).build();

    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
