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

import static org.junit.jupiter.api.Assertions.*;

import com.netflix.spectator.api.Counter;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Id;
import com.netflix.spectator.api.LongTaskTimer;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentDataType;
import com.netflix.spinnaker.cats.agent.CacheResult;
import com.netflix.spinnaker.cats.agent.CachingAgent;
import com.netflix.spinnaker.cats.provider.ProviderCache;
import java.util.Collection;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class NewMetricInstrumentationTest {

  private Registry registry;

  private NewMetricInstrumentation metricInstrumentation;

  @BeforeEach
  void setup() {
    registry = new DefaultRegistry();
    metricInstrumentation = new NewMetricInstrumentation(registry);
  }

  @Test
  void testExecutionCompletedComputesLongTaskTimerAndCompletedExecutions()
      throws InterruptedException {
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

    Id expectedCompletedExecutionsId =
        registry
            .createId("completedExecutions")
            .withTag("className", NewMetricInstrumentation.class.getSimpleName())
            .withTag("agent", String.format("TestProvider/%s", agentType));

    Id expectedStartedExecutionsId =
        registry
            .createId("startedExecutions")
            .withTag("className", NewMetricInstrumentation.class.getSimpleName())
            .withTag("agent", String.format("TestProvider/%s", agentType));

    Id expectedExecutionTimeId =
        registry
            .createId("executionTime")
            .withTag("className", NewMetricInstrumentation.class.getSimpleName())
            .withTag("agent", String.format("TestProvider/%s", agentType));

    Id activeTasksId = expectedExecutionTimeId.withTag("statistic", "activeTasks");
    Id durationId = expectedExecutionTimeId.withTag("statistic", "duration");
    // when
    LongTaskTimer timer = registry.longTaskTimer(expectedExecutionTimeId);
    metricInstrumentation.executionStarted(agent);

    // then
    Counter startedCounter = registry.counter(expectedStartedExecutionsId);
    Counter completedCounter =
        registry.counter(expectedCompletedExecutionsId.withTag("success", "true"));

    assertEquals(0.0, completedCounter.measure().iterator().next().value());

    assertEquals(1.0, startedCounter.measure().iterator().next().value());

    assertTrue(
        StreamSupport.stream(timer.measure().spliterator(), false)
            .anyMatch(it -> it.id().equals(activeTasksId)));
    assertTrue(
        StreamSupport.stream(timer.measure().spliterator(), false)
            .anyMatch(it -> it.id().equals(durationId)));

    // when
    metricInstrumentation.executionCompleted(agent, 500L);

    // then
    assertEquals(1.0, completedCounter.measure().iterator().next().value());
  }
}
