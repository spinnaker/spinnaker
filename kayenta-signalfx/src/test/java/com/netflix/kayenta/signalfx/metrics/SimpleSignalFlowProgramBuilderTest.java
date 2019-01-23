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
import com.netflix.kayenta.signalfx.config.SignalFxScopeConfiguration;
import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;
import com.tngtech.java.junit.dataprovider.UseDataProvider;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;

@RunWith(DataProviderRunner.class)
public class SimpleSignalFlowProgramBuilderTest {

  @Test
  public void test_that_the_program_builder_builds_the_expected_program_no_location_key() {

    String metricName = "request.count";
    String aggregationMethod = "mean";

    SignalFxCanaryScope scope = new SignalFxCanaryScope();
    SignalFxScopeConfiguration scopeConfiguration = new SignalFxScopeConfiguration();
    scope.setScope("1.0.0");
    scope.setScopeKey("version");
    scope.setExtendedScopeParams(ImmutableMap.of(
        "env", "production",
        "_scope_key", "version"));

    SimpleSignalFlowProgramBuilder builder = SimpleSignalFlowProgramBuilder
        .create(metricName, aggregationMethod, scopeConfiguration);

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

  @DataProvider
  public static Object[][] locationScopeProvider() {
    return new Object[][]{
        {
          new SignalFxCanaryScope()
            .setScopeKey("version")
            .setLocationKey("region")
            .setScope("1.0.0")
            .setLocation("us-west-2")
            .setExtendedScopeParams(ImmutableMap.of(
              "env", "production",
              "_scope_key", "version",
              "_location_key", "region")),
          SignalFxScopeConfiguration.builder().build()
        },
        {
          new SignalFxCanaryScope()
            .setScopeKey("version")
            .setScope("1.0.0")
            .setLocation("us-west-2")
            .setExtendedScopeParams(ImmutableMap.of("env", "production")),
          SignalFxScopeConfiguration.builder()
                .defaultLocationKey("region")
                .build()
        }
    };
  }
  @Test
  @UseDataProvider("locationScopeProvider")
  public void test_that_the_program_builder_builds_the_expected_program_when_location_is_provided(
      SignalFxCanaryScope scope,
      SignalFxScopeConfiguration scopeConfiguration) {

    String metricName = "request.count";
    String aggregationMethod = "mean";

    SimpleSignalFlowProgramBuilder builder = SimpleSignalFlowProgramBuilder
        .create(metricName, aggregationMethod, scopeConfiguration);

    builder.withQueryPair(new QueryPair("app", "cms"));
    builder.withQueryPair(new QueryPair("response_code", "400"));
    builder.withQueryPair(new QueryPair("uri", "/v2/auth/iam-principal"));
    builder.withScope(scope);

    String expected = "data('request.count', filter=" +
        "filter('app', 'cms') " +
        "and filter('response_code', '400') " +
        "and filter('uri', '/v2/auth/iam-principal') " +
        "and filter('version', '1.0.0') " +
        "and filter('region', 'us-west-2') " +
        "and filter('env', 'production'))" +
        ".mean(by=['env', 'region', 'version']).publish()";

    assertEquals(expected, builder.build());
  }

  @Test
  public void test_that_the_program_builder_builds_the_expected_program_with_extra_scope_qp_pairs() {

    String metricName = "request.count";
    String aggregationMethod = "mean";

    SignalFxCanaryScope scope = new SignalFxCanaryScope();
    SignalFxScopeConfiguration scopeConfiguration = new SignalFxScopeConfiguration();
    scope.setScope("1.0.0");
    scope.setScopeKey("version");
    scope.setExtendedScopeParams(ImmutableMap.of(
        "env", "production",
        "region", "us-west-2",
        "_scope_key", "version"));

    SimpleSignalFlowProgramBuilder builder = SimpleSignalFlowProgramBuilder
        .create(metricName, aggregationMethod, scopeConfiguration);

    builder.withQueryPair(new QueryPair("app", "cms"));
    builder.withQueryPair(new QueryPair("response_code", "400"));
    builder.withQueryPair(new QueryPair("uri", "/v2/auth/iam-principal"));
    builder.withScope(scope);

    String expected = "data('request.count', filter=" +
        "filter('app', 'cms') " +
        "and filter('response_code', '400') " +
        "and filter('uri', '/v2/auth/iam-principal') " +
        "and filter('version', '1.0.0') " +
        "and filter('env', 'production') " +
        "and filter('region', 'us-west-2'))" +
        ".mean(by=['env', 'region', 'version']).publish()";

    assertEquals(expected, builder.build());
  }

  @Test
  public void test_that_the_program_builder_builds_the_expected_program_1_qp() {

    String metricName = "request.count";
    String aggregationMethod = "mean";

    SignalFxCanaryScope scope = new SignalFxCanaryScope();
    SignalFxScopeConfiguration scopeConfiguration = new SignalFxScopeConfiguration();
    scope.setScope("1.0.0");
    scope.setScopeKey("version");
    scope.setExtendedScopeParams(ImmutableMap.of(
        "env", "production",
        "_scope_key", "version"));

    SimpleSignalFlowProgramBuilder builder = SimpleSignalFlowProgramBuilder
        .create(metricName, aggregationMethod, scopeConfiguration);

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
    SignalFxScopeConfiguration scopeConfiguration = new SignalFxScopeConfiguration();
    scope.setScope("1.0.0");
    scope.setScopeKey("version");
    scope.setExtendedScopeParams(ImmutableMap.of(
        "env", "production",
        "_scope_key", "version"));

    SimpleSignalFlowProgramBuilder builder = SimpleSignalFlowProgramBuilder
        .create(metricName, aggregationMethod, scopeConfiguration);

    builder.withScope(scope);

    String expected = "data('request.count', filter=" +
        "filter('version', '1.0.0') " +
        "and filter('env', 'production'))" +
        ".mean(by=['env', 'version']).publish()";

    assertEquals(expected, builder.build());
  }

  @Test
  public void test_that_when_there_are_extra_scope_query_pairs_that_the_program_builds_as_expected() {
    String metricName = "request.count";
    String aggregationMethod = "mean";

    SignalFxCanaryScope scope = new SignalFxCanaryScope();
    SignalFxScopeConfiguration scopeConfiguration = new SignalFxScopeConfiguration();
    scope.setScope("1.0.0");
    scope.setScopeKey("version");
    scope.setExtendedScopeParams(ImmutableMap.of("_scope_key", "version"));

    SimpleSignalFlowProgramBuilder builder = SimpleSignalFlowProgramBuilder
        .create(metricName, aggregationMethod, scopeConfiguration);

    builder.withScope(scope);

    String expected = "data('request.count', filter=" +
        "filter('version', '1.0.0'))" +
        ".mean(by=['version']).publish()";

    assertEquals(expected, builder.build());
  }

  @Test
  public void test_that_if_a_default_scope_key_was_defined_it_is_used_if_not_overwritten() {
    String metricName = "request.count";
    String aggregationMethod = "mean";

    SignalFxCanaryScope scope = new SignalFxCanaryScope();
    SignalFxScopeConfiguration scopeConfiguration = SignalFxScopeConfiguration.builder()
        .defaultScopeKey("server_group")
        .build();

    scope.setScope("my_microservice-control-v1");

    SimpleSignalFlowProgramBuilder builder = SimpleSignalFlowProgramBuilder
        .create(metricName, aggregationMethod, scopeConfiguration);

    builder.withScope(scope);

    String expected = "data('request.count', filter=" +
        "filter('server_group', 'my_microservice-control-v1'))" +
        ".mean(by=['server_group']).publish()";

    assertEquals(expected, builder.build());
  }
}
