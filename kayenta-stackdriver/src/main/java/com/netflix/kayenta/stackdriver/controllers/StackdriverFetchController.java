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

package com.netflix.kayenta.stackdriver.controllers;

import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.providers.StackdriverCanaryMetricSetQueryConfig;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import com.netflix.kayenta.stackdriver.canary.StackdriverCanaryScope;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/fetch/stackdriver")
@Slf4j
public class StackdriverFetchController {

  @Autowired
  AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  SynchronousQueryProcessor synchronousQueryProcessor;

  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public String queryMetrics(@RequestParam(required = false) final String metricsAccountName,
                             @RequestParam(required = false) final String storageAccountName,
                             @ApiParam(defaultValue = "cpu") @RequestParam String metricSetName,
                             @ApiParam(defaultValue = "compute.googleapis.com/instance/cpu/utilization") @RequestParam String metricType,
                             @RequestParam(required = false) List<String> groupByFields, // metric.label.instance_name
                             @ApiParam(defaultValue = "myapp-v010-") @RequestParam String scope,
                             @ApiParam(defaultValue = "2017-06-01T15:13:00Z") @RequestParam String intervalStartTimeIso,
                             @ApiParam(defaultValue = "2017-06-02T15:27:00Z") @RequestParam String intervalEndTimeIso,
                             @ApiParam(defaultValue = "3600") @RequestParam String step) throws IOException {
    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
                                                                                     AccountCredentials.Type.METRICS_STORE,
                                                                                     accountCredentialsRepository);
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);

    StackdriverCanaryMetricSetQueryConfig.StackdriverCanaryMetricSetQueryConfigBuilder stackdriverCanaryMetricSetQueryConfigBuilder =
      StackdriverCanaryMetricSetQueryConfig
        .builder()
        .metricType(metricType);

    if (groupByFields != null && !groupByFields.isEmpty()) {
      stackdriverCanaryMetricSetQueryConfigBuilder.groupByFields(groupByFields);
    }

    CanaryMetricConfig canaryMetricConfig =
      CanaryMetricConfig
        .builder()
        .name(metricSetName)
        .query(stackdriverCanaryMetricSetQueryConfigBuilder.build())
        .build();

    Instant startTimeInstant = Instant.parse(intervalStartTimeIso);
    long startTimeMillis = startTimeInstant.toEpochMilli();
    Instant endTimeInstant = Instant.parse(intervalEndTimeIso);
    long endTimeMillis = endTimeInstant.toEpochMilli();

    StackdriverCanaryScope stackdriverCanaryScope = new StackdriverCanaryScope();
    stackdriverCanaryScope.setScope(scope);
    stackdriverCanaryScope.setStart(startTimeMillis + "");
    stackdriverCanaryScope.setEnd(endTimeMillis + "");
    stackdriverCanaryScope.setIntervalStartTimeIso(intervalStartTimeIso);
    stackdriverCanaryScope.setIntervalEndTimeIso(intervalEndTimeIso);
    stackdriverCanaryScope.setStep(step);

    return synchronousQueryProcessor.processQuery(resolvedMetricsAccountName,
                                                  resolvedStorageAccountName,
                                                  canaryMetricConfig,
                                                  stackdriverCanaryScope);
  }
}
