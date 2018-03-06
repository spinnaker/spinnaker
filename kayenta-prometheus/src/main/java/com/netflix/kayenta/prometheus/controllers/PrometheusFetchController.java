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

package com.netflix.kayenta.prometheus.controllers;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.providers.PrometheusCanaryMetricSetQueryConfig;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.prometheus.canary.PrometheusCanaryScope;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import io.swagger.annotations.ApiParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/fetch/prometheus")
@Slf4j
public class PrometheusFetchController {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final SynchronousQueryProcessor synchronousQueryProcessor;

  @Autowired
  public PrometheusFetchController(AccountCredentialsRepository accountCredentialsRepository,
                                   SynchronousQueryProcessor synchronousQueryProcessor) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.synchronousQueryProcessor = synchronousQueryProcessor;
  }

  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public Map queryMetrics(@RequestParam(required = false) final String metricsAccountName,
                          @RequestParam(required = false) final String storageAccountName,
                          @ApiParam(defaultValue = "cpu") @RequestParam String metricSetName,
                          @ApiParam(defaultValue = "node_cpu") @RequestParam String metricName,
                          @RequestParam(required = false) List<String> groupByFields,
                          @RequestParam(required = false) final String project,
                          @ApiParam(defaultValue = "gce_instance") @RequestParam String resourceType,
                          @ApiParam(defaultValue = "us-central1") @RequestParam(required = false) String region,
                          @RequestParam(required = false, defaultValue = "myapp-dev-v055") String scope,
                          @ApiParam(defaultValue = "mode=~\"user|system\"") @RequestParam(required = false) List<String> labelBindings,
                          @ApiParam(defaultValue = "2018-03-04T22:02:53Z") @RequestParam Instant start,
                          @ApiParam(defaultValue = "2018-03-04T22:12:22Z") @RequestParam Instant end,
                          @ApiParam(defaultValue = "60") @RequestParam Long step) throws IOException {
    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
                                                                                     AccountCredentials.Type.METRICS_STORE,
                                                                                     accountCredentialsRepository);
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
                                                                                     AccountCredentials.Type.OBJECT_STORE,
                                                                                     accountCredentialsRepository);

    PrometheusCanaryMetricSetQueryConfig prometheusCanaryMetricSetQueryConfig =
      PrometheusCanaryMetricSetQueryConfig
        .builder()
        .metricName(metricName)
        .labelBindings(labelBindings)
        .groupByFields(groupByFields)
        .build();
    CanaryMetricConfig canaryMetricConfig =
      CanaryMetricConfig
        .builder()
        .name(metricSetName)
        .query(prometheusCanaryMetricSetQueryConfig)
        .build();

    PrometheusCanaryScope prometheusCanaryScope = new PrometheusCanaryScope();
    prometheusCanaryScope.setScope(scope);
    prometheusCanaryScope.setRegion(region);
    prometheusCanaryScope.setResourceType(resourceType);
    prometheusCanaryScope.setStart(start);
    prometheusCanaryScope.setEnd(end);
    prometheusCanaryScope.setStep(step);

    if (!StringUtils.isEmpty(project)) {
      prometheusCanaryScope.setProject(project);
    }

    String metricSetListId = synchronousQueryProcessor.processQuery(resolvedMetricsAccountName,
                                                                    resolvedStorageAccountName,
                                                                    CanaryConfig.builder().metric(canaryMetricConfig).build(),
                                                                    0,
                                                                    prometheusCanaryScope);

    return Collections.singletonMap("metricSetListId", metricSetListId);
  }
}
