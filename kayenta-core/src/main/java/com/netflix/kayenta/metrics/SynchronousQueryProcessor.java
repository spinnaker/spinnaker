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
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import retrofit.RetrofitError;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
public class SynchronousQueryProcessor {
  private final MetricsServiceRepository metricsServiceRepository;
  private final StorageServiceRepository storageServiceRepository;
  private final Registry registry;

  @Autowired
  public SynchronousQueryProcessor(MetricsServiceRepository metricsServiceRepository,
                                   StorageServiceRepository storageServiceRepository,
                                   Registry registry) {
    this.metricsServiceRepository = metricsServiceRepository;
    this.storageServiceRepository = storageServiceRepository;
    this.registry = registry;
  }

  public String processQuery(String metricsAccountName,
                             String storageAccountName,
                             CanaryConfig canaryConfig,
                             int metricIndex,
                             CanaryScope canaryScope) throws IOException {
    MetricsService metricsService =
      metricsServiceRepository
        .getOne(metricsAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No metrics service was configured; unable to read from metrics store."));

    StorageService storageService =
      storageServiceRepository
        .getOne(storageAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to write metric set list."));

    Id queryId = registry.createId("canary.telemetry.query").withTag("metricsStore", metricsService.getType());

    CanaryMetricConfig canaryMetricConfig = canaryConfig.getMetrics().get(metricIndex);
    List<MetricSet> metricSetList = null;
    int retries = 0;
    boolean success = false;

    while (!success) {
      try {
        registry.counter(queryId.withTag("retries", retries + "")).increment();
        metricSetList = metricsService.queryMetrics(metricsAccountName, canaryConfig, canaryMetricConfig, canaryScope);
        success = true;
      } catch (IOException | UncheckedIOException | RetrofitError e) {
        retries++;
        // TODO: Externalize this as a configurable setting.
        if (retries >= 10)
          throw e;
        log.warn("Retrying metric service query");
      }
    }
    String metricSetListId = UUID.randomUUID() + "";

    storageService.storeObject(storageAccountName, ObjectType.METRIC_SET_LIST, metricSetListId, metricSetList);
    return metricSetListId;
  }

  public TaskResult processQueryAndProduceTaskResult(String metricsAccountName,
                                                     String storageAccountName,
                                                     CanaryConfig canaryConfig,
                                                     int metricIndex,
                                                     CanaryScope canaryScope) {
    try {
      String metricSetListId = processQuery(metricsAccountName, storageAccountName, canaryConfig, metricIndex, canaryScope);
      Map outputs = Collections.singletonMap("metricSetId", metricSetListId);

      return new TaskResult(ExecutionStatus.SUCCEEDED, Collections.emptyMap(), outputs);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
