/*
 * Copyright 2018 Adobe
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

package com.netflix.kayenta.newrelic.controller;

import static com.netflix.kayenta.canary.util.FetchControllerUtils.determineDefaultProperty;

import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.NewRelicCanaryMetricSetQueryConfig;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
import com.netflix.kayenta.newrelic.config.NewRelicConfigurationTestControllerDefaultProperties;
import com.netflix.kayenta.security.AccountCredentials;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.kayenta.security.CredentialsHelper;
import io.swagger.annotations.ApiParam;
import java.io.IOException;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fetch/newrelic")
@Slf4j
public class NewRelicFetchController {

  private final AccountCredentialsRepository accountCredentialsRepository;
  private final SynchronousQueryProcessor synchronousQueryProcessor;
  private final NewRelicConfigurationTestControllerDefaultProperties
    newrelicConfigurationTestControllerDefaultProperties;

  @Autowired
  public NewRelicFetchController(
    AccountCredentialsRepository accountCredentialsRepository,
    SynchronousQueryProcessor synchronousQueryProcessor,
    NewRelicConfigurationTestControllerDefaultProperties newrelicConfigurationTestControllerDefaultProperties) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.synchronousQueryProcessor = synchronousQueryProcessor;
    this.newrelicConfigurationTestControllerDefaultProperties =
      newrelicConfigurationTestControllerDefaultProperties;
  }

  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public Map queryMetrics(
    @RequestParam(required = false) final String metricsAccountName,
    @RequestParam(required = false) final String storageAccountName,
    @ApiParam(defaultValue = "cpu") @RequestParam String metricSetName,
    @ApiParam(defaultValue = "avg:system.cpu.user") @RequestParam String metricName,
    @ApiParam(value = "The scope of the NewRelic query. e.g. autoscaling_group:myapp-prod-v002")
    @RequestParam(required = false) String scope,
    @ApiParam(value = "An ISO format timestamp, e.g.: 2018-03-15T01:23:45Z")
    @RequestParam String start,
    @ApiParam(value = "An ISO format timestamp, e.g.: 2018-03-15T01:23:45Z")
    @RequestParam String end,
    @ApiParam(defaultValue = "60", value = "seconds") @RequestParam Long step,
    @ApiParam(defaultValue = "false")
    @RequestParam(required = false) final boolean dryRun)
    throws IOException {

    // Apply defaults.
    scope = determineDefaultProperty(scope, "scope",
      newrelicConfigurationTestControllerDefaultProperties);
    start = determineDefaultProperty(start, "start",
      newrelicConfigurationTestControllerDefaultProperties);
    end = determineDefaultProperty(end, "end",
      newrelicConfigurationTestControllerDefaultProperties);

    if (StringUtils.isEmpty(start)) {
      throw new IllegalArgumentException("Start time is required.");
    }

    if (StringUtils.isEmpty(end)) {
      throw new IllegalArgumentException("End time is required.");
    }

    String resolvedMetricsAccountName =
      CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
        AccountCredentials.Type.METRICS_STORE,
        accountCredentialsRepository);
    String resolvedStorageAccountName =
      CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
        AccountCredentials.Type.OBJECT_STORE,
        accountCredentialsRepository);

    NewRelicCanaryMetricSetQueryConfig newrelicCanaryMetricSetQueryConfig =
      NewRelicCanaryMetricSetQueryConfig
        .builder()
        .q(metricName)
        .build();

    CanaryMetricConfig canaryMetricConfig =
      CanaryMetricConfig
        .builder()
        .name(metricSetName)
        .query(newrelicCanaryMetricSetQueryConfig)
        .build();

    CanaryScope canaryScope =
      new CanaryScope(scope, null, Instant.parse(start), Instant.parse(end), step,
        Collections.emptyMap());

    return synchronousQueryProcessor
      .processQueryAndReturnMap(resolvedMetricsAccountName, resolvedStorageAccountName,
        null, canaryMetricConfig, 0, canaryScope, dryRun);
  }
}
