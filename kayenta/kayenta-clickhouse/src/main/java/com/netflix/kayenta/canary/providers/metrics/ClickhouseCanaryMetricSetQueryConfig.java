package com.netflix.kayenta.canary.providers.metrics;

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.util.StringUtils;

/**
 * The query for a Clickhouse metric is always a SQL statement supplied via {@link
 * #customInlineTemplate} or {@link #customFilterTemplate} - there is no structured/programmatic
 * query builder. Whether the SQL targets the OpenTelemetry Collector's Clickhouse exporter schema
 * (otel_metrics_gauge / otel_metrics_sum / otel_metrics_histogram) or is fully ad-hoc is entirely
 * up to the author of the template; Kayenta performs no query generation or automatic scope binding
 * beyond expanding the standard template variables (see {@link QueryConfigUtils}).
 */
@Builder(toBuilder = true)
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonTypeName("clickhouse")
public class ClickhouseCanaryMetricSetQueryConfig implements CanaryMetricSetQueryConfig {

  public static final String SERVICE_TYPE = "clickhouse";

  @Getter private String customInlineTemplate;

  @Getter private String customFilterTemplate;

  @Override
  public CanaryMetricSetQueryConfig cloneWithEscapedInlineTemplate() {
    if (StringUtils.isEmpty(customInlineTemplate)) {
      return this;
    } else {
      return this.toBuilder()
          .customInlineTemplate(customInlineTemplate.replace("${", "$\\{"))
          .build();
    }
  }

  @Override
  public String getServiceType() {
    return SERVICE_TYPE;
  }
}
