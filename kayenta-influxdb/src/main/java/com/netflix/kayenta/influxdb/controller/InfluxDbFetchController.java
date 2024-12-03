/*
 * Copyright 2018 Joseph Motha
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

package com.netflix.kayenta.influxdb.controller;

import static com.netflix.kayenta.canary.util.FetchControllerUtils.determineDefaultProperty;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.InfluxdbCanaryMetricSetQueryConfig;
import com.netflix.kayenta.influxdb.config.InfluxDbConfigurationTestControllerDefaultProperties;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import io.swagger.v3.oas.annotations.Parameter;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fetch/influxdb")
public class InfluxDbFetchController {
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final SynchronousQueryProcessor synchronousQueryProcessor;
  private final InfluxDbConfigurationTestControllerDefaultProperties
      influxDbConfigurationTestControllerDefaultProperties;

  @Autowired
  public InfluxDbFetchController(
      AccountCredentialsRepository accountCredentialsRepository,
      SynchronousQueryProcessor synchronousQueryProcessor,
      InfluxDbConfigurationTestControllerDefaultProperties
          influxDbConfigurationTestControllerDefaultProperties) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.synchronousQueryProcessor = synchronousQueryProcessor;
    this.influxDbConfigurationTestControllerDefaultProperties =
        influxDbConfigurationTestControllerDefaultProperties;
  }

  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public Map<String, String> queryMetrics(
      @RequestParam(required = false) final String metricsAccountName,
      @RequestParam(required = false) final String storageAccountName,
      @Parameter(example = "cpu") @RequestParam String metricSetName,
      @Parameter(example = "temperature") @RequestParam String metricName,
      @Parameter(description = "Fields that are being queried. e.g. internal, external")
          @RequestParam(required = false)
          List<String> fields,
      @Parameter(
              description =
                  "The scope of the Influxdb query. e.g. autoscaling_group:myapp-prod-v002")
          @RequestParam(required = false)
          String scope,
      @Parameter(description = "An ISO format timestamp, e.g.: 2018-03-15T01:23:45Z")
          @RequestParam(required = false)
          String start,
      @Parameter(description = "An ISO format timestamp, e.g.: 2018-03-15T01:23:45Z")
          @RequestParam(required = false)
          String end,
      @Parameter(example = "60", description = "seconds") @RequestParam Long step)
      throws IOException {
    // Apply defaults.
    scope =
        determineDefaultProperty(
            scope, "scope", influxDbConfigurationTestControllerDefaultProperties);
    start =
        determineDefaultProperty(
            start, "start", influxDbConfigurationTestControllerDefaultProperties);
    end =
        determineDefaultProperty(end, "end", influxDbConfigurationTestControllerDefaultProperties);

    if (StringUtils.isEmpty(start)) {
      throw new IllegalArgumentException("Start time is required.");
    }

    if (StringUtils.isEmpty(end)) {
      throw new IllegalArgumentException("End time is required.");
    }

    String resolvedMetricsAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(metricsAccountName, AccountCredentials.Type.METRICS_STORE)
            .getName();
    String resolvedStorageAccountName =
        accountCredentialsRepository
            .getRequiredOneBy(storageAccountName, AccountCredentials.Type.OBJECT_STORE)
            .getName();

    InfluxdbCanaryMetricSetQueryConfig influxDbCanaryMetricSetQueryConfig =
        InfluxdbCanaryMetricSetQueryConfig.builder().metricName(metricName).fields(fields).build();

    CanaryMetricConfig canaryMetricConfig =
        CanaryMetricConfig.builder()
            .name(metricSetName)
            .query(influxDbCanaryMetricSetQueryConfig)
            .build();

    CanaryScope canaryScope =
        new CanaryScope(
            scope,
            null /* location */,
            Instant.parse(start),
            Instant.parse(end),
            step,
            Collections.emptyMap());

    String metricSetListId =
        synchronousQueryProcessor.executeQuery(
            resolvedMetricsAccountName,
            resolvedStorageAccountName,
            CanaryConfig.builder().metric(canaryMetricConfig).build(),
            0,
            canaryScope);

    return Collections.singletonMap("metricSetListId", metricSetListId);
  }
}
