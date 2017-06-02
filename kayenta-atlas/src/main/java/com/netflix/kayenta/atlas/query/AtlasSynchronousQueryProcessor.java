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

package com.netflix.kayenta.atlas.query;

import com.netflix.kayenta.atlas.canary.AtlasCanaryScope;
import com.netflix.kayenta.canary.AtlasCanaryMetricSetQueryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.metrics.MetricsServiceRepository;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@Slf4j
public class AtlasSynchronousQueryProcessor {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  MetricsServiceRepository metricsServiceRepository;

  @Autowired
  StorageServiceRepository storageServiceRepository;

  public String processQuery(AtlasQuery atlasQuery) throws IOException {
    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(atlasQuery.getMetricsAccountName(),
                                                                                     AccountCredentials.Type.METRICS_STORE,
                                                                                     accountCredentialsRepository);
    Optional<MetricsService> metricsService = metricsServiceRepository.getOne(resolvedMetricsAccountName);
    List<MetricSet> metricSetList;

    if (metricsService.isPresent()) {
      AtlasCanaryScope atlasCanaryScope = new AtlasCanaryScope();
      atlasCanaryScope.setType(atlasQuery.getType());
      atlasCanaryScope.setScope(atlasQuery.getScope());
      atlasCanaryScope.setStart(atlasQuery.getStart());
      atlasCanaryScope.setEnd(atlasQuery.getEnd());
      atlasCanaryScope.setStep(atlasQuery.getStep());

      AtlasCanaryMetricSetQueryConfig atlasMetricSetQuery =
        AtlasCanaryMetricSetQueryConfig
          .builder()
          .q(atlasQuery.getQ())
          .build();
      CanaryMetricConfig canaryMetricConfig =
        CanaryMetricConfig
          .builder()
          .name(atlasQuery.getMetricSetName())
          .query(atlasMetricSetQuery)
          .build();

      metricSetList = metricsService
        .get()
        .queryMetrics(resolvedMetricsAccountName, canaryMetricConfig, atlasCanaryScope);
    } else {
      log.debug("No metrics service was configured; skipping placeholder logic to read from metrics store.");

      metricSetList = Collections.singletonList(MetricSet.builder().name("no-metrics").build());
    }

    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(atlasQuery.getStorageAccountName(),
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);
    Optional<StorageService> storageService = storageServiceRepository.getOne(resolvedStorageAccountName);
    String metricSetListId = UUID.randomUUID() + "";

    if (storageService.isPresent()) {
      storageService
        .get()
        .storeObject(resolvedStorageAccountName, ObjectType.METRIC_SET_LIST, metricSetListId, metricSetList);
    } else {
      log.debug("No storage service was configured; skipping placeholder logic to write to bucket.");
    }

    return metricSetListId;
  }
}
