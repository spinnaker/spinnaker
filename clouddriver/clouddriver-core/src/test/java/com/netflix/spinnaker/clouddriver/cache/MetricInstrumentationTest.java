/*
 * Copyright 2023 JPMorgan Chase & Co.
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
 *
 */

package com.netflix.spinnaker.clouddriver.cache;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class MetricInstrumentationTest {

  private MeterRegistry registry;

  private MetricInstrumentation metricInstrumentation;

  @BeforeEach
  void setup() {
    registry = new SimpleMeterRegistry();
    metricInstrumentation = new MetricInstrumentation(registry);
  }

  @Test
  void test_executionCompleted_hasExpectedMetricLabels() {
    // given
    String agentType = "test-account-bob/us-east-1/TestCachingAgent";
    String provider = "io.spinnaker.clouddriver.test.TestProvider";

    Agent agent =
        new CachingAgent() {
          @Override
          public Collection<AgentDataType> getProvidedDataTypes() {
            return List.of();
          }

          @Override
          public CacheResult loadData(ProviderCache providerCache) {
            return null;
          }

          @Override
          public String getAgentType() {
            return agentType;
          }

          @Override
          public String getProviderName() {
            return provider;
          }
        };

    // when
    metricInstrumentation.executionCompleted(agent, 500L);

    // then
    Timer timer =
        registry
            .find("executionTime")
            .tags(
                "className",
                MetricInstrumentation.class.getSimpleName(),
                "agent",
                String.format("TestProvider/%s", agentType),
                "success",
                "true")
            .timer();
    assertNotNull(timer);
  }
}
