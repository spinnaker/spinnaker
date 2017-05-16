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

package com.netflix.kayenta.controllers;

import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.metrics.MetricsServiceRepository;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
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
@RequestMapping("/fetch")
@Slf4j
public class FetchController {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  MetricsServiceRepository metricsServiceRepository;

  @Autowired
  StorageServiceRepository storageServiceRepository;

  @RequestMapping(value = "/query", method = RequestMethod.GET)
  public String queryMetrics(@RequestParam(required = false) final String accountName,
                             @ApiParam(defaultValue = "cpu") @RequestParam String metricSetName,
                             @ApiParam(defaultValue = "myapp-v010-") @RequestParam String instanceNamePrefix,
                             @ApiParam(defaultValue = "2017-05-01T15:13:00Z") @RequestParam String intervalStartTime,
                             @ApiParam(defaultValue = "2017-05-02T15:27:00Z") @RequestParam String intervalEndTime) throws IOException {
    String resolvedAccountName = CredentialsHelper.resolveAccountByNameOrType(accountName,
                                                                              AccountCredentials.Type.METRICS_STORE,
                                                                              accountCredentialsRepository);
    Optional<MetricsService> metricsService = metricsServiceRepository.getOne(resolvedAccountName);
    List<MetricSet> metricSetList;

    if (metricsService.isPresent()) {
      metricSetList = metricsService
        .get()
        .queryMetrics(resolvedAccountName, metricSetName, instanceNamePrefix, intervalStartTime, intervalEndTime);
    } else {
      log.debug("No metrics service was configured; skipping placeholder logic to read from metrics store.");

      metricSetList = Collections.singletonList(MetricSet.builder().name("no-metrics").build());
    }

    // TODO(duftler): This is placeholder logic. Just demonstrating that we can write to the bucket.
    // It is not expected that this would (necessarily) use the same account name as that used for the metrics store.
    Optional<StorageService> storageService = storageServiceRepository.getOne(resolvedAccountName);
    String metricSetListId = UUID.randomUUID() + "";

    if (storageService.isPresent()) {
      storageService.get().storeObject(resolvedAccountName, ObjectType.METRIC_SET_LIST, metricSetListId, metricSetList);
    } else {
      log.debug("No storage service was configured; skipping placeholder logic to write to bucket.");
    }

    return metricSetListId;
  }
}
