package com.netflix.kayenta.canary.providers.metrics;

import com.fasterxml.jackson.annotation.JsonTypeName;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * The query for a Clickhouse metric is always a SQL statement supplied via {@link #getTemplate} or
 * {@link #getCustomFilterTemplate} - there is no structured/programmatic query builder. Whether the
 * SQL targets the OpenTelemetry Collector's Clickhouse exporter schema (otel_metrics_gauge /
 * otel_metrics_sum / otel_metrics_histogram) or is fully ad-hoc is entirely up to the author of the
 * template; Kayenta performs no query generation or automatic scope binding beyond expanding the
 * standard template variables (see {@link QueryConfigUtils}).
 */
@SuperBuilder(toBuilder = true)
@ToString
@NoArgsConstructor
@JsonTypeName("clickhouse")
public class ClickhouseCanaryMetricSetQueryConfig extends AbstractCanaryMetricSetQueryConfig {

  public static final String SERVICE_TYPE = "clickhouse";

  @Override
  public String getServiceType() {
    return SERVICE_TYPE;
  }
}
