package com.netflix.kayenta.clickhouse.metrics;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.ClickhouseCanaryMetricSetQueryConfig;
import com.netflix.kayenta.clickhouse.security.ClickhouseNamedAccountCredentials;
import com.netflix.kayenta.clickhouse.service.ClickhouseRemoteService;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.metrics.MetricsService;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import com.netflix.spectator.api.Registry;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.Builder;
import lombok.Getter;
import lombok.Singular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Executes the SQL resolved by {@link ClickhouseQueryBuilderService} against a Clickhouse account
 * and maps the returned rows into a single {@link MetricSet}. The query is entirely user-authored:
 * this service reads exactly one numeric value per row, in row order, and treats row {@code i} as
 * the sample at {@code canaryScope.start + i * step}.
 */
@Builder
@Slf4j
public class ClickhouseMetricsService implements MetricsService {

  @NotNull @Singular @Getter private List<String> accountNames;

  @Autowired private final AccountCredentialsRepository accountCredentialsRepository;

  @Autowired private final Registry registry;

  @Autowired private final ClickhouseQueryBuilderService queryBuilder;

  @Override
  public String getType() {
    return ClickhouseCanaryMetricSetQueryConfig.SERVICE_TYPE;
  }

  @Override
  public boolean servicesAccount(String accountName) {
    return accountNames.contains(accountName);
  }

  @Override
  public String buildQuery(
      String metricsAccountName,
      CanaryConfig canaryConfig,
      CanaryMetricConfig canaryMetricConfig,
      CanaryScope canaryScope) {
    ClickhouseCanaryMetricSetQueryConfig queryConfig =
        (ClickhouseCanaryMetricSetQueryConfig) canaryMetricConfig.getQuery();

    return queryBuilder.buildQuery(canaryConfig, canaryScope, queryConfig);
  }

  @Override
  public List<MetricSet> queryMetrics(
      String accountName,
      CanaryConfig canaryConfig,
      CanaryMetricConfig canaryMetricConfig,
      CanaryScope canaryScope)
      throws IOException {
    ClickhouseNamedAccountCredentials accountCredentials =
        accountCredentialsRepository.getRequiredOne(accountName);

    ClickhouseRemoteService remoteService = accountCredentials.getClickhouseRemoteService();

    String query = buildQuery(accountName, canaryConfig, canaryMetricConfig, canaryScope);

    log.debug("Query sent to Clickhouse account {}: {}", accountName, query);

    List<Double> values = remoteService.queryValues(query);

    return Collections.singletonList(
        MetricSet.builder()
            .name(canaryMetricConfig.getName())
            .startTimeMillis(canaryScope.getStart().toEpochMilli())
            .startTimeIso(canaryScope.getStart().toString())
            .endTimeMillis(canaryScope.getEnd().toEpochMilli())
            .endTimeIso(canaryScope.getEnd().toString())
            .stepMillis(TimeUnit.SECONDS.toMillis(canaryScope.getStep()))
            .values(values)
            .attribute("query", query)
            .build());
  }
}
