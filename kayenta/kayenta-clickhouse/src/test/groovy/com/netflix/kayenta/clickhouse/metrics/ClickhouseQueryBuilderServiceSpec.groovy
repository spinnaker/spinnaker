package com.netflix.kayenta.clickhouse.metrics

import com.netflix.kayenta.canary.CanaryConfig
import com.netflix.kayenta.canary.CanaryMetricConfig
import com.netflix.kayenta.canary.CanaryScope
import com.netflix.kayenta.canary.providers.metrics.ClickhouseCanaryMetricSetQueryConfig
import spock.lang.Shared
import spock.lang.Specification

import java.time.Instant
import java.time.temporal.ChronoUnit

class ClickhouseQueryBuilderServiceSpec extends Specification {

  @Shared
  ClickhouseQueryBuilderService queryBuilder = new ClickhouseQueryBuilderService()

  @Shared
  Instant start = Instant.now()

  @Shared
  Instant end = start.plus(60, ChronoUnit.MINUTES)

  CanaryScope canaryScope() {
    return CanaryScope.builder()
      .scope('myservice-prod-v01')
      .location('us-west-2')
      .step(60L)
      .start(start)
      .end(end)
      .extendedScopeParams([:])
      .build()
  }

  void "expands an inline template using the standard template variables"() {
    given:
    ClickhouseCanaryMetricSetQueryConfig queryConfig =
      ClickhouseCanaryMetricSetQueryConfig.builder()
        .customInlineTemplate(
          "SELECT avg(Value) FROM otel_metrics_gauge " +
            "WHERE MetricName = 'requests' " +
            "AND ResourceAttributes['deployment.id'] = '\${scope}' " +
            "AND ResourceAttributes['region'] = '\${location}' " +
            "AND TimeUnix BETWEEN toDateTime(\${startEpochSeconds}) AND toDateTime(\${endEpochSeconds}) " +
            "GROUP BY toStartOfInterval(TimeUnix, INTERVAL \${step} SECOND) " +
            "ORDER BY 1")
        .build()
    CanaryMetricConfig canaryMetricConfig = CanaryMetricConfig.builder().query(queryConfig).build()
    CanaryConfig canaryConfig = CanaryConfig.builder().metric(canaryMetricConfig).build()
    CanaryScope scope = canaryScope()

    when:
    String query = queryBuilder.buildQuery(canaryConfig, scope, queryConfig)

    then:
    query ==
      "SELECT avg(Value) FROM otel_metrics_gauge " +
        "WHERE MetricName = 'requests' " +
        "AND ResourceAttributes['deployment.id'] = 'myservice-prod-v01' " +
        "AND ResourceAttributes['region'] = 'us-west-2' " +
        "AND TimeUnix BETWEEN toDateTime(${start.epochSecond}) AND toDateTime(${end.epochSecond}) " +
        "GROUP BY toStartOfInterval(TimeUnix, INTERVAL 60 SECOND) " +
        "ORDER BY 1"

    and: "the epoch-second values injected for template expansion don't leak into the scope's params"
    scope.extendedScopeParams == [:]
  }

  void "expands a named customFilterTemplate referenced from the canary config"() {
    given:
    ClickhouseCanaryMetricSetQueryConfig queryConfig =
      ClickhouseCanaryMetricSetQueryConfig.builder()
        .customFilterTemplate('myTemplate')
        .build()
    CanaryMetricConfig canaryMetricConfig = CanaryMetricConfig.builder().query(queryConfig).build()
    CanaryConfig canaryConfig =
      CanaryConfig.builder()
        .metric(canaryMetricConfig)
        .templates([myTemplate: "SELECT avg(Value) FROM otel_metrics_gauge WHERE ResourceAttributes['deployment.id'] = '\${scope}'"])
        .build()

    when:
    String query = queryBuilder.buildQuery(canaryConfig, canaryScope(), queryConfig)

    then:
    query == "SELECT avg(Value) FROM otel_metrics_gauge WHERE ResourceAttributes['deployment.id'] = 'myservice-prod-v01'"
  }

  void "throws when neither customInlineTemplate nor customFilterTemplate is set"() {
    given:
    ClickhouseCanaryMetricSetQueryConfig queryConfig = ClickhouseCanaryMetricSetQueryConfig.builder().build()
    CanaryMetricConfig canaryMetricConfig = CanaryMetricConfig.builder().query(queryConfig).build()
    CanaryConfig canaryConfig = CanaryConfig.builder().metric(canaryMetricConfig).build()

    when:
    queryBuilder.buildQuery(canaryConfig, canaryScope(), queryConfig)

    then:
    thrown(IllegalArgumentException)
  }
}
