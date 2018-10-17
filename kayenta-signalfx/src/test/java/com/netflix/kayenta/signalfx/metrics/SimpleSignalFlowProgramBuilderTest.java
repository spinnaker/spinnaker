/*
 * Copyright (c) 2018 Nike, inc.
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
 *
 */

package com.netflix.kayenta.signalfx.metrics;

import com.google.common.collect.ImmutableMap;
import com.netflix.kayenta.canary.providers.metrics.QueryPair;
import com.netflix.kayenta.signalfx.canary.SignalFxCanaryScope;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SimpleSignalFlowProgramBuilderTest {

  @Test
  public void test_that_the_program_builder_builds_the_expected_program() {

    String metricName = "request.count";
    String aggregationMethod = "mean";

    SignalFxCanaryScope scope = new SignalFxCanaryScope();
    scope.setScope("1.0.0");
    scope.setScopeKey("version");
    scope.setExtendedScopeParams(ImmutableMap.of("env", "production"));

    SimpleSignalFlowProgramBuilder builder = SimpleSignalFlowProgramBuilder
        .create(metricName, aggregationMethod);

    builder.withQueryPair(new QueryPair("app", "cms"));
    builder.withQueryPair(new QueryPair("response_code", "400"));
    builder.withQueryPair(new QueryPair("uri", "/v2/auth/iam-principal"));
    builder.withScope(scope);

    String expected = "data('request.count', filter=" +
        "filter('app', 'cms') " +
        "and filter('response_code', '400') " +
        "and filter('uri', '/v2/auth/iam-principal') " +
        "and filter('version', '1.0.0') " +
        "and filter('env', 'production'))" +
        ".mean(by=['env', 'version']).publish()";

    assertEquals(expected, builder.build());
  }

  @Test
  public void test_that_the_program_builder_builds_the_expected_program_1_qp() {

    String metricName = "request.count";
    String aggregationMethod = "mean";

    SignalFxCanaryScope scope = new SignalFxCanaryScope();
    scope.setScope("1.0.0");
    scope.setScopeKey("version");
    scope.setExtendedScopeParams(ImmutableMap.of("env", "production"));

    SimpleSignalFlowProgramBuilder builder = SimpleSignalFlowProgramBuilder
        .create(metricName, aggregationMethod);

    builder.withQueryPair(new QueryPair("app", "cms"));
    builder.withScope(scope);

    String expected = "data('request.count', filter=" +
        "filter('app', 'cms') " +
        "and filter('version', '1.0.0') " +
        "and filter('env', 'production'))" +
        ".mean(by=['env', 'version']).publish()";

    assertEquals(expected, builder.build());
  }

  @Test
  public void test_that_the_program_builder_builds_the_expected_program_with_no_query_pairs() {

    String metricName = "request.count";
    String aggregationMethod = "mean";

    SignalFxCanaryScope scope = new SignalFxCanaryScope();
    scope.setScope("1.0.0");
    scope.setScopeKey("version");
    scope.setExtendedScopeParams(ImmutableMap.of("env", "production"));

    SimpleSignalFlowProgramBuilder builder = SimpleSignalFlowProgramBuilder
        .create(metricName, aggregationMethod);

    builder.withScope(scope);

    String expected = "data('request.count', filter=" +
        "filter('version', '1.0.0') " +
        "and filter('env', 'production'))" +
        ".mean(by=['env', 'version']).publish()";

    assertEquals(expected, builder.build());
  }
}
