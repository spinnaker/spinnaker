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
import com.netflix.kayenta.prometheus.config.PrometheusConfigurationTestControllerDefaultProperties;
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
  private final PrometheusConfigurationTestControllerDefaultProperties prometheusConfigurationTestControllerDefaultProperties;

  @Autowired
  public PrometheusFetchController(AccountCredentialsRepository accountCredentialsRepository,
                                   SynchronousQueryProcessor synchronousQueryProcessor,
                                   PrometheusConfigurationTestControllerDefaultProperties prometheusConfigurationTestControllerDefaultProperties) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.synchronousQueryProcessor = synchronousQueryProcessor;
    this.prometheusConfigurationTestControllerDefaultProperties = prometheusConfigurationTestControllerDefaultProperties;
  }

  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public Map queryMetrics(@RequestParam(required = false) final String metricsAccountName,
                          @RequestParam(required = false) final String storageAccountName,
                          @ApiParam(defaultValue = "cpu") @RequestParam String metricSetName,
                          @ApiParam(defaultValue = "node_cpu") @RequestParam String metricName,
                          @RequestParam(required = false) List<String> groupByFields,
                          @RequestParam(required = false) String project,
                          @ApiParam(value = "Used to identify the type of the resource being queried, " +
                                            "e.g. aws_ec2_instance, gce_instance.")
                            @RequestParam(required = false) String resourceType,
                          @ApiParam(value = "The region to use when scoping the query. Valid choices depend on what cloud " +
                                            "platform the query relates to.")
                            @RequestParam(required = false) String region,
                          @ApiParam(value = "The name of the resource to use when scoping the query. " +
                                            "The most common use-case is to provide a server group name.")
                            @RequestParam(required = false) String scope,
                          @ApiParam(defaultValue = "mode=~\"user|system\"")
                            @RequestParam(required = false) List<String> labelBindings,
                          @ApiParam(value = "An ISO format timestamp, e.g.: 2018-03-08T01:02:53Z")
                            @RequestParam(required = false) Instant start,
                          @ApiParam(value = "An ISO format timestamp, e.g.: 2018-03-08T01:12:22Z")
                            @RequestParam(required = false) Instant end,
                          @ApiParam(defaultValue = "60") @RequestParam Long step) throws IOException {
    // Apply defaults.
    if (StringUtils.isEmpty(project) && !StringUtils.isEmpty(prometheusConfigurationTestControllerDefaultProperties.getProject())) {
      project = prometheusConfigurationTestControllerDefaultProperties.getProject();
    }
    if (StringUtils.isEmpty(resourceType) && !StringUtils.isEmpty(prometheusConfigurationTestControllerDefaultProperties.getResourceType())) {
      resourceType = prometheusConfigurationTestControllerDefaultProperties.getResourceType();
    }
    if (StringUtils.isEmpty(region) && !StringUtils.isEmpty(prometheusConfigurationTestControllerDefaultProperties.getRegion())) {
      region = prometheusConfigurationTestControllerDefaultProperties.getRegion();
    }
    if (StringUtils.isEmpty(scope) && !StringUtils.isEmpty(prometheusConfigurationTestControllerDefaultProperties.getScope())) {
      scope = prometheusConfigurationTestControllerDefaultProperties.getScope();
    }
    if (start == null && !StringUtils.isEmpty(prometheusConfigurationTestControllerDefaultProperties.getStart())) {
      start = Instant.parse(prometheusConfigurationTestControllerDefaultProperties.getStart());
    }
    if (end == null && !StringUtils.isEmpty(prometheusConfigurationTestControllerDefaultProperties.getEnd())) {
      end = Instant.parse(prometheusConfigurationTestControllerDefaultProperties.getEnd());
    }

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
