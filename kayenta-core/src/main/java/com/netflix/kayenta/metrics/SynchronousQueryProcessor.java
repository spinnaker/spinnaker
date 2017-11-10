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

import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.Registry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Component
@Slf4j
public class SynchronousQueryProcessor {

  @Autowired
  MetricsServiceRepository metricsServiceRepository;

  @Autowired
  StorageServiceRepository storageServiceRepository;

  @Autowired
  Registry registry;

  public List<String> processQuery(String metricsAccountName,
                                   String storageAccountName,
                                   List<CanaryMetricConfig> canaryMetricConfigs,
                                   CanaryScope canaryScope) throws IOException {
    MetricsService metricsService =
      metricsServiceRepository
        .getOne(metricsAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No metrics service was configured; unable to read from metrics store."));

    StorageService storageService =
      storageServiceRepository
        .getOne(storageAccountName)
        .orElseThrow(() -> new IllegalArgumentException("No storage service was configured; unable to write metric set list."));

    List<String> metricSetListIds = new ArrayList<>();
    Id queryId = registry.createId("canary.telemetry.query").withTag("metricsStore", metricsService.getType());

    for (CanaryMetricConfig canaryMetricConfig : canaryMetricConfigs) {
      List<MetricSet> metricSetList = null;
      int retries = 0;
      boolean success = false;

      while (!success) {
        try {
          registry.counter(queryId.withTag("retries", retries + "")).increment();
          metricSetList = metricsService.queryMetrics(metricsAccountName, canaryMetricConfig, canaryScope);
          success = true;
        } catch (IOException e) {
          retries++;
          if (retries >= 10)
            throw e;
          log.warn("Retrying atlas query");
        }
      }
      String metricSetListId = UUID.randomUUID() + "";

      storageService.storeObject(storageAccountName, ObjectType.METRIC_SET_LIST, metricSetListId, metricSetList);
      metricSetListIds.add(metricSetListId);
    }

    return metricSetListIds;
  }
}
