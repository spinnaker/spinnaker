package com.netflix.kayenta.clickhouse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.providers.metrics.ClickhouseCanaryMetricSetQueryConfig;
import org.junit.jupiter.api.Test;

/**
 * Verifies that a canary config referencing {@code "type": "clickhouse"} round-trips through
 * Jackson polymorphic (de)serialization into a {@link ClickhouseCanaryMetricSetQueryConfig},
 * exercising the same {@code @JsonTypeName} mechanism used at runtime by kork's
 * ObjectMapperSubtypeConfigurer (which scans the shared {@code
 * com.netflix.kayenta.canary.providers.metrics} package across all provider jars).
 */
public class ClickhouseIntegrationTest {

  private static final String CANARY_CONFIG_JSON =
      "{"
          + "\"name\": \"clickhouse-sample-config\","
          + "\"metrics\": [{"
          + "  \"name\": \"requests-per-second\","
          + "  \"query\": {"
          + "    \"type\": \"clickhouse\","
          + "    \"customInlineTemplate\": \"SELECT avg(Value) FROM otel_metrics_gauge WHERE ResourceAttributes['deployment.id'] = '${scope}'\""
          + "  }"
          + "}]"
          + "}";

  @Test
  public void loadConfig() throws Exception {
    ObjectMapper objectMapper = new ObjectMapper();
    objectMapper.registerSubtypes(ClickhouseCanaryMetricSetQueryConfig.class);

    CanaryConfig config = objectMapper.readValue(CANARY_CONFIG_JSON, CanaryConfig.class);

    assertEquals(1, config.getMetrics().size());

    CanaryMetricConfig metric = config.getMetrics().get(0);
    assertInstanceOf(ClickhouseCanaryMetricSetQueryConfig.class, metric.getQuery());

    ClickhouseCanaryMetricSetQueryConfig queryConfig =
        (ClickhouseCanaryMetricSetQueryConfig) metric.getQuery();
    assertEquals(
        "SELECT avg(Value) FROM otel_metrics_gauge WHERE ResourceAttributes['deployment.id'] = '${scope}'",
        queryConfig.getCustomInlineTemplate());
  }
}
