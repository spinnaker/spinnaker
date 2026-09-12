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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.WavefrontCanaryMetricSetQueryConfig;
import com.netflix.kayenta.wavefront.canary.WavefrontCanaryScope;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class WavefrontMetricsServiceTest {

  private WavefrontMetricsService wavefrontMetricsService =
      new WavefrontMetricsService(null, null, null);
  private static final String METRIC_NAME = "example.metric.name";
  private static final String SCOPE = "env=test";
  private static final String AGGREGATE = "avg";

  @Test
  public void testBuildQuery_NoScopeProvided() {
    CanaryScope canaryScope = createScope("");
    CanaryMetricConfig canaryMetricSetQueryConfig = queryConfig(AGGREGATE);
    String query =
        wavefrontMetricsService.buildQuery("", null, canaryMetricSetQueryConfig, canaryScope);
    assertThat(query).isEqualTo(AGGREGATE + "(ts(" + METRIC_NAME + "))");
  }

  @Test
  public void testBuildQuery_NoAggregateProvided() {
    CanaryScope canaryScope = createScope(SCOPE);
    CanaryMetricConfig canaryMetricSetQueryConfig = queryConfig("");
    String query =
        wavefrontMetricsService.buildQuery("", null, canaryMetricSetQueryConfig, canaryScope);
    assertThat(query).isEqualTo("ts(" + METRIC_NAME + ", " + SCOPE + ")");
  }

  @Test
  public void testBuildQuery_ScopeAndAggregateProvided() {
    CanaryScope canaryScope = createScope(SCOPE);
    CanaryMetricConfig canaryMetricSetQueryConfig = queryConfig("avg");
    String query =
        wavefrontMetricsService.buildQuery("", null, canaryMetricSetQueryConfig, canaryScope);
    assertThat(query).isEqualTo(AGGREGATE + "(ts(" + METRIC_NAME + ", " + SCOPE + "))");
  }

  @Test
  public void testBuildQuery_customInlineTemplateIsUsedVerbatim() {
    WavefrontCanaryMetricSetQueryConfig queryConfig =
        WavefrontCanaryMetricSetQueryConfig.builder()
            .template("avg(ts(" + METRIC_NAME + ", autoscaling_group=${scope}))")
            .build();
    CanaryMetricConfig canaryMetricConfig = CanaryMetricConfig.builder().query(queryConfig).build();
    CanaryScope canaryScope = createScope(SCOPE);

    String query =
        wavefrontMetricsService.buildQuery(
            "", CanaryConfig.builder().build(), canaryMetricConfig, canaryScope);

    assertThat(query).isEqualTo("avg(ts(" + METRIC_NAME + ", autoscaling_group=" + SCOPE + "))");
  }

  @Test
  public void testBuildQuery_customFilterTemplateResolvesNamedEntry() {
    WavefrontCanaryMetricSetQueryConfig queryConfig =
        WavefrontCanaryMetricSetQueryConfig.builder().customFilterTemplate("myTemplate").build();
    CanaryMetricConfig canaryMetricConfig = CanaryMetricConfig.builder().query(queryConfig).build();

    CanaryConfig canaryConfig =
        CanaryConfig.builder()
            .templates(
                Collections.singletonMap(
                    "myTemplate", "avg(ts(" + METRIC_NAME + ", autoscaling_group=${scope}))"))
            .build();

    CanaryScope canaryScope = createScope(SCOPE);

    String query =
        wavefrontMetricsService.buildQuery("", canaryConfig, canaryMetricConfig, canaryScope);

    assertThat(query).isEqualTo("avg(ts(" + METRIC_NAME + ", autoscaling_group=" + SCOPE + "))");
  }

  private CanaryMetricConfig queryConfig(String aggregate) {
    WavefrontCanaryMetricSetQueryConfig wavefrontCanaryMetricSetQueryConfig =
        WavefrontCanaryMetricSetQueryConfig.builder()
            .aggregate(aggregate)
            .metricName(METRIC_NAME)
            .build();
    CanaryMetricConfig queryConfig =
        CanaryMetricConfig.builder().query(wavefrontCanaryMetricSetQueryConfig).build();
    return queryConfig;
  }

  private CanaryScope createScope(String scope) {
    WavefrontCanaryScope canaryScope = new WavefrontCanaryScope();
    canaryScope.setScope(scope);
    return canaryScope;
  }
}
