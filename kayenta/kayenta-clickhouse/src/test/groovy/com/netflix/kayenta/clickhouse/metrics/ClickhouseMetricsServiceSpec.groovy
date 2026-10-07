package com.netflix.kayenta.clickhouse.metrics

import com.netflix.kayenta.canary.CanaryConfig
import com.netflix.kayenta.canary.CanaryMetricConfig
import com.netflix.kayenta.canary.CanaryScope
import com.netflix.kayenta.canary.providers.metrics.ClickhouseCanaryMetricSetQueryConfig
import com.netflix.kayenta.clickhouse.security.ClickhouseNamedAccountCredentials
import com.netflix.kayenta.clickhouse.service.ClickhouseRemoteService
import com.netflix.kayenta.metrics.MetricSet
import com.netflix.kayenta.security.AccountCredentialsRepository
import com.netflix.spectator.api.NoopRegistry
import spock.lang.Specification

import java.time.Instant

class ClickhouseMetricsServiceSpec extends Specification {

  AccountCredentialsRepository accountCredentialsRepository = Mock()
  ClickhouseRemoteService clickhouseRemoteService = Mock()

  ClickhouseMetricsService clickhouseMetricsService =
    ClickhouseMetricsService.builder()
      .accountName("my-clickhouse-account")
      .accountCredentialsRepository(accountCredentialsRepository)
      .registry(new NoopRegistry())
      .queryBuilder(new ClickhouseQueryBuilderService())
      .build()

  void "queryMetrics executes the resolved SQL and maps rows positionally into a MetricSet"() {
    given:
    ClickhouseCanaryMetricSetQueryConfig queryConfig =
      ClickhouseCanaryMetricSetQueryConfig.builder()
        .customInlineTemplate("SELECT avg(Value) FROM otel_metrics_gauge WHERE MetricName = 'requests'")
        .build()
    CanaryMetricConfig canaryMetricConfig =
      CanaryMetricConfig.builder().name("requests").query(queryConfig).build()
    CanaryConfig canaryConfig = CanaryConfig.builder().metric(canaryMetricConfig).build()

    Instant start = Instant.parse("2020-01-01T00:00:00Z")
    Instant end = Instant.parse("2020-01-01T00:05:00Z")
    CanaryScope canaryScope =
      CanaryScope.builder()
        .scope("myapp-prod")
        .location("us-west-2")
        .start(start)
        .end(end)
        .step(60L)
        .extendedScopeParams([:])
        .build()

    ClickhouseNamedAccountCredentials credentials =
      ClickhouseNamedAccountCredentials.builder()
        .name("my-clickhouse-account")
        .clickhouseRemoteService(clickhouseRemoteService)
        .build()

    when:
    List<MetricSet> metricSets =
      clickhouseMetricsService.queryMetrics("my-clickhouse-account", canaryConfig, canaryMetricConfig, canaryScope)

    then:
    1 * accountCredentialsRepository.getRequiredOne("my-clickhouse-account") >> credentials
    1 * clickhouseRemoteService.queryValues(
      "SELECT avg(Value) FROM otel_metrics_gauge WHERE MetricName = 'requests'"
    ) >> [1.0d, 2.0d, Double.NaN, 4.0d, 5.0d]

    metricSets.size() == 1
    with(metricSets[0]) {
      name == "requests"
      startTimeMillis == start.toEpochMilli()
      endTimeMillis == end.toEpochMilli()
      stepMillis == 60_000L
      values == [1.0d, 2.0d, Double.NaN, 4.0d, 5.0d]
    }
  }

  void "servicesAccount only returns true for configured account names"() {
    expect:
    clickhouseMetricsService.servicesAccount("my-clickhouse-account")
    !clickhouseMetricsService.servicesAccount("some-other-account")
  }
}
