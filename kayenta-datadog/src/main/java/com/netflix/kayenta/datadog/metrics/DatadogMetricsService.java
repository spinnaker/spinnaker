package com.netflix.kayenta.datadog.metrics;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.DatadogCanaryMetricSetQueryConfig;
import com.netflix.kayenta.datadog.security.DatadogCredentials;
import com.netflix.kayenta.datadog.security.DatadogNamedAccountCredentials;
import com.netflix.kayenta.datadog.service.DatadogRemoteService;
import com.netflix.kayenta.datadog.service.DatadogTimeSeries;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.spectator.api.Registry;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Builder
@Slf4j
public class DatadogMetricsService implements MetricsService {
  @NotNull
  @Singular
  @Getter
  private List<String> accountNames;

  @Autowired
  private final AccountCredentialsRepository accountCredentialsRepository;

  @Autowired
  private final Registry registry;

  @Override
  public String getType() {
    return "datadog";
  }

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public List<MetricSet> queryMetrics(String accountName, CanaryConfig canaryConfig, CanaryMetricConfig canaryMetricConfig, CanaryScope canaryScope) throws IOException {
    DatadogNamedAccountCredentials accountCredentials = (DatadogNamedAccountCredentials)accountCredentialsRepository
      .getOne(accountName)
      .orElseThrow(() -> new IllegalArgumentException("Unable to resolve account " + accountName + "."));

    DatadogCredentials credentials = accountCredentials.getCredentials();
    DatadogRemoteService remoteService = accountCredentials.getDatadogRemoteService();
    DatadogCanaryMetricSetQueryConfig queryConfig = (DatadogCanaryMetricSetQueryConfig)canaryMetricConfig.getQuery();

    DatadogTimeSeries timeSeries = remoteService.getTimeSeries(
      credentials.getApiKey(),
      credentials.getApplicationKey(),
      (int)canaryScope.getStart().getEpochSecond(),
      (int)canaryScope.getEnd().getEpochSecond(),
      queryConfig.getMetricName() + "{" + canaryScope.getScope() + "}"
    );

    List<MetricSet> ret = new ArrayList<MetricSet>();

    for (DatadogTimeSeries.DatadogSeriesEntry series : timeSeries.getSeries()) {
      ret.add(
        MetricSet.builder()
          .name(canaryMetricConfig.getName())
          .startTimeMillis(series.getStart())
          .startTimeIso(Instant.ofEpochMilli(series.getStart()).toString())
          .stepMillis(series.getInterval() * 1000)
          .values(series.getDataPoints().collect(Collectors.toList()))
          .build()
      );
    }

    return ret;
  }
}
