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
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider;
import com.netflix.spinnaker.cats.provider.ProviderRegistry;
import com.netflix.spinnaker.clouddriver.config.PubSubSchedulerProperties;
import com.netflix.spinnaker.kork.pubsub.model.MessageChannel;
import com.netflix.spinnaker.kork.pubsub.model.MessageSubscription;
import com.netflix.spinnaker.kork.pubsub.model.PubsubMessage;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class PubSubAgentRunnerTest {
  private PubSubTestSupport fixture;
  private PubSubAgentRunner runner;
  private ProviderRegistry registry;
  private MessageSubscription subscription;
  private final AtomicReference<Consumer<PubsubMessage>> dispatcher = new AtomicReference<>();
  private final List<String> acknowledged = new CopyOnWriteArrayList<>();
  private final AtomicBoolean running = new AtomicBoolean();

  @BeforeEach
  void setUp() throws Exception {
    fixture = new PubSubTestSupport();
    registry = mock(ProviderRegistry.class);
    subscription = mock(MessageSubscription.class);
    when(subscription.isRunning()).thenAnswer(invocation -> running.get());
    doAnswer(
            invocation -> {
              running.set(true);
              return null;
            })
        .when(subscription)
        .start();
    doAnswer(
            invocation -> {
              running.set(false);
              return null;
            })
        .when(subscription)
        .stop();
    doAnswer(
            invocation -> {
              acknowledged.add(((PubsubMessage) invocation.getArgument(0)).id());
              return null;
            })
        .when(subscription)
        .ack(any());
    MessageChannel channel = mock(MessageChannel.class);
    when(channel.subscribe(anyString(), anyInt(), any(), any()))
        .thenAnswer(
            invocation -> {
              dispatcher.set(invocation.getArgument(2));
              return subscription;
            });
    runner = new PubSubAgentRunner();
    runner.stateMachine = fixture.state;
    runner.messageChannel = channel;
    runner.properties = new PubSubSchedulerProperties();
    runner.properties.setMaxConcurrentAgents(2);
    runner.meterRegistry = new SimpleMeterRegistry();
    ReflectionTestUtils.setField(runner, "providerRegistry", registry);
  }

  @AfterEach
  void tearDown() throws Exception {
    runner.destroy();
    fixture.close();
  }

  @Test
  void acknowledgesCompletionDuplicatesMalformedUnknownAndFailedExecutions() {
    AtomicInteger executions = new AtomicInteger();
    agent("success", ignored -> executions.incrementAndGet());
    agent(
        "failed",
        ignored -> {
          throw new IllegalStateException("agent failure");
        });
    runner.onApplicationEvent(null);
    dispatch("success-1", "success");
    await().untilAsserted(() -> assertThat(acknowledged).contains("success-1"));
    dispatch("duplicate", "success");
    dispatch("failure", "failed");
    dispatch("unknown", "missing");
    dispatcher.get().accept(new PubsubMessage("malformed", "", Map.of()));
    await().untilAsserted(() -> assertThat(acknowledged).hasSize(5));
    assertThat(executions).hasValue(1);
    assertThat(fixture.state.getAgent("success").getCurrentState()).isEqualTo("FINISHED");
    assertThat(fixture.state.getAgent("failed").getCurrentState()).isEqualTo("FAILED");
  }

  @Test
  void neverRunsMoreAgentsThanTheConfiguredConcurrency() throws Exception {
    CountDownLatch twoStarted = new CountDownLatch(2);
    CountDownLatch release = new CountDownLatch(1);
    AtomicInteger inFlight = new AtomicInteger();
    AtomicInteger peak = new AtomicInteger();
    for (int i = 0; i < 6; i++) {
      agent(
          "agent-" + i,
          ignored -> {
            peak.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
            twoStarted.countDown();
            try {
              assertThat(release.await(5, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              throw new IllegalStateException(e);
            } finally {
              inFlight.decrementAndGet();
            }
          });
    }
    runner.onApplicationEvent(null);
    try (var pollThread = Executors.newSingleThreadExecutor()) {
      var delivery =
          pollThread.submit(
              () -> {
                for (int i = 0; i < 6; i++) {
                  dispatch("message-" + i, "agent-" + i);
                }
              });
      try {
        assertThat(twoStarted.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(peak).hasValue(2);
        assertThat(acknowledged).isEmpty();
      } finally {
        release.countDown();
      }
      delivery.get(5, TimeUnit.SECONDS);
      await().untilAsserted(() -> assertThat(acknowledged).hasSize(6));
      assertThat(peak).hasValue(2);
    }
  }

  @Test
  void disabledNodesPauseAndResumeAndUseSharedAbandonedMessageRecovery() {
    NodeStatusProvider node = mock(NodeStatusProvider.class);
    runner.nodeStatusProvider = node;
    when(node.isNodeEnabled()).thenReturn(false);
    runner.onApplicationEvent(null);
    assertThat(running).isFalse();
    when(node.isNodeEnabled()).thenReturn(true);
    runner.maintain();
    assertThat(running).isTrue();
    verify(subscription).reclaim(Duration.ofMinutes(20), 100);
    when(node.isNodeEnabled()).thenReturn(false);
    runner.maintain();
    assertThat(running).isFalse();
  }

  private void agent(String name, AgentExecution execution) {
    Agent agent = mock(Agent.class);
    when(agent.getAgentType()).thenReturn(name);
    when(agent.getAgentExecution(registry)).thenReturn(execution);
    when(registry.getAgentForProviderName(name)).thenReturn(agent);
    fixture.state.createOrUpdateAgent(name, StateMachine.State.PENDING);
  }

  private void dispatch(String id, String agent) {
    dispatcher.get().accept(new PubsubMessage(id, "", Map.of("agentType", agent)));
  }
}
