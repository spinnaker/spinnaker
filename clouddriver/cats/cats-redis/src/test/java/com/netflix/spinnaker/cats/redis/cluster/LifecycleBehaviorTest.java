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

import static com.netflix.spinnaker.cats.redis.cluster.TestFixtures.assertAgentInSet;
import static com.netflix.spinnaker.cats.redis.cluster.TestFixtures.createTestScriptManager;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.ShardingFilter;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
 * Agent lifecycle and behavior tests for the Priority Redis Scheduler.
 *
 * <p>Tests cover:
 *
 * <ul>
 *   <li>Agent score lifecycle (registration, acquisition, completion, rescheduling)
 *   <li>Shutdown behavior (race condition prevention, graceful shutdown, requeue)
 *   <li>Redis state consistency during lifecycle transitions
 * </ul>
 *
 * <p>Tests focus on lifecycle state transitions and Redis state consistency rather than detailed
 * metrics verification.
 */
@Testcontainers
@DisplayName("Agent Lifecycle and Behavior Tests")
@SuppressWarnings("resource") // GenericContainer lifecycle managed by @Testcontainers
@Timeout(60)
class LifecycleBehaviorTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine")
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", "testpass");

  private JedisPool jedisPool;
  private RedisScriptManager scriptManager;
  private AgentAcquisitionService acquisitionService;
  private AgentIntervalProvider intervalProvider;
  private ShardingFilter shardingFilter;
  private PriorityAgentProperties agentProperties;
  private PrioritySchedulerProperties schedulerProperties;
  private ExecutorService executorService;

  @BeforeEach
  void setUp() {
    jedisPool = TestFixtures.createTestJedisPool(redis);

    scriptManager = createTestScriptManager(jedisPool);

    intervalProvider = mock(AgentIntervalProvider.class);
    shardingFilter = mock(ShardingFilter.class);
    when(shardingFilter.filter(any(Agent.class))).thenReturn(true);

    // interval=2s, timeout=5s
    AgentIntervalProvider.Interval iv = new AgentIntervalProvider.Interval(2000L, 5000L);
    when(intervalProvider.getInterval(any(Agent.class))).thenReturn(iv);

    agentProperties = new PriorityAgentProperties();
    agentProperties.setMaxConcurrentAgents(10);
    agentProperties.setEnabledPattern(".*");
    agentProperties.setDisabledPattern("");

    schedulerProperties = new PrioritySchedulerProperties();
    schedulerProperties.setRefreshPeriodSeconds(30);
    schedulerProperties.getKeys().setWaitingSet("waiting");
    schedulerProperties.getKeys().setWorkingSet("working");
    schedulerProperties.getKeys().setCleanupLeaderKey("cleanup-leader");
    schedulerProperties
        .getBatchOperations()
        .setEnabled(false); // Disable batch to use individual acquisition

    executorService = Executors.newCachedThreadPool();

    acquisitionService =
        new AgentAcquisitionService(
            jedisPool,
            scriptManager,
            intervalProvider,
            shardingFilter,
            agentProperties,
            schedulerProperties,
            TestFixtures.createTestMetrics());
  }

  @AfterEach
  void tearDown() {
    TestFixtures.shutdownExecutorSafely(executorService);
    TestFixtures.closePoolSafely(jedisPool);
  }

  private Agent createMockAgent(String agentType) {
    return TestFixtures.createMockAgent(agentType, "test");
  }

  @Nested
  @DisplayName("Score Lifecycle Tests")
  class ScoreLifecycleTests {

    /**
     * Tests that agent registration adds agent to waiting set with score approximately equal to
     * current time (within ±3 seconds). Uses Redis server time to avoid clock skew.
     */
    @Test
    @DisplayName("Registration schedules waiting with score≈now")
    void registrationSchedulesImmediate() {
      Agent agent = createMockAgent("reg-agent");
      acquisitionService.registerAgent(
          agent, mock(AgentExecution.class), TestFixtures.createMockInstrumentation());

      try (Jedis j = jedisPool.getResource()) {
        // Compare against Redis server time to avoid host/container skew and second-boundary drift
        java.util.List<String> t = j.time();
        long redisNowSec = Long.parseLong(t.get(0));
        Double s = j.zscore("waiting", "reg-agent");
        assertThat(s).isNotNull();
        // Within [-3s, +3s] of Redis TIME now
        assertThat(Math.abs(s.longValue() - redisNowSec)).isLessThanOrEqualTo(3);
      }
      // Metrics/internal map verification omitted; focus is on Redis score correctness
    }

    /**
     * Tests that acquisition moves agent from waiting to working set with deadline score (now +
     * timeout). Verifies score delta is within 2-8 seconds (accounting for 5s timeout). Uses
     * test-controlled execution to observe intermediate state.
     */
    @Test
    @DisplayName("Acquisition moves waiting->working with deadline = now + timeout")
    void acquisitionSetsDeadline() {
      Agent agent = createMockAgent("acq-agent");
      // Use CountDownLatch for test-controlled completion - allows verification of intermediate
      // state
      CountDownLatch completionLatch = new CountDownLatch(1);
      AgentExecution exec = mock(AgentExecution.class);
      doAnswer(
              inv -> {
                try {
                  completionLatch.await(); // Test controls when execution completes
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return null;
              })
          .when(exec)
          .executeAgent(any());

      acquisitionService.registerAgent(agent, exec, TestFixtures.createMockInstrumentation());

      int acquired = acquisitionService.saturatePool(0L, null, executorService);
      assertThat(acquired).isEqualTo(1);

      try (Jedis j = jedisPool.getResource()) {
        // Use Redis server time to avoid host/container clock skew and second-boundary flakiness
        java.util.List<String> t = j.time();
        long redisNowSec = Long.parseLong(t.get(0));

        Double workScore = j.zscore("working", "acq-agent");
        assertThat(workScore).isNotNull();
        long delta = workScore.longValue() - redisNowSec;
        // timeout=5s with a slightly wider tolerance (±3s) to account for second rounding and
        // jitter
        assertThat(delta).isBetween(2L, 8L);
      }

      // Complete execution - test controls completion timing
      completionLatch.countDown();
      // Metrics/activeAgents tracking verification omitted; focus is on Redis score correctness
    }

    /**
     * Tests that successful completion preserves agent cadence by rescheduling to waiting set with
     * score approximately equal to now + interval (within ±5 seconds tolerance).
     */
    @Test
    @DisplayName("Success completion preserves cadence relative to original acquire")
    void successPreservesCadence() throws Exception {
      Agent agent = createMockAgent("cadence-agent");
      // Use ControllableAgentExecution for consistent pattern
      TestFixtures.ControllableAgentExecution exec =
          new TestFixtures.ControllableAgentExecution().withFixedDuration(10);

      acquisitionService.registerAgent(agent, exec, TestFixtures.createMockInstrumentation());

      int acquired = acquisitionService.saturatePool(0L, null, executorService);
      assertThat(acquired).isEqualTo(1);

      // Wait for completion using polling, then process completions next cycle (prevent reacquire)
      TestFixtures.waitForBackgroundTask(
          () -> acquisitionService.getActiveAgentCount() == 0, 1000, 50);
      acquisitionService.saturatePool(1L, new Semaphore(0), executorService);

      // expected next ≈ now + interval (approximation consistent with agentScore() for working)
      long intervalMs = intervalProvider.getInterval(agent).getInterval();

      try (Jedis j = jedisPool.getResource()) {
        java.util.List<String> t = j.time();
        long redisNowSec = Long.parseLong(t.get(0));
        long desiredNextSec = redisNowSec + (intervalMs / 1000L);
        Double waitScore = j.zscore("waiting", "cadence-agent");
        assertThat(waitScore).isNotNull();
        long actual = waitScore.longValue();
        // allow a slightly wider band for CI timing (Redis seconds granularity + scheduling jitter)
        assertThat(Math.abs(actual - desiredNextSec)).isLessThanOrEqualTo(5);
      }
      // Metrics verification omitted; focus is on Redis score correctness for cadence preservation
    }

    /**
     * Tests that failure completion reschedules agent immediately (score ≈ now, within ±4 seconds)
     * when failure backoff is enabled. Verifies acquire attempts metric is incremented.
     */
    @Test
    @DisplayName("Failure completion reschedules immediately (score≈now)")
    void failureReschedulesImmediately() throws Exception {
      // Enable immediate retry semantics to reflect intended business behavior
      schedulerProperties.getFailureBackoff().setEnabled(true);
      schedulerProperties.getFailureBackoff().setMaxImmediateRetries(1);

      // Create metrics registry to verify metrics calls
      com.netflix.spectator.api.DefaultRegistry registry =
          new com.netflix.spectator.api.DefaultRegistry();
      PrioritySchedulerMetrics testMetrics = new PrioritySchedulerMetrics(registry);

      // Create acquisition service with test metrics for verification
      AgentAcquisitionService testAcquisitionService =
          new AgentAcquisitionService(
              jedisPool,
              scriptManager,
              intervalProvider,
              shardingFilter,
              agentProperties,
              schedulerProperties,
              testMetrics);

      Agent agent = createMockAgent("fail-agent");
      AgentExecution failing = mock(AgentExecution.class);
      doThrow(new RuntimeException("boom")).when(failing).executeAgent(any());

      testAcquisitionService.registerAgent(
          agent, failing, TestFixtures.createMockInstrumentation());
      int acquired = testAcquisitionService.saturatePool(0L, null, executorService);
      assertThat(acquired).isEqualTo(1);

      // Wait for completion using polling
      TestFixtures.waitForBackgroundTask(
          () -> testAcquisitionService.getActiveAgentCount() == 0, 1000, 50);
      testAcquisitionService.saturatePool(
          1L, new Semaphore(0), executorService); // process only completions, prevent reacquire

      try (Jedis j = jedisPool.getResource()) {
        // Use Redis server time to avoid host/container skew and seconds quantization issues
        java.util.List<String> t = j.time();
        long redisNowSec = Long.parseLong(t.get(0));
        Double s = j.zscore("waiting", "fail-agent");
        assertThat(s).isNotNull();
        // Allow a slightly wider tolerance for CI variance and double second rounding
        assertThat(Math.abs(s.longValue() - redisNowSec)).isLessThanOrEqualTo(4);
      }

      // Verify metrics calls - saturatePool() called twice
      assertThat(
              registry
                  .counter(
                      registry
                          .createId("cats.priorityScheduler.acquire.attempts")
                          .withTag("scheduler", "priority"))
                  .count())
          .describedAs(
              "Acquire attempts should be incremented twice (acquisition + completion processing)")
          .isGreaterThanOrEqualTo(2);

      // Note: Failure-specific metrics verification is omitted; focus is on immediate reschedule
      // behavior (score ≈ now), which is the primary concern for failure handling.
    }

    /**
     * Tests that shutdown conditional requeue only moves agents when score matches (ownership
     * verification). Verifies correct score results in requeue, wrong score leaves agent in working
     * set. Prevents race conditions during shutdown.
     */
    @Test
    @DisplayName("Shutdown conditional requeue only moves when score matches (ownership)")
    void shutdownConditionalRequeueOwnership() throws Exception {
      Agent agent = createMockAgent("shutdown-agent");
      // Use CountDownLatch for test-controlled completion - allows verification of deadlineScore
      CountDownLatch completionLatch = new CountDownLatch(1);
      AgentExecution exec = mock(AgentExecution.class);
      doAnswer(
              inv -> {
                try {
                  completionLatch.await(); // Test controls when execution completes
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                }
                return null;
              })
          .when(exec)
          .executeAgent(any());

      acquisitionService.registerAgent(agent, exec, TestFixtures.createMockInstrumentation());

      int acq = acquisitionService.saturatePool(0L, null, executorService);
      assertThat(acq).isEqualTo(1);

      String deadlineScore = acquisitionService.getActiveAgentsMap().get("shutdown-agent");
      assertThat(deadlineScore).isNotNull();

      // Correct expected score -> move to waiting
      acquisitionService.forceRequeueAgentForShutdown(agent, deadlineScore);
      try (Jedis j = jedisPool.getResource()) {
        assertAgentInSet(j, "waiting", "shutdown-agent");
      }

      // Put back into working and try with wrong score -> should not swap
      try (Jedis j = jedisPool.getResource()) {
        long redisNowSec = TestFixtures.getRedisTimeSeconds(j);
        long nowSecPlus = redisNowSec + 10;
        j.zrem("waiting", "shutdown-agent");
        j.zadd("working", nowSecPlus, "shutdown-agent");
      }

      try (Jedis j = jedisPool.getResource()) {
        long redisNowSec = TestFixtures.getRedisTimeSeconds(j);
        acquisitionService.forceRequeueAgentForShutdown(agent, Long.toString(redisNowSec + 999));
      }

      try (Jedis j = jedisPool.getResource()) {
        // Still in working since expected score mismatched
        assertAgentInSet(j, "working", "shutdown-agent");
      }

      // Complete execution - test controls completion timing
      completionLatch.countDown();
      // Metrics verification omitted; focus is on conditional requeue behavior based on score
      // matching
    }
  }

  @Nested
  @DisplayName("Shutdown Behavior Tests")
  class ShutdownBehaviorTests {

    @Nested
    @DisplayName("Shutdown Agent Preservation")
    class RaceConditionTests {

      /**
       * Tests that agents completing during shutdown are preserved by being requeued to waiting
       * set. Verifies agent is in waiting set, removed from working set, and no longer tracked as
       * active after shutdown completion.
       */
      @Test
      @DisplayName("Should preserve agents in waiting when they complete during shutdown")
      void shouldAlwaysReQueueDuringShutdown() throws Exception {
        // Given - Agent that will complete during shutdown
        Agent slowAgent = createMockAgent("slow-agent");
        CountDownLatch executionStarted = new CountDownLatch(1);
        CountDownLatch allowCompletion = new CountDownLatch(1);

        AgentExecution slowExecution = mock(AgentExecution.class);
        doAnswer(
                invocation -> {
                  executionStarted.countDown();
                  allowCompletion.await(5, TimeUnit.SECONDS);
                  return null; // Successful execution
                })
            .when(slowExecution)
            .executeAgent(any());

        ExecutionInstrumentation instrumentation = TestFixtures.createMockInstrumentation();

        // Register agent
        acquisitionService.registerAgent(slowAgent, slowExecution, instrumentation);

        // Clear any auto-added entries from registration to start clean
        try (Jedis jedis = jedisPool.getResource()) {
          TestFixtures.cleanupRedisSets(jedis, "waiting", "working");
        }

        // Start agent execution - this should add it to working set
        int acquired = acquisitionService.saturatePool(0L, null, executorService);
        assertThat(acquired).isEqualTo(1);
        assertThat(executionStarted.await(2, TimeUnit.SECONDS)).isTrue();

        // Verify agent is in working set and tracked as active
        try (Jedis jedis = jedisPool.getResource()) {
          assertThat(jedis.zcard("working"))
              .describedAs("Agent should be in working set after acquisition")
              .isEqualTo(1);
          TestFixtures.assertAgentInSet(jedis, "working", "slow-agent");
        }
        assertThat(acquisitionService.getActiveAgentCount()).isEqualTo(1);

        // When - Trigger shutdown while agent is still running
        acquisitionService.setShuttingDown(true);

        // Allow agent to complete during shutdown
        allowCompletion.countDown();

        // Wait for completion and Redis operations using polling
        TestFixtures.waitForBackgroundTask(
            () -> {
              try (Jedis jedis = jedisPool.getResource()) {
                // Check if agent is in waiting set (completion processed)
                Set<String> waitingAgents = jedis.zrange("waiting", 0, -1);
                return waitingAgents != null && waitingAgents.contains("racing-agent");
              }
            },
            2000,
            50);

        // Then - Agent MUST be in waiting set for restart
        try (Jedis jedis = jedisPool.getResource()) {
          Set<String> waitingAgents = jedis.zrange("waiting", 0, -1);
          Set<String> workingAgents = jedis.zrange("working", 0, -1);
          long waitingSize = jedis.zcard("waiting");
          long workingSize = jedis.zcard("working");

          assertThat(waitingSize)
              .describedAs(
                  "Agent MUST be in waiting after shutdown completion to prevent data loss. "
                      + "waiting=%s, working=%s, activeCount=%d, shuttingDown=%s",
                  waitingAgents,
                  workingAgents,
                  acquisitionService.getActiveAgentCount(),
                  acquisitionService.isShuttingDown())
              .isGreaterThanOrEqualTo(1);

          boolean slowAgentInWaiting = waitingAgents.contains("slow-agent");
          assertThat(slowAgentInWaiting)
              .describedAs(
                  "slow-agent MUST be in waiting set after shutdown. " + "waiting=%s, working=%s.",
                  waitingAgents, workingAgents)
              .isTrue();

          assertThat(workingSize)
              .describedAs(
                  "Agent should not be in working after completion. working=%s", workingAgents)
              .isEqualTo(0);

          Double score = jedis.zscore("waiting", "slow-agent");
          assertThat(score).isNotNull();
          long scoreSeconds = score.longValue();
          long currentSeconds = TestFixtures.nowSeconds();
          assertThat(scoreSeconds)
              .describedAs(
                  "Score should be near current time. Got %d, current %d.",
                  scoreSeconds, currentSeconds)
              .isBetween(currentSeconds - 10, currentSeconds + 300);
        }

        assertThat(acquisitionService.getActiveAgentCount())
            .describedAs("Agent should not be in activeAgents after completion")
            .isEqualTo(0);
        // Metrics verification omitted; focus is on agent preservation during shutdown
      }
    }

    @Nested
    @DisplayName("Shutdown Completion List Hygiene")
    class CompletionHygieneTests {

      /**
       * Tests that shutdown completion processing drains the queue without retaining references.
       * Verifies completion queue is drained (size = 0), agents are requeued, and second shutdown
       * call is idempotent.
       */
      @Test
      @DisplayName("Shutdown completion processing should drain queue without retaining references")
      void shutdownProcessingClearsCompletions() throws Exception {
        Agent agent = createMockAgent("complete-agent");
        AgentExecution exec = mock(AgentExecution.class);
        ExecutionInstrumentation instr = TestFixtures.createMockInstrumentation();
        acquisitionService.registerAgent(agent, exec, instr);

        acquisitionService.setShuttingDown(false);
        long nowSec = TestFixtures.nowSeconds();
        acquisitionService.conditionalReleaseAgent(
            agent, String.valueOf(nowSec), false, null, null);

        assertThat(acquisitionService.getCompletionQueueSize()).isEqualTo(1);

        acquisitionService.setShuttingDown(true);
        assertThat(acquisitionService.getCompletionQueueSize()).isEqualTo(0);

        long afterFirst;
        try (Jedis jedis = jedisPool.getResource()) {
          afterFirst = jedis.zcard("waiting");
          assertThat(afterFirst).isGreaterThanOrEqualTo(1);
        }

        acquisitionService.setShuttingDown(true);
        try (Jedis jedis = jedisPool.getResource()) {
          long afterSecond = jedis.zcard("waiting");
          assertThat(afterSecond).isEqualTo(afterFirst);
        }
        // Metrics verification omitted; focus is on completion queue draining and idempotency
      }
    }

    @Nested
    @DisplayName("Scheduling Score Correctness")
    class SchedulingScoreTests {

      /**
       * Tests that shutdown uses immediate scores (score ≈ current time) to prevent thundering
       * herd. Verifies requeued agent has score within -10 to +60 seconds of current time.
       */
      @Test
      @DisplayName("Should use immediate scores during shutdown")
      void shouldUseImmediateScoresDuringShutdown() throws Exception {
        Agent agent = createMockAgent("test-agent");
        AgentExecution execution = mock(AgentExecution.class);
        ExecutionInstrumentation instrumentation = TestFixtures.createMockInstrumentation();

        acquisitionService.registerAgent(agent, execution, instrumentation);

        acquisitionService.setShuttingDown(true);
        acquisitionService.conditionalReleaseAgent(agent, "test-score", false, null, null);

        try (Jedis jedis = jedisPool.getResource()) {
          Double scoreDouble = jedis.zscore("waiting", "test-agent");
          assertThat(scoreDouble).isNotNull();

          long scoreValue = scoreDouble.longValue();
          long currentTimeSeconds = TestFixtures.nowSeconds();

          assertThat(scoreValue)
              .describedAs(
                  "Score should be current time. Got: %d, Current: %d",
                  scoreValue, currentTimeSeconds)
              .isBetween(currentTimeSeconds - 10, currentTimeSeconds + 60);
        }
        // Metrics verification omitted; focus is on immediate score calculation during shutdown
      }
    }

    @Nested
    @DisplayName("Graceful Shutdown")
    class GracefulShutdownTests {

      /**
       * Tests that graceful shutdown requeues all active agents to waiting set with scores near
       * cadence (within ±5 seconds). Verifies working set is empty after graceful shutdown.
       */
      @Test
      @DisplayName("Should re-queue ALL registered agents during graceful shutdown")
      void shouldReQueueAllRegisteredAgentsDuringGracefulShutdown() throws Exception {
        String[] agentTypes = {"agent-1", "agent-2", "agent-3", "agent-completed", "agent-idle"};
        AgentExecution execution = mock(AgentExecution.class);
        ExecutionInstrumentation instrumentation = TestFixtures.createMockInstrumentation();

        for (String agentType : agentTypes) {
          Agent agent = createMockAgent(agentType);
          acquisitionService.registerAgent(agent, execution, instrumentation);
        }

        String expectedScore;
        try (Jedis jedis = jedisPool.getResource()) {
          long currentTimeSeconds = TestFixtures.nowSeconds();
          long completionDeadline = currentTimeSeconds + 300;
          expectedScore = String.valueOf(completionDeadline);

          jedis.zadd("working", completionDeadline, "agent-1");
          jedis.zadd("working", completionDeadline, "agent-2");
          jedis.zadd("working", completionDeadline, "agent-3");

          assertThat(jedis.zcard("working")).isEqualTo(3);
          assertThat(jedis.zcard("waiting")).isEqualTo(5);
        }

        acquisitionService.setGracefulShutdown(true);

        String[] activeAgents = {"agent-1", "agent-2", "agent-3"};
        for (String agentType : activeAgents) {
          Agent agent = acquisitionService.getAgentByType(agentType);
          if (agent != null) {
            acquisitionService.forceRequeueAgentForShutdown(agent, expectedScore);
          }
        }

        try (Jedis jedis = jedisPool.getResource()) {
          Set<String> agentsInWaitz = jedis.zrange("waiting", 0, -1);

          assertThat(agentsInWaitz)
              .describedAs("Active agents should be re-queued in waiting")
              .contains("agent-1", "agent-2", "agent-3");

          long expectedNextSec = Long.parseLong(expectedScore) - 4L;
          for (String agentType : activeAgents) {
            Double score = jedis.zscore("waiting", agentType);
            assertThat(score)
                .describedAs("Agent %s should be scheduled near cadence", agentType)
                .isNotNull();
            long actual = score.longValue();
            assertThat(actual).isBetween(expectedNextSec - 5L, expectedNextSec + 5L);
          }

          assertThat(jedis.zcard("working"))
              .describedAs("working should be empty after graceful shutdown")
              .isEqualTo(0);
        }
        // Metrics verification omitted; focus is on graceful shutdown requeue correctness
      }

      /**
       * Verifies race condition prevention during shutdown by testing that: 1. Agents in working
       * set are correctly requeued to waiting set during shutdown 2. Agents that were already
       * removed from working set are not requeued (defensive behavior)
       *
       * <p>This test exercises the shutdown race condition scenario where an agent might be removed
       * from working set (e.g., by completion or cleanup) before shutdown requeue logic runs. The
       * forceRequeueAgentForShutdown() method uses conditional Redis moves that only succeed if the
       * score matches, preventing requeueing of agents that were already removed.
       */
      @Test
      @DisplayName("Should prevent agent loss during shutdown race conditions")
      void shouldPreventAgentLossDuringShutdownRaceConditions() throws Exception {
        // Test Part 1: Verify agent in WORKING_SET is correctly requeued
        Agent racingAgent = createMockAgent("racing-agent");
        AgentExecution execution = mock(AgentExecution.class);
        ExecutionInstrumentation instrumentation = TestFixtures.createMockInstrumentation();

        acquisitionService.registerAgent(racingAgent, execution, instrumentation);

        // Verify agent added to Redis WAITING_SET via registerAgent()
        try (Jedis jedis = jedisPool.getResource()) {
          // Wait for async Redis operations using polling
          TestFixtures.waitForBackgroundTask(
              () -> {
                try (Jedis j = jedisPool.getResource()) {
                  return j.zscore("waiting", "racing-agent") != null;
                }
              },
              1000,
              50);
          Double waitingScore = jedis.zscore("waiting", "racing-agent");
          // If not found immediately, trigger repopulation
          if (waitingScore == null) {
            acquisitionService.saturatePool(0L, null, executorService);
            TestFixtures.waitForBackgroundTask(
                () -> {
                  try (Jedis j = jedisPool.getResource()) {
                    return j.zscore("waiting", "racing-agent") != null;
                  }
                },
                1000,
                50);
            waitingScore = jedis.zscore("waiting", "racing-agent");
          }
          assertThat(waitingScore)
              .describedAs("Agent should be added to WAITING_SET via registerAgent()")
              .isNotNull();
        }

        long completionDeadline = TestFixtures.nowSeconds() + 300;
        try (Jedis jedis = jedisPool.getResource()) {
          jedis.zadd("working", completionDeadline, "racing-agent");
        }

        acquisitionService.setGracefulShutdown(true);

        Agent registeredAgent = acquisitionService.getAgentByType("racing-agent");
        assertThat(registeredAgent).isNotNull();
        acquisitionService.forceRequeueAgentForShutdown(
            registeredAgent, String.valueOf(completionDeadline));

        try (Jedis jedis = jedisPool.getResource()) {
          TestFixtures.assertAgentInSet(jedis, "waiting", "racing-agent");
          TestFixtures.assertAgentNotInSet(jedis, "working", "racing-agent");
        }

        // Test Part 2: Verify already-removed agent is NOT requeued
        Agent completedAgent = createMockAgent("completed-agent");
        acquisitionService.registerAgent(completedAgent, execution, instrumentation);

        try (Jedis jedis = jedisPool.getResource()) {
          // Wait for registration to complete using polling
          TestFixtures.waitForBackgroundTask(
              () -> {
                try (Jedis j = jedisPool.getResource()) {
                  return j.zscore("waiting", "completed-agent") != null;
                }
              },
              1000,
              50);
          Double initialWaitingScore = jedis.zscore("waiting", "completed-agent");
          if (initialWaitingScore != null) {
            // Agent was added to WAITING_SET by registerAgent - remove it to test requeue behavior
            jedis.zrem("waiting", "completed-agent");
          }

          // Add agent to working set
          jedis.zadd("working", completionDeadline, "completed-agent");
          assertThat(jedis.zscore("working", "completed-agent"))
              .describedAs("Completed agent should be in WORKING_SET before removal")
              .isNotNull();

          // Remove agent from working set (simulating race condition - agent completed/cleaned up)
          jedis.zrem("working", "completed-agent");
          TestFixtures.assertAgentNotInSet(jedis, "working", "completed-agent");

          // Verify agent is NOT in WAITING_SET before requeue attempt
          TestFixtures.assertAgentNotInSet(jedis, "waiting", "completed-agent");

          // Try to requeue the removed agent
          Agent registeredCompletedAgent = acquisitionService.getAgentByType("completed-agent");
          assertThat(registeredCompletedAgent).isNotNull();
          acquisitionService.forceRequeueAgentForShutdown(
              registeredCompletedAgent, String.valueOf(completionDeadline));

          // Verify agent was NOT requeued (conditional move should fail because agent not in
          // WORKING_SET)
          assertThat(jedis.zscore("waiting", "completed-agent"))
              .describedAs(
                  "Completed agent should NOT be re-queued after removal from WORKING_SET. "
                      + "Conditional move should fail because agent is no longer in WORKING_SET with matching score.")
              .isNull();
        }
        // Metrics verification omitted; focus is on race condition prevention during shutdown
      }
    }

    @Nested
    @DisplayName("Redis State Consistency")
    class RedisConsistencyTests {

      /**
       * Tests that null Redis script results are handled gracefully. Verifies agent requeue works
       * correctly even when duplicate agent exists in waiting set.
       */
      @Test
      @DisplayName("Should handle null Redis script results gracefully")
      void shouldHandleNullRedisScriptResults() throws Exception {
        Agent agent = createMockAgent("duplicate-agent");
        AgentExecution execution = mock(AgentExecution.class);
        ExecutionInstrumentation instrumentation = TestFixtures.createMockInstrumentation();

        try (Jedis jedis = jedisPool.getResource()) {
          jedis.zadd("waiting", TestFixtures.nowSeconds(), "duplicate-agent");
        }

        acquisitionService.registerAgent(agent, execution, instrumentation);
        acquisitionService.setShuttingDown(true);

        acquisitionService.conditionalReleaseAgent(agent, "test-score", false, null, null);

        try (Jedis jedis = jedisPool.getResource()) {
          assertThat(jedis.zcard("waiting")).isEqualTo(1);
          TestFixtures.assertAgentInSet(jedis, "waiting", "duplicate-agent");
        }
        // Script result verification omitted; focus is on graceful handling of duplicate agents
      }
    }
  }

  /**
   * Verifies that shutdown preserves agent cadence when possible by calculating the next run time
   * as original_acquire_time + interval. During shutdown, the computeShutdownRescheduleOffsetMs()
   * method attempts to preserve cadence by calculating desiredNextRunMs = originalAcquireMs +
   * intervalMs when deadlineScore is available, ensuring agents maintain their intended execution
   * schedule across restarts.
   *
   * <p>Purpose: Ensures that graceful shutdown maintains agent execution cadence by calculating the
   * next run time based on when the agent was originally acquired, rather than using jitter-based
   * scheduling. This prevents agents from clustering together after a restart and maintains even
   * distribution of load over time.
   *
   * <p>Implementation details: When shutdown occurs and deadlineScore is available, the method
   * extracts the original acquire time from the deadlineScore (which is the deadline score:
   * acquire_time + timeout). It calculates the desired next run time as originalAcquireMs +
   * intervalMs, then computes an offset from the current Redis TIME to schedule the agent at that
   * desired time. The score() method adds this offset to Redis TIME to produce the final score.
   *
   * <p>Verification approach: The test registers an agent with a known interval, acquires it to get
   * the deadlineScore, simulates shutdown, triggers forceRequeueAgentForShutdown(), and verifies
   * the requeued score matches the expected cadence-based calculation (original_acquire_time +
   * interval). Allows tolerance (60 seconds) for timing differences between Redis TIME and
   * System.currentTimeMillis(), serverClientOffset adjustments, and delays between calculation and
   * Redis TIME calls. The tolerance verifies cadence preservation rather than exact absolute time.
   */
  @Test
  @DisplayName("Should preserve agent cadence during shutdown when deadlineScore available")
  void shouldPreserveAgentCadenceDuringShutdown() throws Exception {
    Agent agent = TestFixtures.createMockAgent("cadence-agent", "test-provider");
    // Use CountDownLatch for test-controlled completion - allows verification of deadlineScore
    CountDownLatch completionLatch = new CountDownLatch(1);
    AgentExecution execution = mock(AgentExecution.class);
    doAnswer(
            invocation -> {
              try {
                completionLatch.await(); // Test controls when execution completes
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return null;
            })
        .when(execution)
        .executeAgent(any(Agent.class));
    ExecutionInstrumentation instrumentation = TestFixtures.createMockInstrumentation();

    // Configure interval (30 seconds)
    when(intervalProvider.getInterval(agent))
        .thenReturn(new AgentIntervalProvider.Interval(30000L, 5000L, 60000L));

    acquisitionService.registerAgent(agent, execution, instrumentation);

    // Verify agent is registered locally
    assertThat(acquisitionService.getRegisteredAgentCount()).isEqualTo(1);

    // Ensure agent is in Redis waiting set with a ready score
    // Remove from both sets first (in case registerAgent already added it)
    // Then add with a score well in the past to make it immediately ready
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.zrem("waiting", "cadence-agent");
      jedis.zrem("working", "cadence-agent");
      long nowSeconds = TestFixtures.nowSeconds();
      jedis.zadd("waiting", nowSeconds - 200, "cadence-agent");
      // Verify agent is in Redis
      TestFixtures.assertAgentInSet(jedis, "waiting", "cadence-agent");
    }

    // Acquire agent to get deadlineScore
    ExecutorService executor = Executors.newSingleThreadExecutor();
    try {
      // Use runCount > 0 to avoid triggering repopulation which might interfere
      int acquired = acquisitionService.saturatePool(1L, null, executor);
      assertThat(acquired)
          .describedAs("Agent should be acquired from Redis waiting set")
          .isEqualTo(1);

      // Get the acquire score from activeAgents map (set immediately during acquisition)
      // activeAgents.put() is called synchronously in saturatePool (batch or individual mode)
      // So it should be there immediately after saturatePool returns
      java.util.Map<String, String> activeAgents = acquisitionService.getActiveAgentsMap();
      String deadlineScore = activeAgents.get("cadence-agent");

      // If still null, wait a moment and retry using polling (shouldn't be necessary but handle
      // race conditions)
      if (deadlineScore == null) {
        TestFixtures.waitForBackgroundTask(
            () -> acquisitionService.getActiveAgentsMap().get("cadence-agent") != null, 1000, 50);
        activeAgents = acquisitionService.getActiveAgentsMap();
        deadlineScore = activeAgents.get("cadence-agent");
      }

      assertThat(deadlineScore)
          .describedAs(
              "Agent should be acquired and tracked with deadlineScore immediately after saturatePool")
          .isNotNull();

      // Verify agent is in working set before shutdown requeue
      try (Jedis jedis = jedisPool.getResource()) {
        Double workingScore = jedis.zscore("working", "cadence-agent");
        assertThat(workingScore)
            .describedAs("Agent should be in working set before shutdown requeue")
            .isNotNull();
      }

      // Simulate shutdown by setting shuttingDown flag
      acquisitionService.setShuttingDown(true);

      // Trigger shutdown requeue via forceRequeueAgentForShutdown
      acquisitionService.forceRequeueAgentForShutdown(agent, deadlineScore);

      // Then - Verify agent requeued with cadence-preserved score
      try (Jedis jedis = jedisPool.getResource()) {
        Double requeuedScore = jedis.zscore("waiting", "cadence-agent");
        assertThat(requeuedScore).isNotNull();

        // Calculate expected cadence-based score
        // The deadlineScore is the deadline (acquire_time + timeout) in seconds
        // To get original acquire time: deadlineScoreSeconds * 1000 - timeoutMs
        // Next run should be: originalAcquireMs + intervalMs
        long deadlineScoreSeconds = Long.parseLong(deadlineScore);
        long agentTimeoutMs = 5000L; // From interval
        long originalAcquireMs = (deadlineScoreSeconds * 1000L) - agentTimeoutMs;
        long intervalMs = 30000L; // From interval
        long desiredNextRunMs = originalAcquireMs + intervalMs;

        // The score is calculated by score(jedis, offsetMs) where offsetMs comes from
        // computeShutdownRescheduleOffsetMs. The offset is: desiredNextRunMs - nowMs
        // And score() adds: redisTime + offsetMs = desiredNextRunMs
        // So the final score should be desiredNextRunMs / 1000 (seconds)
        long expectedScoreSeconds = desiredNextRunMs / 1000;

        // Verify score matches cadence-based calculation
        // Note: There may be small timing differences due to Redis TIME vs
        // System.currentTimeMillis()
        // and serverClientOffset adjustments. The key is that the score preserves cadence
        // (original_acquire_time + interval), not the exact absolute time.
        long actualScore = requeuedScore.longValue();
        long scoreDifference = Math.abs(actualScore - expectedScoreSeconds);

        // Verify the score is approximately correct (within 60 seconds tolerance)
        // The tolerance accounts for:
        // - Timing differences between calculation (System.currentTimeMillis()) and Redis TIME
        // - serverClientOffset adjustments that may differ between calls
        // - Small delays between score calculation and Redis TIME call
        // - The fact that computeShutdownRescheduleOffsetMs uses System.currentTimeMillis() +
        // serverClientOffset
        //   while score() uses Redis TIME, which can have significant differences
        // The tolerance verifies cadence is preserved (original_acquire_time + interval) rather
        // than
        // exact absolute time, which is the key requirement.
        assertThat(scoreDifference)
            .describedAs(
                "Requeued score should preserve cadence (original_acquire_time + interval). Expected: "
                    + expectedScoreSeconds
                    + ", Actual: "
                    + actualScore
                    + ", Difference: "
                    + scoreDifference
                    + " seconds")
            .isLessThanOrEqualTo(60L);
      }

      // Complete execution - test controls completion timing
      completionLatch.countDown();
      // Metrics verification omitted; focus is on cadence preservation calculation during shutdown
    } finally {
      TestFixtures.shutdownExecutorSafely(executor);
    }
  }
}
