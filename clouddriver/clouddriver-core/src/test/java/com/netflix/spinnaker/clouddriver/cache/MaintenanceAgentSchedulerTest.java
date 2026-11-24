/*
 * Copyright 2026 DoorDash, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.DefaultAgentIntervalProvider;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class MaintenanceAgentSchedulerTest {
  private static final long INTERVAL = 60_000;
  private static final long ERROR_INTERVAL = 7_000;
  private static final long TIMEOUT = 45_000;

  private MaintenanceLock lock;
  private DynamicConfigService dynamicConfigService;
  private ExecutionInstrumentation instrumentation;
  private ScheduledExecutorService executor;
  private MaintenanceAgentScheduler scheduler;
  private final AgentIntervalProvider intervals =
      new DefaultAgentIntervalProvider(INTERVAL, ERROR_INTERVAL, TIMEOUT);

  @BeforeEach
  void setUp() {
    lock = mock(MaintenanceLock.class);
    dynamicConfigService = mock(DynamicConfigService.class);
    when(dynamicConfigService.getConfig(
            eq(String.class), eq(MaintenanceAgentScheduler.DISABLED_AGENTS_KEY), anyString()))
        .thenReturn("");
    instrumentation = mock(ExecutionInstrumentation.class);
    executor = Executors.newScheduledThreadPool(2);
    scheduler =
        new MaintenanceAgentScheduler(lock, intervals, dynamicConfigService, executor, 30_000);
  }

  @AfterEach
  void tearDown() {
    executor.shutdownNow();
  }

  private static class TestAgent implements MaintenanceAgent {
    final AtomicInteger runs = new AtomicInteger();
    final RuntimeException failure;

    TestAgent(RuntimeException failure) {
      this.failure = failure;
    }

    @Override
    public void run() {
      runs.incrementAndGet();
      if (failure != null) {
        throw failure;
      }
    }

    @Override
    public String getAgentType() {
      return "TestAgent";
    }

    @Override
    public String getProviderName() {
      return "test";
    }

    @Override
    public long getPollIntervalMillis() {
      return -1;
    }

    @Override
    public long getTimeoutMillis() {
      return -1;
    }

    @Override
    public long getErrorIntervalMillis() {
      return -1;
    }
  }

  @Test
  void runsAndHoldsUntilNextIntervalWhenLockAcquired() {
    TestAgent agent = new TestAgent(null);
    when(lock.tryAcquire("TestAgent", TIMEOUT)).thenReturn(true);

    long before = System.currentTimeMillis();
    scheduler.tick(agent, agent.getAgentExecution(null), instrumentation);
    long after = System.currentTimeMillis();

    assertThat(agent.runs.get()).isEqualTo(1);
    ArgumentCaptor<Long> until = ArgumentCaptor.forClass(Long.class);
    verify(lock).hold(eq("TestAgent"), until.capture());
    assertThat(until.getValue()).isBetween(before + INTERVAL, after + INTERVAL);
    verify(instrumentation).executionStarted(agent);
    verify(instrumentation).executionCompleted(eq(agent), anyLong());
    verify(instrumentation, never()).executionFailed(eq(agent), any(), anyLong());
  }

  @Test
  void skipsWhenLockNotAcquired() {
    TestAgent agent = new TestAgent(null);
    when(lock.tryAcquire(anyString(), anyLong())).thenReturn(false);

    scheduler.tick(agent, agent.getAgentExecution(null), instrumentation);

    assertThat(agent.runs.get()).isZero();
    verify(lock, never()).hold(anyString(), anyLong());
  }

  @Test
  void holdsForErrorIntervalWhenAgentFails() {
    TestAgent agent = new TestAgent(new IllegalStateException("boom"));
    when(lock.tryAcquire("TestAgent", TIMEOUT)).thenReturn(true);

    long before = System.currentTimeMillis();
    scheduler.tick(agent, agent.getAgentExecution(null), instrumentation);
    long after = System.currentTimeMillis();

    assertThat(agent.runs.get()).isEqualTo(1);
    ArgumentCaptor<Long> until = ArgumentCaptor.forClass(Long.class);
    verify(lock).hold(eq("TestAgent"), until.capture());
    assertThat(until.getValue()).isBetween(before + ERROR_INTERVAL, after + ERROR_INTERVAL);
    verify(instrumentation).executionFailed(eq(agent), any(IllegalStateException.class), anyLong());
    verify(instrumentation, never()).executionCompleted(eq(agent), anyLong());
  }

  @Test
  void skipsDisabledAgentsWithoutTouchingTheLock() {
    TestAgent agent = new TestAgent(null);
    when(dynamicConfigService.getConfig(
            eq(String.class), eq(MaintenanceAgentScheduler.DISABLED_AGENTS_KEY), anyString()))
        .thenReturn("Other, TestAgent ,Another");

    scheduler.tick(agent, agent.getAgentExecution(null), instrumentation);

    assertThat(agent.runs.get()).isZero();
    verify(lock, never()).tryAcquire(anyString(), anyLong());
  }

  @Test
  void lockFailureDoesNotEscapeTick() {
    TestAgent agent = new TestAgent(null);
    when(lock.tryAcquire(anyString(), anyLong())).thenThrow(new RuntimeException("db down"));

    scheduler.tick(agent, agent.getAgentExecution(null), instrumentation);

    assertThat(agent.runs.get()).isZero();
  }

  @Test
  void scheduledAgentKeepsTickingAfterFailures() throws Exception {
    CountDownLatch ticks = new CountDownLatch(3);
    when(lock.tryAcquire(anyString(), anyLong())).thenReturn(true);
    AgentExecution failing =
        a -> {
          ticks.countDown();
          throw new IllegalStateException("boom");
        };
    // lock poll interval shorter than the agent interval drives the tick period
    MaintenanceAgentScheduler fast =
        new MaintenanceAgentScheduler(lock, intervals, dynamicConfigService, executor, 10);

    fast.schedule(new TestAgent(null), failing, instrumentation);

    assertThat(ticks.await(5, TimeUnit.SECONDS)).isTrue();
  }

  @Test
  void unscheduleStopsTicking() throws Exception {
    AtomicInteger executions = new AtomicInteger();
    when(lock.tryAcquire(anyString(), anyLong())).thenReturn(true);
    MaintenanceAgentScheduler fast =
        new MaintenanceAgentScheduler(lock, intervals, dynamicConfigService, executor, 10);
    TestAgent agent = new TestAgent(null);

    fast.schedule(agent, a -> executions.incrementAndGet(), instrumentation);
    long deadline = System.currentTimeMillis() + 5000;
    while (executions.get() < 2 && System.currentTimeMillis() < deadline) {
      Thread.sleep(5);
    }
    assertThat(executions.get()).isGreaterThanOrEqualTo(2);

    fast.unschedule(agent);
    Thread.sleep(50); // let any in-flight tick finish
    int afterUnschedule = executions.get();
    Thread.sleep(200);

    assertThat(executions.get()).isEqualTo(afterUnschedule);
    verify(lock, times(afterUnschedule)).hold(eq("TestAgent"), anyLong());
  }

  private static <T> T any() {
    return org.mockito.ArgumentMatchers.any();
  }

  private static <T> T any(Class<T> type) {
    return org.mockito.ArgumentMatchers.any(type);
  }
}
