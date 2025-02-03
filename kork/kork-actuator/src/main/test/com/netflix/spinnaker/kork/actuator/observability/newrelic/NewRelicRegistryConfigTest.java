/*
 * Copyright 2025 Netflix, Inc.
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

package com.netflix.spinnaker.kork.actuator.observability.newrelic;

import static org.junit.Assert.assertEquals;

import com.netflix.spinnaker.kork.actuator.observability.model.MetricsNewRelicConfig;
import java.time.Duration;
import org.junit.Test;

public class NewRelicRegistryConfigTest {

  @Test
  public void test_that_NewRelicRegistryConfig_proxies_PluginMetricsNewRelicConfig() {
    var step = Duration.ofSeconds(30);
    var delegate =
        MetricsNewRelicConfig.builder()
            .apiKey("foo")
            .batchSize(10)
            .enableAuditMode(true)
            .numThreads(10)
            .stepInSeconds((int) step.toSeconds())
            .uri("https://foo.com")
            .build();

    var sut = new NewRelicRegistryConfig(delegate);
    assertEquals(delegate.getApiKey(), sut.apiKey());
    assertEquals(delegate.getBatchSize(), sut.batchSize());
    assertEquals(delegate.isEnableAuditMode(), sut.enableAuditMode());
    assertEquals(delegate.getNumThreads(), sut.numThreads());
    assertEquals(step, sut.step());
    assertEquals(delegate.getUri(), sut.uri());
    assertEquals(5, delegate.getConnectDurationSeconds());
  }

  @Test(expected = NewRelicRegistryConfig.NewRelicRegistryConfigException.class)
  public void test_that_apiKey_throws_exception_if_not_set() {
    var delegate = MetricsNewRelicConfig.builder().build();
    var sut = new NewRelicRegistryConfig(delegate);
    sut.apiKey();
  }
}
