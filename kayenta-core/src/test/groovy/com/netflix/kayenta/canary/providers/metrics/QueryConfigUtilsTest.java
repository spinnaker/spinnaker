/*
 * Copyright 2020 Playtika
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

package com.netflix.kayenta.canary.providers.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryMetricSetQueryConfig;
import com.netflix.kayenta.canary.CanaryScope;
import org.junit.Test;

public class QueryConfigUtilsTest {

  @Test
  public void expandCustomFilter_returnsNullIfTemplatesNotSet() {
    CanaryConfig canaryConfig = CanaryConfig.builder().build();
    TestCanaryMetricSetQueryConfig metricSetQuery = new TestCanaryMetricSetQueryConfig();
    CanaryScope canaryScope = CanaryScope.builder().build();
    String[] baseScopeAttribute = {};

    String actual =
        QueryConfigUtils.expandCustomFilter(
            canaryConfig, metricSetQuery, canaryScope, baseScopeAttribute);

    assertThat(actual).isNull();
  }

  @Test
  public void
      expandCustomFilter_returnsExpandedTemplateWithExtractedBaseScopeAttributesFromFilterTemplate() {
    CanaryConfig canaryConfig = CanaryConfig.builder().build();
    TestCanaryMetricSetQueryConfig metricSetQuery = new TestCanaryMetricSetQueryConfig();
    metricSetQuery.setCustomInlineTemplate(
        "test template ${p1} ${p2} ${scope} ${location} ${serviceType} $\\{p1} $\\{p2}");
    metricSetQuery.setServiceType("my-service-type");
    CanaryScope canaryScope =
        CanaryScope.builder()
            .scope("my-test-scope")
            .location("my-test-location")
            .extendedScopeParam("p1", "v1")
            .extendedScopeParam("p2", "v2")
            .build();
    String[] baseScopeAttribute = {"scope", "location", "serviceType"};

    assertThatThrownBy(
            () ->
                QueryConfigUtils.expandCustomFilter(
                    canaryConfig, metricSetQuery, canaryScope, baseScopeAttribute))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Unable to find property 'serviceType'."); // see:
    // https://github.com/spinnaker/kayenta/pull/659#issuecomment-579819419

    // instead should be:
    //    assertThat(actual)
    //        .isEqualTo("test template v1 v2 my-test-scope my-test-location my-service-type v1
    // v2");
  }

  @Test
  public void
      expandCustomFilter_returnsExpandedTemplateIfAllParametersAvailableFromFilterTemplate() {
    CanaryConfig canaryConfig =
        CanaryConfig.builder()
            .template("test-template", "test template ${p1} ${p2} $\\{p1} $\\{p2}")
            .build();
    CanaryMetricSetQueryConfig metricSetQuery = mock(CanaryMetricSetQueryConfig.class);
    CanaryScope canaryScope =
        CanaryScope.builder().extendedScopeParam("p1", "v1").extendedScopeParam("p2", "v2").build();
    String[] baseScopeAttribute = {};
    when(metricSetQuery.getCustomFilterTemplate()).thenReturn("test-template");

    String actual =
        QueryConfigUtils.expandCustomFilter(
            canaryConfig, metricSetQuery, canaryScope, baseScopeAttribute);

    assertThat(actual).isEqualTo("test template v1 v2 v1 v2");
  }

  @Test
  public void
      expandCustomFilter_returnsExpandedTemplateIfAllParametersAvailableFromInlineTemplate() {
    CanaryConfig canaryConfig = CanaryConfig.builder().build();
    CanaryMetricSetQueryConfig metricSetQuery = mock(CanaryMetricSetQueryConfig.class);
    CanaryScope canaryScope =
        CanaryScope.builder().extendedScopeParam("p1", "v1").extendedScopeParam("p2", "v2").build();
    String[] baseScopeAttribute = {};
    when(metricSetQuery.getCustomInlineTemplate())
        .thenReturn("test template ${p1} ${p2} $\\{p1} $\\{p2}");

    String actual =
        QueryConfigUtils.expandCustomFilter(
            canaryConfig, metricSetQuery, canaryScope, baseScopeAttribute);

    assertThat(actual).isEqualTo("test template v1 v2 v1 v2");
  }

  @Test
  public void expandCustomFilter_failsWithExceptionIfParameterMissing() {
    CanaryConfig canaryConfig = CanaryConfig.builder().build();
    CanaryMetricSetQueryConfig metricSetQuery = mock(CanaryMetricSetQueryConfig.class);
    CanaryScope canaryScope =
        CanaryScope.builder().extendedScopeParam("p1", "v1").extendedScopeParam("p2", "v2").build();
    String[] baseScopeAttribute = {};
    when(metricSetQuery.getCustomInlineTemplate())
        .thenReturn("test template ${unknown-param} ${p2}");

    assertThatThrownBy(
            () ->
                QueryConfigUtils.expandCustomFilter(
                    canaryConfig, metricSetQuery, canaryScope, baseScopeAttribute))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "Problem evaluating custom filter template: test template ${unknown-param} ${p2}");
  }
}
