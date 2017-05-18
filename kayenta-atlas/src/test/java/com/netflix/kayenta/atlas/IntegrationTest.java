package com.netflix.kayenta.atlas;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import com.netflix.kayenta.atlas.model.AtlasResults;
import com.netflix.kayenta.atlas.model.TimeseriesData;
import com.netflix.kayenta.atlas.security.AtlasNamedAccountCredentials;
import com.netflix.kayenta.atlas.service.AtlasRemoteService;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.metrics.MetricSet;
import com.netflix.kayenta.security.AccountCredentialsRepository;
import lombok.*;
import org.apache.commons.io.IOUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.validation.constraints.NotNull;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
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

    private CanaryMetricConfigWithResults queryMetric(CanaryMetricConfig metric, CanaryScope scope) {
        long step = scope.getStep();
        long start = scope.getStart() / step * step;
        long end = scope.getEnd() / step * step;
        long count = (end - start) / step;

        AtlasResults results = AtlasResults.builder()
                .id("dummyId")
                .start(start)
                .end(end)
                .label("dummyLabel")
                .query(metric.getQuery() + "," + scope.cq())
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
        CanaryScope experiment = CanaryScope.builder()
                .type("application")
                .scope("app_leo")
                .start(0L)
                .end(600000L)
                .step(60000L)
                .build();
        CanaryScope control = CanaryScope.builder()
                .type("application")
                .scope("app_lep")
                .start(0L)
                .end(600000L)
                .step(60000L)
                .build();

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
