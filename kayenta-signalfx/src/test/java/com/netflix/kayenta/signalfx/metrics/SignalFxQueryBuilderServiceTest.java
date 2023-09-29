/*
 * Copyright 2020 Netflix, Inc.
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

package com.netflix.kayenta.signalfx.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.canary.CanaryConfig;
import com.netflix.kayenta.canary.CanaryScope;
import com.netflix.kayenta.canary.providers.metrics.SignalFxCanaryMetricSetQueryConfig;
import com.netflix.kayenta.signalfx.canary.SignalFxCanaryScope;
import com.netflix.kayenta.signalfx.config.SignalFxScopeConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class SignalFxQueryBuilderServiceTest {

  private SignalFxQueryBuilderService signalFxQueryBuilderService;

  @BeforeEach
  public void before() {
    signalFxQueryBuilderService = new SignalFxQueryBuilderService();
  }

  @Test
  public void test_inline_template_supplied() {
    String customInlineTemplate =
        "data('request.count', filter="
            + "filter('server_group', 'my_microservice-control-v1'))"
            + ".mean(by=['server_group']).publish()";

    CanaryConfig canaryConfig = CanaryConfig.builder().build();
    SignalFxCanaryMetricSetQueryConfig metricSetQueryConfig =
        SignalFxCanaryMetricSetQueryConfig.builder()
            .customInlineTemplate(customInlineTemplate)
            .build();
    CanaryScope canaryScope = CanaryScope.builder().build();
    SignalFxScopeConfiguration scopeConfiguration = SignalFxScopeConfiguration.builder().build();
    SignalFxCanaryScope scope = new SignalFxCanaryScope();

    String expected =
        "data('request.count', filter="
            + "filter('server_group', 'my_microservice-control-v1'))"
            + ".mean(by=['server_group']).publish()";

    assertEquals(
        expected,
        signalFxQueryBuilderService.buildQuery(
            canaryConfig, metricSetQueryConfig, canaryScope, scopeConfiguration, scope));
  }

  @Test
  public void test_inline_template_not_supplied() {
    CanaryConfig canaryConfig = CanaryConfig.builder().build();
    SignalFxCanaryMetricSetQueryConfig metricSetQueryConfig =
        SignalFxCanaryMetricSetQueryConfig.builder().build();
    CanaryScope canaryScope = CanaryScope.builder().build();
    SignalFxCanaryScope scope = new SignalFxCanaryScope();
    SignalFxScopeConfiguration scopeConfiguration = SignalFxScopeConfiguration.builder().build();

    SignalFxQueryBuilderService signalFxQueryBuilderServiceSpy = spy(signalFxQueryBuilderService);
    doReturn("some query")
        .when(signalFxQueryBuilderServiceSpy)
        .getSimpleProgram(any(), any(), any(), any(), any());
    signalFxQueryBuilderServiceSpy.buildQuery(
        canaryConfig, metricSetQueryConfig, canaryScope, scopeConfiguration, scope);
    verify(signalFxQueryBuilderServiceSpy, times(1))
        .getSimpleProgram(any(), any(), any(), any(), any());
  }

  @Test
  public void test_inline_template_and_extended_scope_params_supplied() {
    String customInlineTemplate =
        "data('request.count', filter="
            + "filter('server_group', 'my_microservice-control-v1'))"
            + ".mean(by=['${some_key}']).publish()";

    SignalFxCanaryScope scope = new SignalFxCanaryScope();
    CanaryConfig canaryConfig = CanaryConfig.builder().build();
    SignalFxCanaryMetricSetQueryConfig metricSetQueryConfig =
        SignalFxCanaryMetricSetQueryConfig.builder()
            .customInlineTemplate(customInlineTemplate)
            .build();
    CanaryScope canaryScope = CanaryScope.builder().build();
    canaryScope.setExtendedScopeParams(ImmutableMap.of("some_key", "some_value"));
    SignalFxScopeConfiguration scopeConfiguration = SignalFxScopeConfiguration.builder().build();

    String expected =
        "data('request.count', filter="
            + "filter('server_group', 'my_microservice-control-v1'))"
            + ".mean(by=['some_value']).publish()";

    assertEquals(
        expected,
        signalFxQueryBuilderService.buildQuery(
            canaryConfig, metricSetQueryConfig, canaryScope, scopeConfiguration, scope));
  }
}
