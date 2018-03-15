package com.netflix.kayenta.datadog.controller;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.DatadogCanaryMetricSetQueryConfig;
import com.netflix.kayenta.datadog.config.DatadogConfigurationTestControllerDefaultProperties;
import com.netflix.kayenta.metrics.SynchronousQueryProcessor;
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
import java.util.Map;

import static com.netflix.kayenta.canary.util.FetchControllerUtils.determineDefaultProperty;


@RestController
@RequestMapping("/fetch/datadog")
@Slf4j
public class DatadogFetchController {
  private final AccountCredentialsRepository accountCredentialsRepository;
  private final SynchronousQueryProcessor synchronousQueryProcessor;
  private final DatadogConfigurationTestControllerDefaultProperties datadogConfigurationTestControllerDefaultProperties;

  @Autowired
  public DatadogFetchController(AccountCredentialsRepository accountCredentialsRepository,
                                SynchronousQueryProcessor synchronousQueryProcessor,
                                DatadogConfigurationTestControllerDefaultProperties datadogConfigurationTestControllerDefaultProperties) {
    this.accountCredentialsRepository = accountCredentialsRepository;
    this.synchronousQueryProcessor = synchronousQueryProcessor;
    this.datadogConfigurationTestControllerDefaultProperties = datadogConfigurationTestControllerDefaultProperties;
  }

  @RequestMapping(value = "/query", method = RequestMethod.POST)
  public Map queryMetrics(@RequestParam(required = false) final String metricsAccountName,
                          @RequestParam(required = false) final String storageAccountName,
                          @ApiParam(defaultValue = "cpu") @RequestParam String metricSetName,
                          @ApiParam(defaultValue = "avg:system.cpu.user") @RequestParam String metricName,
                          @ApiParam(value = "The scope of the Datadog query. e.g. autoscaling_group:myapp-prod-v002")
                            @RequestParam(required = false) String scope,
                          @ApiParam(value = "An ISO format timestamp, e.g.: 2018-03-15T01:23:45Z")
                            @RequestParam String start,
                          @ApiParam(value = "An ISO format timestamp, e.g.: 2018-03-15T01:23:45Z")
                            @RequestParam String end,
                          @ApiParam(defaultValue = "60", value = "seconds") @RequestParam Long step) throws IOException {
    // Apply defaults.
    scope = determineDefaultProperty(scope, "scope", datadogConfigurationTestControllerDefaultProperties);
    start = determineDefaultProperty(start, "start", datadogConfigurationTestControllerDefaultProperties);
    end = determineDefaultProperty(end, "end", datadogConfigurationTestControllerDefaultProperties);

    if (StringUtils.isEmpty(start)) {
      throw new IllegalArgumentException("Start time is required.");
    }

    if (StringUtils.isEmpty(end)) {
      throw new IllegalArgumentException("End time is required.");
    }

    String resolvedMetricsAccountName = CredentialsHelper.resolveAccountByNameOrType(metricsAccountName,
      AccountCredentials.Type.METRICS_STORE,
      accountCredentialsRepository);
    String resolvedStorageAccountName = CredentialsHelper.resolveAccountByNameOrType(storageAccountName,
      AccountCredentials.Type.OBJECT_STORE,
      accountCredentialsRepository);

    DatadogCanaryMetricSetQueryConfig datadogCanaryMetricSetQueryConfig =
      DatadogCanaryMetricSetQueryConfig
        .builder()
        .metricName(metricName)
        .build();

    CanaryMetricConfig canaryMetricConfig =
      CanaryMetricConfig
        .builder()
        .name(metricSetName)
        .query(datadogCanaryMetricSetQueryConfig)
        .build();

    CanaryScope canaryScope = new CanaryScope(scope, null /* region */, Instant.parse(start), Instant.parse(end), step, Collections.emptyMap());

    String metricSetListId = synchronousQueryProcessor.processQuery(resolvedMetricsAccountName,
                                                                    resolvedStorageAccountName,
                                                                    CanaryConfig.builder().metric(canaryMetricConfig).build(),
                                                                    0,
                                                                    canaryScope);

    return Collections.singletonMap("metricSetListId", metricSetListId);
  }
}
