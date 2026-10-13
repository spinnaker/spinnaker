/*
 * Copyright 2026 spinnaker.io
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

package com.netflix.spinnaker.cats.pubsub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.provider.Provider;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.config.PubSubSchedulerProperties;
import com.netflix.spinnaker.kork.pubsub.model.MessageChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PubSubAgentSchedulerTest {
  @Test
  void competingSchedulersOnlyPublishTheWinningPendingTransition() throws Exception {
    try (PubSubTestSupport fixture = new PubSubTestSupport()) {
      Agent agent = agent("agent");
      MessageChannel channel = mock(MessageChannel.class);
      PubSubAgentScheduler first = scheduler(fixture, channel, List.of(agent));
      PubSubAgentScheduler second = scheduler(fixture, channel, List.of(agent));
      first.schedule(agent, null, null);
      first.run();
      second.run();
      verify(channel).publish("", Map.of("agentType", "agent"));
      assertThat(fixture.state.getAgent("agent").getCurrentState()).isEqualTo("PENDING");
    }
  }

  @Test
  void stalePendingAndTimedOutRunningAgentsAreRequeuedAndDeletedAgentsArePurged() throws Exception {
    try (PubSubTestSupport fixture = new PubSubTestSupport()) {
      MessageChannel channel = mock(MessageChannel.class);
      PubSubAgentScheduler scheduler =
          scheduler(fixture, channel, List.of(agent("pending"), agent("running")));
      fixture.state.createOrUpdateAgent("pending", StateMachine.State.PENDING);
      fixture.state.createOrUpdateAgent("running", StateMachine.State.RUNNING);
      fixture.state.createOrUpdateAgent("deleted", StateMachine.State.DELETED);
      fixture.sql.execute("UPDATE pubsub_agent_state SET last_transition_time = 0");
      scheduler.run();
      verify(channel).publish("", Map.of("agentType", "pending"));
      verify(channel).publish("", Map.of("agentType", "running"));
      assertThat(fixture.state.getAgent("deleted")).isNull();
      assertThat(fixture.state.getAgent("running").getCurrentState()).isEqualTo("PENDING");
    }
  }

  @Test
  void failedAgentsKeepTheirErrorBackoffAndUnschedulingMarksThemDeleted() throws Exception {
    try (PubSubTestSupport fixture = new PubSubTestSupport()) {
      Agent agent = agent("failed");
      MessageChannel channel = mock(MessageChannel.class);
      PubSubAgentScheduler scheduler = scheduler(fixture, channel, List.of(agent));
      fixture.state.createOrUpdateAgent("failed", StateMachine.State.FAILED);
      scheduler.run();
      verify(channel, never()).publish(anyString(), anyMap());
      fixture.sql.execute("UPDATE pubsub_agent_state SET last_transition_time = 0");
      scheduler.run();
      verify(channel).publish("", Map.of("agentType", "failed"));
      scheduler.unschedule(agent);
      assertThat(fixture.state.getAgent("failed").getCurrentState()).isEqualTo("DELETED");
    }
  }

  private Agent agent(String name) {
    Agent agent = mock(Agent.class);
    when(agent.getAgentType()).thenReturn(name);
    return agent;
  }

  private PubSubAgentScheduler scheduler(
      PubSubTestSupport fixture, MessageChannel channel, List<Agent> agents) {
    ProviderRegistry registry = mock(ProviderRegistry.class);
    Provider provider = mock(Provider.class);
    when(provider.getAgents()).thenReturn(agents);
    when(registry.getProviders()).thenReturn(List.of(provider));
    PubSubSchedulerProperties properties = new PubSubSchedulerProperties();
    properties.setMinutesBeforeDeletingMarkedForDeletion(0);
    properties.setMinutesBeforeReQueueOfAgents(1);
    PubSubAgentScheduler scheduler = new PubSubAgentScheduler();
    ReflectionTestUtils.setField(scheduler, "stateMachine", fixture.state);
    ReflectionTestUtils.setField(scheduler, "providerRegistry", registry);
    ReflectionTestUtils.setField(
        scheduler,
        "intervalProvider",
        (AgentIntervalProvider) agent -> new AgentIntervalProvider.Interval(1000, 60_000, 1000));
    scheduler.messageChannel = channel;
    scheduler.properties = properties;
    scheduler.meterRegistry = new SimpleMeterRegistry();
    when(channel.statistics()).thenReturn(new MessageChannel.Statistics(0, 0, 0));
    return scheduler;
  }
}
