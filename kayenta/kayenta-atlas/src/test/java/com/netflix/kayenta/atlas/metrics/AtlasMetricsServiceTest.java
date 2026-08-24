/*
 * Copyright 2026 Google, Inc.
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

package com.netflix.kayenta.atlas.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.kayenta.atlas.canary.AtlasCanaryScope;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricConfig;
import com.netflix.kayenta.canary.providers.metrics.AtlasCanaryMetricSetQueryConfig;
import java.util.Collections;
import org.junit.jupiter.api.Test;

public class AtlasMetricsServiceTest {

  private final AtlasMetricsService atlasMetricsService = AtlasMetricsService.builder().build();

  @Test
  public void testBuildQuery_customInlineTemplateIsUsedVerbatim() {
    AtlasCanaryMetricSetQueryConfig queryConfig =
        AtlasCanaryMetricSetQueryConfig.builder()
            .template("name,requestsPerSecond,:eq,:list,(,nf.cluster,${scope},:eq,:cq,),:each")
            .build();
    CanaryMetricConfig canaryMetricConfig = CanaryMetricConfig.builder().query(queryConfig).build();

    AtlasCanaryScope canaryScope = createScope();

    String query =
        atlasMetricsService.buildQuery(
            "account", CanaryConfig.builder().build(), canaryMetricConfig, canaryScope);

    assertThat(query)
        .isEqualTo("name,requestsPerSecond,:eq,:list,(,nf.cluster,myapp-prod-v002,:eq,:cq,),:each");
  }

  @Test
  public void testBuildQuery_noTemplateFallsBackToLegacyComposition() {
    AtlasCanaryMetricSetQueryConfig queryConfig =
        AtlasCanaryMetricSetQueryConfig.builder().q("name,requestsPerSecond,:eq").build();
    CanaryMetricConfig canaryMetricConfig = CanaryMetricConfig.builder().query(queryConfig).build();

    AtlasCanaryScope canaryScope = createScope();

    String query =
        atlasMetricsService.buildQuery(
            "account", CanaryConfig.builder().build(), canaryMetricConfig, canaryScope);

    assertThat(query)
        .isEqualTo("name,requestsPerSecond,:eq,:list,(,nf.cluster,myapp-prod-v002,:eq,:cq,),:each");
  }

  @Test
  public void testBuildQuery_customFilterTemplateResolvesNamedEntry() {
    AtlasCanaryMetricSetQueryConfig queryConfig =
        AtlasCanaryMetricSetQueryConfig.builder().customFilterTemplate("myTemplate").build();
    CanaryMetricConfig canaryMetricConfig = CanaryMetricConfig.builder().query(queryConfig).build();

    CanaryConfig canaryConfig =
        CanaryConfig.builder()
            .templates(
                Collections.singletonMap(
                    "myTemplate",
                    "name,requestsPerSecond,:eq,:list,(,nf.cluster,${scope},:eq,:cq,),:each"))
            .build();

    AtlasCanaryScope canaryScope = createScope();

    String query =
        atlasMetricsService.buildQuery("account", canaryConfig, canaryMetricConfig, canaryScope);

    assertThat(query)
        .isEqualTo("name,requestsPerSecond,:eq,:list,(,nf.cluster,myapp-prod-v002,:eq,:cq,),:each");
  }

  private AtlasCanaryScope createScope() {
    AtlasCanaryScope canaryScope = new AtlasCanaryScope();
    canaryScope.setType("cluster");
    canaryScope.setScope("myapp-prod-v002");
    canaryScope.setDeployment("main");
    canaryScope.setDataset("regional");
    canaryScope.setEnvironment("prod");
    return canaryScope;
  }
}
