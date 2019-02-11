/*
 * Copyright 2019 Intuit, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.kayenta.wavefront.metrics;

import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.WavefrontCanaryMetricSetQueryConfig;
import com.netflix.kayenta.wavefront.canary.WavefrontCanaryScope;
import org.junit.Test;

import java.time.Instant;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class WavefrontMetricsServiceTest {

    private WavefrontMetricsService wavefrontMetricsService = new WavefrontMetricsService(null, null, null);
    private static final String METRIC_NAME = "example.metric.name";
    private static final String SCOPE = "env=test";
    private static final String AGGREGATE= "avg";

    @Test
    public void testBuildQuery_NoScopeProvided() {
        CanaryScope canaryScope = createScope("");
        CanaryMetricConfig canaryMetricSetQueryConfig = queryConfig(AGGREGATE);
        String query = wavefrontMetricsService.buildQuery("", null, canaryMetricSetQueryConfig, canaryScope );
        assertThat(query, is(AGGREGATE +"(ts("+METRIC_NAME+"))"));
    }

    @Test
    public void testBuildQuery_NoAggregateProvided() {
        CanaryScope canaryScope = createScope(SCOPE);
        CanaryMetricConfig canaryMetricSetQueryConfig = queryConfig("");
        String query = wavefrontMetricsService.buildQuery("", null, canaryMetricSetQueryConfig, canaryScope );
        assertThat(query, is("ts("+METRIC_NAME+", " + SCOPE +")"));
    }

    @Test
    public void testBuildQuery_ScopeAndAggregateProvided() {
        CanaryScope canaryScope = createScope(SCOPE);
        CanaryMetricConfig canaryMetricSetQueryConfig = queryConfig("avg");
        String query = wavefrontMetricsService.buildQuery("", null, canaryMetricSetQueryConfig, canaryScope );
        assertThat(query, is(AGGREGATE +"(ts("+METRIC_NAME+", "+SCOPE +"))"));
    }

    private CanaryMetricConfig queryConfig(String aggregate) {
        WavefrontCanaryMetricSetQueryConfig wavefrontCanaryMetricSetQueryConfig = WavefrontCanaryMetricSetQueryConfig
                .builder()
                .aggregate(aggregate)
                .metricName(METRIC_NAME)
                .build();
        CanaryMetricConfig queryConfig = CanaryMetricConfig.builder().query(wavefrontCanaryMetricSetQueryConfig).build();
        return queryConfig;
    }

    private CanaryScope createScope(String scope) {
        WavefrontCanaryScope canaryScope = new WavefrontCanaryScope();
        canaryScope.setScope(scope);
        return canaryScope;
    }
}
