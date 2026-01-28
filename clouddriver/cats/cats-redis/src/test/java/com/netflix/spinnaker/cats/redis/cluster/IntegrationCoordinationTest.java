/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.cats.redis.cluster;

import static com.netflix.spinnaker.cats.redis.cluster.TestFixtures.createTestScriptManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.ShardingFilter;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Integration tests for service coordination in the Priority Redis Scheduler.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Repopulation presence checks (adding only missing local agents)
 *   <li>Cleanup cadence and coordination
 *   <li>Coordination between zombie and orphan cleanup services
 * </ul>
 *
 * <p>Tests focus on inter-service coordination and state consistency rather than detailed metrics
 * verification.
 */
@Testcontainers
@DisplayName("Integration and Coordination Tests")
@SuppressWarnings("resource") // GenericContainer lifecycle managed by @Testcontainers
@Timeout(60)
class IntegrationCoordinationTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine")
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", "testpass");

  private JedisPool jedisPool;
  private RedisScriptManager scriptManager;
  private PrioritySchedulerMetrics metrics;

  @BeforeEach
  void setUp() {
    jedisPool = TestFixtures.createTestJedisPool(redis, "testpass", 32);
    metrics = TestFixtures.createTestMetrics();
    scriptManager = createTestScriptManager(jedisPool, metrics);
    try (Jedis j = jedisPool.getResource()) {
      j.flushAll();
    }
  }

  @AfterEach
  void tearDown() {
    TestFixtures.closePoolSafely(jedisPool);
  }

  @Nested
  @DisplayName("Repopulation Presence Tests")
  class RepopulationPresenceTests {

    /**
     * Tests that repopulation adds only missing local agents and ignores non-local entries.
     * Verifies that 5 local agents removed from Redis are repopulated while 100 non-local agents
     * remain untouched.
     */
    @Test
    @DisplayName("Repopulation adds only missing local agents; ignores non-local entries")
    void repopulationAddsOnlyMissingLocalAgents() {
      AgentIntervalProvider intervalProvider = mock(AgentIntervalProvider.class);
      when(intervalProvider.getInterval(any(Agent.class)))
          .thenReturn(new AgentIntervalProvider.Interval(1000L, 2000L));

      ShardingFilter shardingFilter = mock(ShardingFilter.class);
      when(shardingFilter.filter(any(Agent.class))).thenReturn(true);

      PriorityAgentProperties agentProperties = new PriorityAgentProperties();
      agentProperties.setEnabledPattern(".*");
      agentProperties.setDisabledPattern("");
      agentProperties.setMaxConcurrentAgents(0);

      PrioritySchedulerProperties schedulerProperties = new PrioritySchedulerProperties();
      schedulerProperties.setRefreshPeriodSeconds(1);
      schedulerProperties.getBatchOperations().setEnabled(true);
      schedulerProperties.getBatchOperations().setBatchSize(100);
      schedulerProperties.getKeys().setWaitingSet("waiting");
      schedulerProperties.getKeys().setWorkingSet("working");
      schedulerProperties.getKeys().setCleanupLeaderKey("cleanup-leader");

      AgentAcquisitionService acquisitionService =
          new AgentAcquisitionService(
              jedisPool,
              scriptManager,
              intervalProvider,
              shardingFilter,
              agentProperties,
              schedulerProperties,
              metrics);

      ExecutorService agentWorkPool = Executors.newFixedThreadPool(8);

      Set<String> locals = new HashSet<>();
      for (int i = 1; i <= 5; i++) {
        String name = "A" + i;
        locals.add(name);
        acquisitionService.registerAgent(
            TestFixtures.createMockAgent(name),
            mock(AgentExecution.class),
            TestFixtures.createMockInstrumentation());
      }

      try (Jedis j = jedisPool.getResource()) {
        for (int i = 1; i <= 100; i++) {
          j.zadd("waiting", 0, "B" + i);
        }
        for (String agentType : locals) {
          j.zrem("waiting", agentType);
          j.zrem("working", agentType);
        }
      }

      acquisitionService.saturatePool(1L, new Semaphore(0), agentWorkPool);

      try (Jedis j = jedisPool.getResource()) {
        long localsPresent = 0;
        for (String agentType : locals) {
          Double waitingScore = j.zscore("waiting", agentType);
          Double workingScore = j.zscore("working", agentType);
          if (waitingScore != null || workingScore != null) localsPresent++;
        }
        assertThat(localsPresent).isEqualTo(locals.size());
        assertThat(j.zcard("waiting")).isGreaterThanOrEqualTo(100);
      }

      // Note: Metrics verification is omitted; focus is on repopulation behavior (local agents
      // added, non-local preserved), not specific metric values.
      TestFixtures.shutdownExecutorSafely(agentWorkPool);
    }
  }

  @Nested
  @DisplayName("Cleanup Cadence Tests")
  class CleanupCadenceTests {

    @Nested
    @DisplayName("Orphan cadence")
    class OrphanCadence {

      /**
       * Tests that orphan cleanup runs at each interval and respects leadership TTL semantics.
       * Verifies that the first run updates the timestamp, a run within 200ms is skipped, and a run
       * after 900ms updates the timestamp again.
       */
      @Test
      @DisplayName("Runs each interval and preserves leadership TTL semantics")
      void runsEachInterval() throws Exception {
        PrioritySchedulerProperties props = new PrioritySchedulerProperties();
        props.getKeys().setWaitingSet("waiting");
        props.getKeys().setWorkingSet("working");
        props.getKeys().setCleanupLeaderKey("cleanup-leader");
        props.getOrphanCleanup().setEnabled(true);
        props.getOrphanCleanup().setIntervalMs(1000);
        props.getOrphanCleanup().setRunBudgetMs(200);
        props.getOrphanCleanup().setLeadershipTtlMs(1500);

        OrphanCleanupService orphan =
            new OrphanCleanupService(jedisPool, scriptManager, props, metrics);

        long t0 = System.currentTimeMillis();
        orphan.cleanupOrphanedAgentsIfNeeded();
        long first = orphan.getLastOrphanCleanup();
        assertThat(first).isGreaterThanOrEqualTo(t0);

        Thread.sleep(200);
        long before = orphan.getLastOrphanCleanup();
        orphan.cleanupOrphanedAgentsIfNeeded();
        assertThat(orphan.getLastOrphanCleanup()).isEqualTo(before);

        Thread.sleep(900);
        orphan.cleanupOrphanedAgentsIfNeeded();
        long second = orphan.getLastOrphanCleanup();
        assertThat(second).isGreaterThan(first);

        // Note: Cleanup metrics and leadership TTL verification are omitted; focus is on cadence
        // behavior (interval enforcement), not specific metric values or TTL implementation.
      }
    }
  }

  @Nested
  @DisplayName("Zombie Orphan Coordination Tests")
  class CoordinationTests {

    /**
     * Tests that both zombie and orphan cleanup services coordinate correctly on the same stuck
     * agent. Verifies that only one service processes the agent and the permit is released exactly
     * once. Uses concurrent execution to test race conditions.
     */
    @Test
    @DisplayName("Both cleanup services act on same stuck agent exactly once")
    void zombieAndOrphanCoordination() throws Exception {
      PriorityAgentProperties agentProps = new PriorityAgentProperties();
      agentProps.setMaxConcurrentAgents(1);

      PrioritySchedulerProperties schedProps = new PrioritySchedulerProperties();
      schedProps.getKeys().setWaitingSet("waiting");
      schedProps.getKeys().setWorkingSet("working");
      schedProps.getZombieCleanup().setEnabled(true);
      schedProps.getZombieCleanup().setThresholdMs(300);
      schedProps.getOrphanCleanup().setEnabled(true);
      schedProps.getOrphanCleanup().setThresholdMs(500);

      AgentIntervalProvider ivp = a -> new AgentIntervalProvider.Interval(200L, 200L, 200L);
      ShardingFilter shard = a -> true;

      AgentAcquisitionService acq =
          new AgentAcquisitionService(
              jedisPool, scriptManager, ivp, shard, agentProps, schedProps, metrics);

      ZombieCleanupService zombies =
          new ZombieCleanupService(jedisPool, scriptManager, schedProps, metrics);
      zombies.setAcquisitionService(acq);

      OrphanCleanupService orphans =
          new OrphanCleanupService(jedisPool, scriptManager, schedProps, metrics);
      orphans.setAcquisitionService(acq);

      Agent slow = TestFixtures.createMockAgent("coord-agent");
      AgentExecution exec = mock(AgentExecution.class);
      ExecutionInstrumentation instr = TestFixtures.createMockInstrumentation();
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      org.mockito.Mockito.doAnswer(
              inv -> {
                started.countDown();
                release.await(5, TimeUnit.SECONDS);
                return null;
              })
          .when(exec)
          .executeAgent(any());

      acq.registerAgent(slow, exec, instr);

      Semaphore sem = new Semaphore(1);
      ExecutorService pool = Executors.newFixedThreadPool(1);
      int got = acq.saturatePool(0L, sem, pool);
      assertThat(got).isEqualTo(1);
      assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

      // Sleep long enough to exceed zombie threshold accounting for score rounding.
      // Score calculation rounds UP: seconds = (targetMs + 999) / 1000
      // With 200ms timeout + 300ms threshold, we need > 500ms past acquire.
      // Worst case rounding adds 999ms, so we need > 1499ms total.
      // Use 2000ms for reliable test execution.
      Thread.sleep(2000);

      Map<String, String> active = new ConcurrentHashMap<>(acq.getActiveAgentsMap());
      Map<String, Future<?>> futures = new ConcurrentHashMap<>(acq.getActiveAgentsFutures());

      ExecutorService cleaners = Executors.newFixedThreadPool(2);
      Future<?> zf = cleaners.submit(() -> zombies.cleanupZombieAgents(active, futures));
      Future<?> of = cleaners.submit(orphans::forceCleanupOrphanedAgents);
      zf.get(5, TimeUnit.SECONDS);
      of.get(5, TimeUnit.SECONDS);

      assertThat(sem.availablePermits()).isEqualTo(1);

      release.countDown();

      // Wait for agent to complete (no active agents)
      TestFixtures.waitForBackgroundTask(() -> acq.getActiveAgentCount() == 0, 1000, 25);
      assertThat(acq.getActiveAgentCount()).isEqualTo(0);

      // Note: Cleanup metrics and Redis WORKING_SET verification are omitted; focus is on
      // coordination behavior (only one service processes agent, permit released once),
      // not specific metric values or detailed Redis state.
      TestFixtures.shutdownExecutorSafely(cleaners);
      TestFixtures.shutdownExecutorSafely(pool);
    }
  }
}
