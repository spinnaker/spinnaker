/*
 * Copyright 2026 Snap Inc.
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

package com.netflix.kayenta.graphite.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.GraphiteCanaryMetricSetQueryConfig;
import com.netflix.kayenta.graphite.canary.GraphiteCanaryScope;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class GraphiteMetricsServiceTest {

  private final GraphiteMetricsService graphiteMetricsService =
      GraphiteMetricsService.builder().build();

  @Test
  public void testBuildQuery_customInlineTemplateIsUsedVerbatim() {
    GraphiteCanaryMetricSetQueryConfig queryConfig =
        GraphiteCanaryMetricSetQueryConfig.builder().template("servers.${scope}.cpu").build();
    CanaryMetricConfig canaryMetricConfig = CanaryMetricConfig.builder().query(queryConfig).build();

    CanaryScope canaryScope = createScope("myapp-prod-v002", "us-east-1");

    String query =
        graphiteMetricsService.buildQuery(
            "account", CanaryConfig.builder().build(), canaryMetricConfig, canaryScope);

    assertThat(query).isEqualTo("servers.myapp-prod-v002.cpu");
  }

  @Test
  public void testBuildQuery_noTemplateFallsBackToLegacyComposition() {
    GraphiteCanaryMetricSetQueryConfig queryConfig =
        GraphiteCanaryMetricSetQueryConfig.builder().metricName("servers.$scope.cpu").build();
    CanaryMetricConfig canaryMetricConfig = CanaryMetricConfig.builder().query(queryConfig).build();

    CanaryScope canaryScope = createScope("myapp-prod-v002", "us-east-1");

    String query =
        graphiteMetricsService.buildQuery(
            "account", CanaryConfig.builder().build(), canaryMetricConfig, canaryScope);

    assertThat(query).isEqualTo("servers.myapp-prod-v002.cpu");
  }

  @Test
  public void testBuildQuery_customFilterTemplateResolvesNamedEntry() {
    GraphiteCanaryMetricSetQueryConfig queryConfig =
        GraphiteCanaryMetricSetQueryConfig.builder().customFilterTemplate("cpuTemplate").build();
    CanaryMetricConfig canaryMetricConfig = CanaryMetricConfig.builder().query(queryConfig).build();

    CanaryConfig canaryConfig =
        CanaryConfig.builder()
            .templates(Collections.singletonMap("cpuTemplate", "servers.${scope}.cpu"))
            .build();

    CanaryScope canaryScope = createScope("myapp-prod-v002", "us-east-1");

    String query =
        graphiteMetricsService.buildQuery("account", canaryConfig, canaryMetricConfig, canaryScope);

    assertThat(query).isEqualTo("servers.myapp-prod-v002.cpu");
  }

  private CanaryScope createScope(String scope, String location) {
    GraphiteCanaryScope canaryScope = new GraphiteCanaryScope();
    canaryScope.setScope(scope);
    canaryScope.setLocation(location);
    return canaryScope;
  }
}
