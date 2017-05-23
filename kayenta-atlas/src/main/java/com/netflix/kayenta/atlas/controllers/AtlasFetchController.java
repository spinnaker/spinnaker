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

package com.netflix.kayenta.atlas.controllers;

import com.netflix.kayenta.atlas.canary.AtlasCanaryScope;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.metrics.AtlasMetricSetQuery;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.metrics.MetricsServiceRepository;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.storage.ObjectType;
import com.netflix.kayenta.storage.StorageService;
import com.netflix.kayenta.storage.StorageServiceRepository;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/fetch/atlas")
@Slf4j
public class AtlasFetchController {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  MetricsServiceRepository metricsServiceRepository;

  @Autowired
  StorageServiceRepository storageServiceRepository;

  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public String queryMetrics(@RequestParam(required = false) final String metricsAccountName,
                             @RequestParam(required = false) final String storageAccountName,
                             @RequestParam(required = false) String q,
                             @ApiParam(defaultValue = "cpu") @RequestParam String metricSetName,
                             @ApiParam(defaultValue = "cluster") @RequestParam String type,
                             @RequestParam String scope,
                             @ApiParam(defaultValue = "0") @RequestParam Long start,
                             @ApiParam(defaultValue = "6000000") @RequestParam Long end,
                             @ApiParam(defaultValue = "PT1M") @RequestParam String step) throws IOException {
    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
                                                                                     AccountCredentials.Type.METRICS_STORE,
                                                                                     accountCredentialsRepository);
    Optional<MetricsService> metricsService = metricsServiceRepository.getOne(resolvedMetricsAccountName);
    List<MetricSet> metricSetList;

    if (metricsService.isPresent()) {
      AtlasCanaryScope atlasCanaryScope = new AtlasCanaryScope();
      atlasCanaryScope.setType(type);
      atlasCanaryScope.setScope(scope);
      atlasCanaryScope.setStart(start);
      atlasCanaryScope.setEnd(end);
      atlasCanaryScope.setStep(step);

      AtlasMetricSetQuery atlasMetricSetQuery =
        AtlasMetricSetQuery
          .builder()
          .q(q)
          .build();
      CanaryMetricConfig canaryMetricConfig =
        CanaryMetricConfig
          .builder()
          .name(metricSetName)
          .query(atlasMetricSetQuery)
          .build();

      metricSetList = metricsService
        .get()
        .queryMetrics(resolvedMetricsAccountName, canaryMetricConfig, atlasCanaryScope);
    } else {
      log.debug("No metrics service was configured; skipping placeholder logic to read from metrics store.");

      metricSetList = Collections.singletonList(MetricSet.builder().name("no-metrics").build());
    }

    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
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
