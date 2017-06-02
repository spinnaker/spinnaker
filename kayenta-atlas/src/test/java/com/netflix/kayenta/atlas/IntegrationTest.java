package com.netflix.kayenta.atlas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.netflix.kayenta.atlas.canary.AtlasCanaryScope;
import com.netflix.kayenta.atlas.model.AtlasResults;
import com.netflix.kayenta.atlas.model.TimeseriesData;
import com.netflix.kayenta.canary.AtlasCanaryMetricSetQueryConfig;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;
import static com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES;

@Configuration
class TestConfig {}

@Builder
@ToString
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
class CanaryMetricConfigWithResults {
    @NotNull
    @Getter
    private CanaryMetricConfig canaryMetricConfig;

    @NotNull
    @Getter
    private List<AtlasResults> atlasResults;
}

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {TestConfig.class})
public class IntegrationTest {
    @Autowired
    private ResourceLoader resourceLoader;

    private ObjectMapper objectMapper = new ObjectMapper()
            .setSerializationInclusion(NON_NULL)
            .disable(FAIL_ON_UNKNOWN_PROPERTIES);

    private String getFileContent(String filename) throws IOException {
        try (InputStream inputStream = resourceLoader.getResource("classpath:" + filename).getInputStream()) {
            return IOUtils.toString(inputStream, Charsets.UTF_8.name());
        }
    }

    private CanaryConfig getConfig(String filename) throws IOException {
        String contents = getFileContent(filename);
        return objectMapper.readValue(contents, CanaryConfig.class);
    }

    private CanaryMetricConfigWithResults queryMetric(CanaryMetricConfig metric, AtlasCanaryScope scope) {
        long step = Duration.parse(scope.getStep()).toMillis();
        long start = Long.parseLong(scope.getStart()) / step * step;
        long end = Long.parseLong(scope.getEnd()) / step * step;
        long count = (end - start) / step;

        AtlasCanaryMetricSetQueryConfig atlasMetricSetQuery = (AtlasCanaryMetricSetQueryConfig)metric.getQuery();
        AtlasResults results = AtlasResults.builder()
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
        experiment.setType("application");
        experiment.setScope("app_leo");
        experiment.setStart("0");
        experiment.setEnd("600000");
        experiment.setStep("PT1M");
        AtlasCanaryScope control = new AtlasCanaryScope();
        control.setType("application");
        control.setScope("app_lep");
        control.setStart("0");
        control.setEnd("600000");
        control.setStep("PT1M");

        //   3.  for each metric in the config:
        //      a. issue an Atlas query for this metric, scoped to the canary
        //      b. issue an Atlas query for this metric, scoped to the baseline
        Map<CanaryMetricConfig, List<AtlasResults>> experimentMetrics = config.getMetrics().stream()
                .map((metric) -> queryMetric(metric, experiment))
                .collect(Collectors.toMap(CanaryMetricConfigWithResults::getCanaryMetricConfig,
                        CanaryMetricConfigWithResults::getAtlasResults));
        Map<CanaryMetricConfig, List<AtlasResults>> controlMetrics = config.getMetrics().stream()
                .map((metric) -> queryMetric(metric, control))
                .collect(Collectors.toMap(CanaryMetricConfigWithResults::getCanaryMetricConfig,
                        CanaryMetricConfigWithResults::getAtlasResults));

        System.out.println(experimentMetrics.toString());
        System.out.println(controlMetrics.toString());

        //   4. Collect all responses, and assemble into common metric archival format
        //   5. Profit!
    }

}
