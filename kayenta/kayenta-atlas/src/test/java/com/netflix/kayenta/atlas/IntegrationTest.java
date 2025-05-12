package com.netflix.kayenta.atlas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.netflix.kayenta.atlas.canary.AtlasCanaryScope;
import com.netflix.kayenta.atlas.model.AtlasResults;
import com.netflix.kayenta.atlas.model.TimeseriesData;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.providers.metrics.AtlasCanaryMetricSetQueryConfig;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.*;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;

@Configuration
@ComponentScan({"com.netflix.kayenta.retrofit.config"})
class TestConfig {}

@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
class CanaryMetricConfigWithResults {
  @NotNull @Getter private CanaryMetricConfig canaryMetricConfig;

  @NotNull @Getter private List<AtlasResults> atlasResults;
}

@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = {TestConfig.class})
public class IntegrationTest {

  @Autowired private ResourceLoader resourceLoader;

  @Autowired ObjectMapper objectMapper;

  private void configureObjectMapper(ObjectMapper objectMapper) {
    objectMapper.registerSubtypes(AtlasCanaryMetricSetQueryConfig.class);
  }

  private String getFileContent(String filename) throws IOException {
    try (InputStream inputStream =
        resourceLoader.getResource("classpath:" + filename).getInputStream()) {
      return IOUtils.toString(inputStream, Charsets.UTF_8.name());
    }
  }

  private CanaryConfig getConfig(String filename) throws IOException {
    String contents = getFileContent(filename);
    configureObjectMapper(objectMapper);
    return objectMapper.readValue(contents, CanaryConfig.class);
  }

  private CanaryMetricConfigWithResults queryMetric(
      CanaryMetricConfig metric, AtlasCanaryScope scope) {
    long step = Duration.ofSeconds(scope.getStep()).toMillis();
    long start = scope.getStart().toEpochMilli() / step * step;
    long end = scope.getEnd().toEpochMilli() / step * step;
    long count = (end - start) / step;

    AtlasCanaryMetricSetQueryConfig atlasMetricSetQuery =
        (AtlasCanaryMetricSetQueryConfig) metric.getQuery();
    AtlasResults results =
        AtlasResults.builder()
            .id("dummyId")
            .start(start)
            .end(end)
            .label("dummyLabel")
            .query(atlasMetricSetQuery.getQ() + "," + scope.cq())
            .step(step)
            .type("timeseries")
            .data(TimeseriesData.dummy("array", count))
            .build();
    return CanaryMetricConfigWithResults.builder()
        .canaryMetricConfig(metric)
        .atlasResults(Collections.singletonList(results))
        .build();
  }

  @Test
  public void loadConfig() throws Exception {
    //   1.  Load canary config we want to use.
    CanaryConfig config = getConfig("com/netflix/kayenta/controllers/sample-config.json");

    //   2.  Define scope for baseline and canary clusters
    AtlasCanaryScope experiment = new AtlasCanaryScope();
    experiment.setType("asg");
    experiment.setScope("kayenta-iep-v400");
    experiment.setStart(Instant.parse("2000-01-01T00:11:22Z"));
    experiment.setEnd(Instant.parse("2000-01-01T04:11:22Z"));
    experiment.setStep(300L);
    AtlasCanaryScope control = new AtlasCanaryScope();
    control.setType("asg");
    control.setScope("kayenta-iep-v401");
    control.setStart(Instant.parse("2000-01-01T00:11:22Z"));
    control.setEnd(Instant.parse("2000-01-01T04:11:22Z"));
    control.setStep(300L);

    //   3.  for each metric in the config:
    //      a. issue an Atlas query for this metric, scoped to the canary
    //      b. issue an Atlas query for this metric, scoped to the baseline
    Map<CanaryMetricConfig, List<AtlasResults>> experimentMetrics =
        config.getMetrics().stream()
            .map((metric) -> queryMetric(metric, experiment))
            .collect(
                Collectors.toMap(
                    CanaryMetricConfigWithResults::getCanaryMetricConfig,
                    CanaryMetricConfigWithResults::getAtlasResults));
    Map<CanaryMetricConfig, List<AtlasResults>> controlMetrics =
        config.getMetrics().stream()
            .map((metric) -> queryMetric(metric, control))
            .collect(
                Collectors.toMap(
                    CanaryMetricConfigWithResults::getCanaryMetricConfig,
                    CanaryMetricConfigWithResults::getAtlasResults));

    System.out.println(experimentMetrics.toString());
    System.out.println(controlMetrics.toString());

    //   4. Collect all responses, and assemble into common metric archival format
    //   5. Profit!
  }
}
