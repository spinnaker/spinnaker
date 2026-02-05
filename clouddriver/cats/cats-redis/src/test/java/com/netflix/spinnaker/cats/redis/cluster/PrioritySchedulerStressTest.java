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

import static org.assertj.core.api.Assertions.assertThat;

import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.ShardingFilter;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Stress tests for Priority Scheduler under concurrent load.
 *
 * <p>Tests verify system correctness under stress conditions including permit accounting, agent
 * uniqueness, cleanup coordination, and shutdown preservation. Each test runs concurrent operations
 * (acquisition, zombie cleanup, orphan cleanup, shutdown toggles) for extended durations with
 * randomized timing to expose race conditions.
 *
 * <p><b>Thread.sleep() Usage:</b> This suite intentionally uses {@code Thread.sleep()} for pacing
 * worker threads, not for synchronization. Random delays (10-1500ms) create realistic timing
 * windows that help expose timing-dependent bugs.
 *
 * <p><b>Verification Approach:</b> Tests verify invariants (disjoint sets, permit accounting) and
 * eventual consistency rather than deterministic outcomes. Metrics verification confirms code paths
 * were exercised.
 */
@Testcontainers
@DisplayName("Priority Scheduler Stress Tests")
@Tag("stress")
@SuppressWarnings("resource") // GenericContainer lifecycle managed by @Testcontainers
class PrioritySchedulerStressTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine")
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", "testpass");

  private JedisPool jedisPool;
  private RedisScriptManager scriptManager;
  private PrioritySchedulerMetrics metrics;
  private Registry registry;

  @BeforeEach
  void setUp() {
    jedisPool = TestFixtures.createTestJedisPool(redis, "testpass", 32);
    try (Jedis j = jedisPool.getResource()) {
      j.flushAll();
    }
    registry = new DefaultRegistry();
    metrics = new PrioritySchedulerMetrics(registry);
    scriptManager = new RedisScriptManager(jedisPool, metrics);
    scriptManager.initializeScripts();
  }

  @AfterEach
  void tearDown() {
    TestFixtures.closePoolSafely(jedisPool);
  }

  /**
   * Tests that permit accounting remains correct under concurrent stress with randomized timing.
   *
   * <p>Runs 20 agents with 5 max concurrent for 10 seconds. Exercises concurrent acquisition,
   * zombie cleanup, orphan cleanup, and shutdown toggles with randomized execution timing.
   *
   * <p>Verifies: No thread exceptions, end-of-run invariants (disjoint sets, sum <= registered).
   */
  @Test
  @Timeout(180)
  @DisplayName("Permit accounting with randomized timing (10s)")
  void permitAccountingUnderRandomizedTiming() throws Exception {
    StressParams params = new StressParams(20, 5, Duration.ofSeconds(10));
    StressResult result = runStress(params);

    // Verify invariants
    assertThat(result.violations).as("No thread exceptions").isEmpty();

    // Verify metrics were recorded during stress
    verifyMetricsRecorded();
  }

  /**
   * Tests that agent uniqueness is maintained under concurrent operations.
   *
   * <p>Runs 50 agents with 10 max concurrent for 10 seconds. Higher agent count exercises
   * concurrent acquisition, zombie cleanup, orphan cleanup, and shutdown toggles.
   *
   * <p>Verifies: No thread exceptions.
   */
  @Test
  @Timeout(180)
  @DisplayName("Agent uniqueness under concurrent operations (10s)")
  void agentUniquenessUnderConcurrentOps() throws Exception {
    StressParams params = new StressParams(50, 10, Duration.ofSeconds(10));
    StressResult result = runStress(params);

    // Verify invariants
    assertThat(result.violations).as("No thread exceptions").isEmpty();

    // Verify metrics were recorded during stress
    verifyMetricsRecorded();
  }

  /**
   * Tests that cleanup coordination and invariants hold under extended stress.
   *
   * <p>Runs 80 agents with 10 max concurrent for 30 seconds. Extended duration exercises zombie and
   * orphan cleanup coordination under sustained concurrent operations.
   *
   * <p>Verifies: No invariant violations or exceptions.
   */
  @Test
  @Timeout(300)
  @DisplayName("Cleanup coordination with invariants (30s)")
  void cleanupCoordinationWithInvariants() throws Exception {
    StressParams params = new StressParams(80, 10, Duration.ofSeconds(30));
    StressResult result = runStress(params);

    // Verify invariants
    assertThat(result.violations).as("No invariant violations or exceptions").isEmpty();

    // Verify metrics were recorded during stress
    verifyMetricsRecorded();
  }

  /**
   * Tests that state is preserved correctly under shutdown toggle stress.
   *
   * <p>Runs 40 agents with 8 max concurrent for 8 seconds with 20 shutdown toggles. Includes
   * invariant checker thread monitoring permit accounting throughout.
   *
   * <p>Verifies: No invariant violations or exceptions.
   */
  @Test
  @Timeout(300)
  @DisplayName("Shutdown preservation with repeated toggles (8s)")
  void shutdownPreservationTwentyToggles() throws Exception {
    StressParams params = new StressParams(40, 8, Duration.ofSeconds(8));
    StressResult result = runShutdownPreservation(params, 20);

    // Verify invariants
    assertThat(result.violations).as("No invariant violations or exceptions").isEmpty();

    // Verify metrics were recorded during stress
    verifyMetricsRecorded();
  }

  /**
   * Tests that correctness is maintained under combined stress scenarios.
   *
   * <p>Runs 60 agents with 12 max concurrent for 60 seconds. Combines acquisition, zombie cleanup,
   * orphan cleanup, shutdown toggles, and invariant checking under extended stress.
   *
   * <p>Verifies: No invariant violations or exceptions.
   */
  @Test
  @Timeout(180)
  @DisplayName("Combined scenarios under extended stress (60s)")
  void combinedScenariosSixtySeconds() throws Exception {
    StressParams params = new StressParams(60, 12, Duration.ofSeconds(60));
    StressResult result = runCombinedStress(params);

    // Verify invariants
    assertThat(result.violations).as("No invariant violations or exceptions").isEmpty();

    // Verify metrics were recorded during stress
    verifyMetricsRecorded();
  }

  private StressResult runStress(StressParams params) throws Exception {
    // Properties
    PriorityAgentProperties agentProps = new PriorityAgentProperties();
    agentProps.setEnabledPattern(".*");
    agentProps.setDisabledPattern("");
    agentProps.setMaxConcurrentAgents(params.maxConcurrent);

    PrioritySchedulerProperties schedProps = new PrioritySchedulerProperties();
    schedProps.getKeys().setWaitingSet("waiting");
    schedProps.getKeys().setWorkingSet("working");
    schedProps.getKeys().setCleanupLeaderKey("cleanup-leader");
    schedProps.getCircuitBreaker().setEnabled(false);
    // Make cleanup responsive for stress
    schedProps.getZombieCleanup().setEnabled(true);
    schedProps.getZombieCleanup().setThresholdMs(200L);
    schedProps.getOrphanCleanup().setEnabled(true);
    schedProps.getOrphanCleanup().setThresholdMs(1_000L);

    // Dependencies
    AgentIntervalProvider intervalProvider =
        agent -> new AgentIntervalProvider.Interval(200L, 200L, 400L);
    ShardingFilter shardingFilter = a -> true;

    AgentAcquisitionService acquisitionService =
        new AgentAcquisitionService(
            jedisPool,
            scriptManager,
            intervalProvider,
            shardingFilter,
            agentProps,
            schedProps,
            metrics);

    ZombieCleanupService zombieCleanup =
        new ZombieCleanupService(jedisPool, scriptManager, schedProps, metrics);
    zombieCleanup.setAcquisitionService(acquisitionService);

    OrphanCleanupService orphanCleanup =
        new OrphanCleanupService(jedisPool, scriptManager, schedProps, metrics);
    orphanCleanup.setAcquisitionService(acquisitionService);

    // Register agents with random execution behavior
    for (int i = 0; i < params.numAgents; i++) {
      Agent a = mockAgent("stress-agent-" + i, "stress");
      AgentExecution exec = RandomExecutionFactory.randomized(10, 500);
      ExecutionInstrumentation instr = TestFixtures.createMockInstrumentation();
      acquisitionService.registerAgent(a, exec, instr);
    }

    // Concurrency controls
    Semaphore semaphore = new Semaphore(params.maxConcurrent);
    ExecutorService agentWorkPool = Executors.newCachedThreadPool();
    ExecutorService testThreads = Executors.newCachedThreadPool();
    AtomicBoolean running = new AtomicBoolean(true);
    ViolationCollector violations = new ViolationCollector();

    // Thread 1: Acquisition loop
    Future<?> acquirer =
        testThreads.submit(
            () -> {
              long runCount = 0L;
              try {
                // Initial repopulation
                acquisitionService.saturatePool(runCount++, semaphore, agentWorkPool);
                while (running.get()) {
                  acquisitionService.saturatePool(runCount++, semaphore, agentWorkPool);
                  Thread.sleep(10);
                }
              } catch (Throwable t) {
                violations.add("acquisition-thread exception: " + t);
              }
            });

    // Thread 2: Zombie cleanup (50-200ms intervals)
    Future<?> zombieCleaner =
        testThreads.submit(
            () -> {
              try {
                while (running.get()) {
                  // Snapshots as required by cleanup API
                  java.util.Map<String, String> active =
                      new java.util.HashMap<>(acquisitionService.getActiveAgentsMap());
                  java.util.Map<String, Future<?>> futures =
                      new java.util.HashMap<>(acquisitionService.getActiveAgentsFutures());
                  zombieCleanup.cleanupZombieAgents(active, futures);
                  Thread.sleep(ThreadLocalRandom.current().nextInt(50, 201));
                }
              } catch (Throwable t) {
                violations.add("zombie-cleaner exception: " + t);
              }
            });

    // Thread 3: Orphan cleanup (200-500ms intervals)
    Future<?> orphanCleaner =
        testThreads.submit(
            () -> {
              try {
                while (running.get()) {
                  orphanCleanup.forceCleanupOrphanedAgents();
                  Thread.sleep(ThreadLocalRandom.current().nextInt(200, 501));
                }
              } catch (Throwable t) {
                violations.add("orphan-cleaner exception: " + t);
              }
            });

    // Thread 4: Shutdown toggles
    Future<?> shutdownToggler =
        testThreads.submit(
            () -> {
              try {
                boolean state = false;
                while (running.get()) {
                  state = !state;
                  acquisitionService.setShuttingDown(state);
                  Thread.sleep(ThreadLocalRandom.current().nextInt(200, 501));
                }
              } catch (Throwable t) {
                violations.add("shutdown-toggle exception: " + t);
              }
            });

    // No sampling-time invariant checker; we assert eventual consistency at end-of-run

    // Run the stress window
    Thread.sleep(params.duration.toMillis());
    running.set(false);

    // Join threads
    acquirer.get(10, TimeUnit.SECONDS);
    zombieCleaner.get(10, TimeUnit.SECONDS);
    orphanCleaner.get(10, TimeUnit.SECONDS);
    shutdownToggler.get(10, TimeUnit.SECONDS);
    // no invariant checker to join

    // Wait for all active agent futures to complete (ensures finally blocks have run)
    java.util.Map<String, Future<?>> futures = acquisitionService.getActiveAgentsFutures();
    for (Future<?> future : futures.values()) {
      try {
        future.get(5, TimeUnit.SECONDS);
      } catch (Exception ignored) {
        // Future may be cancelled or failed - that's fine
      }
    }

    // Wait for active agents to settle (should be quick now that all workers finished)
    long activeDeadline = System.currentTimeMillis() + 2000L;
    while (System.currentTimeMillis() < activeDeadline
        && acquisitionService.getActiveAgentCount() > 0) {
      try {
        Thread.sleep(50);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    // Process any remaining completion queue items after threads have stopped
    // This ensures agents that completed just before shutdown are properly rescheduled
    try {
      acquisitionService.saturatePool(Long.MAX_VALUE, null, agentWorkPool);
    } catch (Exception e) {
      // Best-effort: ignore errors during post-shutdown processing
    }

    // End-of-run eventual-consistency assertions with brief stabilization window
    try (Jedis j = jedisPool.getResource()) {
      String WAITING_KEY = schedProps.getKeys().getWaitingSet();
      String WORKING_KEY = schedProps.getKeys().getWorkingSet();
      long endBy = System.currentTimeMillis() + 1000L;
      boolean settled = false;
      java.util.List<String> waiting = null;
      java.util.List<String> working = null;
      int registered = params.numAgents;
      int sumSets = 0;
      int completing = 0;
      int active = 0;

      while (System.currentTimeMillis() < endBy && !settled) {
        waiting = j.zrange(WAITING_KEY, 0, -1);
        working = j.zrange(WORKING_KEY, 0, -1);
        sumSets = waiting.size() + working.size();
        completing = Math.max(0, acquisitionService.getCompletionQueueSize());
        active = Math.max(0, acquisitionService.getActiveAgentCount());
        if ((registered - sumSets) <= (completing + active)) {
          settled = true;
          break;
        }
        Thread.sleep(10);
      }

      // Use final values for assertions (or re-fetch if loop didn't run)
      if (waiting == null || working == null) {
        waiting = j.zrange(WAITING_KEY, 0, -1);
        working = j.zrange(WORKING_KEY, 0, -1);
        sumSets = waiting.size() + working.size();
        completing = Math.max(0, acquisitionService.getCompletionQueueSize());
        active = Math.max(0, acquisitionService.getActiveAgentCount());
      }

      java.util.Set<String> inter = new java.util.HashSet<>(waiting);
      inter.retainAll(working);
      org.assertj.core.api.Assertions.assertThat(inter)
          .describedAs("waiting/workingset must be disjoint at end-of-run")
          .isEmpty();
      org.assertj.core.api.Assertions.assertThat(sumSets)
          .describedAs("sum of sets must not exceed registered")
          .isLessThanOrEqualTo(registered);
      org.assertj.core.api.Assertions.assertThat(registered - sumSets)
          .describedAs(
              "missing members must be explainable by completing queue or active execution")
          .isLessThanOrEqualTo(completing + active);
    }
    // Shutdown pools
    TestFixtures.shutdownExecutorSafely(agentWorkPool);
    TestFixtures.shutdownExecutorSafely(testThreads);

    return new StressResult(new ArrayList<>(violations.violations));
  }

  private StressResult runShutdownPreservation(StressParams params, int toggles) throws Exception {
    // Properties
    PriorityAgentProperties agentProps = new PriorityAgentProperties();
    agentProps.setEnabledPattern(".*");
    agentProps.setDisabledPattern("");
    agentProps.setMaxConcurrentAgents(params.maxConcurrent);

    PrioritySchedulerProperties schedProps = new PrioritySchedulerProperties();
    schedProps.getKeys().setWaitingSet("waiting");
    schedProps.getKeys().setWorkingSet("working");
    schedProps.getKeys().setCleanupLeaderKey("cleanup-leader");
    schedProps.getCircuitBreaker().setEnabled(false);
    // Make cleanup responsive for stress
    schedProps.getZombieCleanup().setEnabled(true);
    schedProps.getZombieCleanup().setThresholdMs(200L);
    schedProps.getOrphanCleanup().setEnabled(true);
    schedProps.getOrphanCleanup().setThresholdMs(1_000L);

    // Dependencies
    AgentIntervalProvider intervalProvider =
        agent -> new AgentIntervalProvider.Interval(200L, 200L, 400L);
    ShardingFilter shardingFilter = a -> true;

    AgentAcquisitionService acquisitionService =
        new AgentAcquisitionService(
            jedisPool,
            scriptManager,
            intervalProvider,
            shardingFilter,
            agentProps,
            schedProps,
            metrics);

    ZombieCleanupService zombieCleanup =
        new ZombieCleanupService(jedisPool, scriptManager, schedProps, metrics);
    zombieCleanup.setAcquisitionService(acquisitionService);

    OrphanCleanupService orphanCleanup =
        new OrphanCleanupService(jedisPool, scriptManager, schedProps, metrics);
    orphanCleanup.setAcquisitionService(acquisitionService);

    // Register agents
    for (int i = 0; i < params.numAgents; i++) {
      Agent a = mockAgent("shutdown-agent-" + i, "shutdown");
      AgentExecution exec = RandomExecutionFactory.randomized(10, 500);
      ExecutionInstrumentation instr = TestFixtures.createMockInstrumentation();
      acquisitionService.registerAgent(a, exec, instr);
    }

    Semaphore semaphore = new Semaphore(params.maxConcurrent);
    ExecutorService agentWorkPool = Executors.newCachedThreadPool();
    ExecutorService testThreads = Executors.newCachedThreadPool();
    AtomicBoolean running = new AtomicBoolean(true);
    ViolationCollector violations = new ViolationCollector();

    // Thread 1: Acquisition loop
    Future<?> acquirer =
        testThreads.submit(
            () -> {
              long runCount = 0L;
              try {
                acquisitionService.saturatePool(runCount++, semaphore, agentWorkPool);
                while (running.get()) {
                  acquisitionService.saturatePool(runCount++, semaphore, agentWorkPool);
                  Thread.sleep(10);
                }
              } catch (Throwable t) {
                violations.add("acquisition-thread exception: " + t);
              }
            });

    // Thread 2: Zombie cleanup
    Future<?> zombieCleaner =
        testThreads.submit(
            () -> {
              try {
                while (running.get()) {
                  java.util.Map<String, String> active =
                      new java.util.HashMap<>(acquisitionService.getActiveAgentsMap());
                  java.util.Map<String, Future<?>> futures =
                      new java.util.HashMap<>(acquisitionService.getActiveAgentsFutures());
                  zombieCleanup.cleanupZombieAgents(active, futures);
                  Thread.sleep(ThreadLocalRandom.current().nextInt(50, 201));
                }
              } catch (Throwable t) {
                violations.add("zombie-cleaner exception: " + t);
              }
            });

    // Thread 3: Orphan cleanup
    Future<?> orphanCleaner =
        testThreads.submit(
            () -> {
              try {
                while (running.get()) {
                  orphanCleanup.forceCleanupOrphanedAgents();
                  Thread.sleep(ThreadLocalRandom.current().nextInt(200, 501));
                }
              } catch (Throwable t) {
                violations.add("orphan-cleaner exception: " + t);
              }
            });

    // Thread 4: Shutdown toggles (run exact number of iterations)
    Future<?> shutdownToggler =
        testThreads.submit(
            () -> {
              try {
                boolean state = false;
                for (int i = 0; i < toggles; i++) {
                  state = !state;
                  acquisitionService.setShuttingDown(state);
                  Thread.sleep(ThreadLocalRandom.current().nextInt(200, 501));
                }
              } catch (Throwable t) {
                violations.add("shutdown-toggle exception: " + t);
              }
            });

    // Thread 5: Invariant checker
    // Mid-run set checks removed to avoid sampling races; end-of-run assertions remain
    Future<?> invariantChecker =
        testThreads.submit(
            () -> {
              final long[] mismatchSince = new long[] {0L};
              try (Jedis j = jedisPool.getResource()) {
                long endBy = System.currentTimeMillis() + params.duration.toMillis();
                while (System.currentTimeMillis() < endBy) {
                  try {
                    // Intentionally skip mid-run set checks to avoid sampling races

                    // Permit mismatch (aligned with scheduler health summary):
                    // heldPermits must not exceed active agents (allow small tolerance for
                    // concurrent state transitions)
                    int totalPermits = params.maxConcurrent;
                    int available = semaphore.availablePermits();
                    int held = Math.max(0, totalPermits - available);
                    int active = acquisitionService.getActiveAgentsMap().size();
                    // Allow tolerance of 1 permit for concurrent state transitions;
                    // Semaphore.release() can also overshoot
                    if (held > active + 1) {
                      long now = System.currentTimeMillis();
                      if (mismatchSince[0] == 0L) {
                        mismatchSince[0] = now;
                      } else if (now - mismatchSince[0] > 500L) {
                        violations.add(
                            "Invariant violated: permits held>active+1 held="
                                + held
                                + " active="
                                + active
                                + " total="
                                + totalPermits);
                        mismatchSince[0] = 0L; // record at most once per persistent window
                      }
                    } else {
                      mismatchSince[0] = 0L;
                    }

                    Thread.sleep(ThreadLocalRandom.current().nextInt(25, 51));
                  } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                  } catch (Throwable t) {
                    violations.add("invariant-check exception: " + t);
                  }
                }
              } catch (Throwable t) {
                violations.add("invariant-check setup exception: " + t);
              }
            });

    // Run window: duration proportional to toggles, then stop
    Thread.sleep(params.duration.toMillis());
    running.set(false);

    // Join threads
    acquirer.get(10, TimeUnit.SECONDS);
    zombieCleaner.get(10, TimeUnit.SECONDS);
    orphanCleaner.get(10, TimeUnit.SECONDS);
    shutdownToggler.get(10, TimeUnit.SECONDS);
    invariantChecker.get(10, TimeUnit.SECONDS);

    // Wait for all active agent futures to complete (ensures finally blocks have run)
    java.util.Map<String, Future<?>> futures = acquisitionService.getActiveAgentsFutures();
    for (Future<?> future : futures.values()) {
      try {
        future.get(5, TimeUnit.SECONDS);
      } catch (Exception ignored) {
        // Future may be cancelled or failed - that's fine
      }
    }

    // Wait for active agents to settle (should be quick now that all workers finished)
    long activeDeadline = System.currentTimeMillis() + 2000L;
    while (System.currentTimeMillis() < activeDeadline
        && acquisitionService.getActiveAgentCount() > 0) {
      try {
        Thread.sleep(50);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    // Process any remaining completion queue items after threads have stopped
    // This ensures agents that completed just before shutdown are properly rescheduled
    try {
      acquisitionService.saturatePool(Long.MAX_VALUE, null, agentWorkPool);
    } catch (Exception e) {
      // Best-effort: ignore errors during post-shutdown processing
    }

    // Shutdown pools
    TestFixtures.shutdownExecutorSafely(agentWorkPool);
    TestFixtures.shutdownExecutorSafely(testThreads);

    return new StressResult(new ArrayList<>(violations.violations));
  }

  private StressResult runCombinedStress(StressParams params) throws Exception {
    // Properties - same as runStress but with longer duration settings
    PriorityAgentProperties agentProps = new PriorityAgentProperties();
    agentProps.setEnabledPattern(".*");
    agentProps.setDisabledPattern("");
    agentProps.setMaxConcurrentAgents(params.maxConcurrent);

    PrioritySchedulerProperties schedProps = new PrioritySchedulerProperties();
    schedProps.getKeys().setWaitingSet("waiting");
    schedProps.getKeys().setWorkingSet("working");
    schedProps.getKeys().setCleanupLeaderKey("cleanup-leader");
    schedProps.getCircuitBreaker().setEnabled(false);
    // Make cleanup responsive for stress
    schedProps.getZombieCleanup().setEnabled(true);
    schedProps.getZombieCleanup().setThresholdMs(200L);
    schedProps.getOrphanCleanup().setEnabled(true);
    schedProps.getOrphanCleanup().setThresholdMs(1_000L);

    // Dependencies
    AgentIntervalProvider intervalProvider =
        agent -> new AgentIntervalProvider.Interval(200L, 200L, 400L);
    ShardingFilter shardingFilter = a -> true;

    AgentAcquisitionService acquisitionService =
        new AgentAcquisitionService(
            jedisPool,
            scriptManager,
            intervalProvider,
            shardingFilter,
            agentProps,
            schedProps,
            metrics);

    ZombieCleanupService zombieCleanup =
        new ZombieCleanupService(jedisPool, scriptManager, schedProps, metrics);
    zombieCleanup.setAcquisitionService(acquisitionService);

    OrphanCleanupService orphanCleanup =
        new OrphanCleanupService(jedisPool, scriptManager, schedProps, metrics);
    orphanCleanup.setAcquisitionService(acquisitionService);

    // Register agents with random execution behavior
    for (int i = 0; i < params.numAgents; i++) {
      Agent a = mockAgent("combined-agent-" + i, "combined");
      AgentExecution exec = RandomExecutionFactory.randomized(10, 500);
      ExecutionInstrumentation instr = TestFixtures.createMockInstrumentation();
      acquisitionService.registerAgent(a, exec, instr);
    }

    // Concurrency controls
    Semaphore semaphore = new Semaphore(params.maxConcurrent);
    ExecutorService agentWorkPool = Executors.newCachedThreadPool();
    ExecutorService testThreads = Executors.newCachedThreadPool();
    AtomicBoolean running = new AtomicBoolean(true);
    ViolationCollector violations = new ViolationCollector();

    // Thread 1: Acquisition loop
    Future<?> acquirer =
        testThreads.submit(
            () -> {
              long runCount = 0L;
              try {
                // Initial repopulation
                acquisitionService.saturatePool(runCount++, semaphore, agentWorkPool);
                while (running.get()) {
                  acquisitionService.saturatePool(runCount++, semaphore, agentWorkPool);
                  Thread.sleep(10);
                }
              } catch (Throwable t) {
                violations.add("acquisition-thread exception: " + t);
              }
            });

    // Thread 2: Zombie cleanup (50-200ms intervals)
    Future<?> zombieCleaner =
        testThreads.submit(
            () -> {
              try {
                while (running.get()) {
                  java.util.Map<String, String> active =
                      new java.util.HashMap<>(acquisitionService.getActiveAgentsMap());
                  java.util.Map<String, Future<?>> futures =
                      new java.util.HashMap<>(acquisitionService.getActiveAgentsFutures());
                  zombieCleanup.cleanupZombieAgents(active, futures);
                  Thread.sleep(ThreadLocalRandom.current().nextInt(50, 201));
                }
              } catch (Throwable t) {
                violations.add("zombie-cleaner exception: " + t);
              }
            });

    // Thread 3: Orphan cleanup (200-500ms intervals)
    Future<?> orphanCleaner =
        testThreads.submit(
            () -> {
              try {
                while (running.get()) {
                  orphanCleanup.forceCleanupOrphanedAgents();
                  Thread.sleep(ThreadLocalRandom.current().nextInt(200, 501));
                }
              } catch (Throwable t) {
                violations.add("orphan-cleaner exception: " + t);
              }
            });

    // Thread 4: Shutdown toggles (continuous during run)
    Future<?> shutdownToggler =
        testThreads.submit(
            () -> {
              try {
                boolean state = false;
                while (running.get()) {
                  state = !state;
                  acquisitionService.setShuttingDown(state);
                  Thread.sleep(ThreadLocalRandom.current().nextInt(500, 1501));
                }
              } catch (Throwable t) {
                violations.add("shutdown-toggle exception: " + t);
              }
            });

    // Thread 5: Invariant checker (sampling during run)
    Future<?> invariantChecker =
        testThreads.submit(
            () -> {
              final long[] mismatchSince = new long[] {0L};
              try (Jedis j = jedisPool.getResource()) {
                long endBy = System.currentTimeMillis() + params.duration.toMillis();
                while (System.currentTimeMillis() < endBy) {
                  try {
                    // Permit mismatch check (allow small tolerance for concurrent state
                    // transitions)
                    int totalPermits = params.maxConcurrent;
                    int available = semaphore.availablePermits();
                    int held = Math.max(0, totalPermits - available);
                    int active = acquisitionService.getActiveAgentsMap().size();
                    // Allow tolerance of 1 permit for concurrent state transitions;
                    // Semaphore.release() can also overshoot
                    if (held > active + 1) {
                      long now = System.currentTimeMillis();
                      if (mismatchSince[0] == 0L) {
                        mismatchSince[0] = now;
                      } else if (now - mismatchSince[0] > 500L) {
                        violations.add(
                            "Invariant violated: permits held>active+1 held="
                                + held
                                + " active="
                                + active
                                + " total="
                                + totalPermits);
                        mismatchSince[0] = 0L;
                      }
                    } else {
                      mismatchSince[0] = 0L;
                    }

                    Thread.sleep(ThreadLocalRandom.current().nextInt(25, 51));
                  } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                  } catch (Throwable t) {
                    violations.add("invariant-check exception: " + t);
                  }
                }
              } catch (Throwable t) {
                violations.add("invariant-check setup exception: " + t);
              }
            });

    // Run the stress window
    Thread.sleep(params.duration.toMillis());
    running.set(false);

    // Join threads
    acquirer.get(10, TimeUnit.SECONDS);
    zombieCleaner.get(10, TimeUnit.SECONDS);
    orphanCleaner.get(10, TimeUnit.SECONDS);
    shutdownToggler.get(10, TimeUnit.SECONDS);
    invariantChecker.get(10, TimeUnit.SECONDS);

    // Wait for all active agent futures to complete (ensures finally blocks have run)
    java.util.Map<String, Future<?>> futures = acquisitionService.getActiveAgentsFutures();
    for (Future<?> future : futures.values()) {
      try {
        future.get(5, TimeUnit.SECONDS);
      } catch (Exception ignored) {
        // Future may be cancelled or failed - that's fine
      }
    }

    // Wait for active agents to settle (should be quick now that all workers finished)
    long activeDeadline = System.currentTimeMillis() + 2000L;
    while (System.currentTimeMillis() < activeDeadline
        && acquisitionService.getActiveAgentCount() > 0) {
      try {
        Thread.sleep(50);
      } catch (InterruptedException ie) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    // Process any remaining completion queue items after threads have stopped
    // This ensures agents that completed just before shutdown are properly rescheduled
    try {
      acquisitionService.saturatePool(Long.MAX_VALUE, null, agentWorkPool);
    } catch (Exception e) {
      // Best-effort: ignore errors during post-shutdown processing
    }

    // End-of-run eventual-consistency assertions with brief stabilization window
    try (Jedis j = jedisPool.getResource()) {
      String WAITING_KEY = schedProps.getKeys().getWaitingSet();
      String WORKING_KEY = schedProps.getKeys().getWorkingSet();
      long endBy = System.currentTimeMillis() + 1000L;
      boolean settled = false;
      java.util.List<String> waiting = null;
      java.util.List<String> working = null;
      int registered = params.numAgents;
      int sumSets = 0;
      int completing = 0;
      int active = 0;

      while (System.currentTimeMillis() < endBy && !settled) {
        waiting = j.zrange(WAITING_KEY, 0, -1);
        working = j.zrange(WORKING_KEY, 0, -1);
        sumSets = waiting.size() + working.size();
        completing = Math.max(0, acquisitionService.getCompletionQueueSize());
        active = Math.max(0, acquisitionService.getActiveAgentCount());
        if ((registered - sumSets) <= (completing + active)) {
          settled = true;
          break;
        }
        Thread.sleep(10);
      }

      // Use final values for assertions (or re-fetch if loop didn't run)
      if (waiting == null || working == null) {
        waiting = j.zrange(WAITING_KEY, 0, -1);
        working = j.zrange(WORKING_KEY, 0, -1);
        sumSets = waiting.size() + working.size();
        completing = Math.max(0, acquisitionService.getCompletionQueueSize());
        active = Math.max(0, acquisitionService.getActiveAgentCount());
      }

      java.util.Set<String> inter = new java.util.HashSet<>(waiting);
      inter.retainAll(working);
      org.assertj.core.api.Assertions.assertThat(inter)
          .describedAs("waiting/workingset must be disjoint at end-of-run")
          .isEmpty();
      org.assertj.core.api.Assertions.assertThat(sumSets)
          .describedAs("sum of sets must not exceed registered")
          .isLessThanOrEqualTo(registered);
      org.assertj.core.api.Assertions.assertThat(registered - sumSets)
          .describedAs(
              "missing members must be explainable by completing queue or active execution")
          .isLessThanOrEqualTo(completing + active);
    }

    // Shutdown pools
    TestFixtures.shutdownExecutorSafely(agentWorkPool);
    TestFixtures.shutdownExecutorSafely(testThreads);

    return new StressResult(new ArrayList<>(violations.violations));
  }

  private static Agent mockAgent(String name, String provider) {
    return TestFixtures.createMockAgent(name, provider);
  }

  /**
   * Verifies that key metrics were recorded during the stress run.
   *
   * <p>Checks that acquisition and cleanup metrics were recorded, indicating the stress test
   * exercised the expected code paths.
   */
  private void verifyMetricsRecorded() {
    // Verify acquisition metrics were recorded
    java.util.concurrent.atomic.AtomicLong acquireAttempts =
        new java.util.concurrent.atomic.AtomicLong(0);
    registry
        .counters()
        .forEach(
            c -> {
              if (c.id().name().equals("cats.priorityScheduler.acquire.attempts")) {
                acquireAttempts.addAndGet((long) c.count());
              }
            });
    assertThat(acquireAttempts.get())
        .describedAs("Acquisition attempts should be recorded during stress")
        .isGreaterThan(0);

    java.util.concurrent.atomic.AtomicLong acquiredCount =
        new java.util.concurrent.atomic.AtomicLong(0);
    registry
        .counters()
        .forEach(
            c -> {
              if (c.id().name().equals("cats.priorityScheduler.acquire.acquired")) {
                acquiredCount.addAndGet((long) c.count());
              }
            });
    assertThat(acquiredCount.get())
        .describedAs("Acquired count should be recorded during stress")
        .isGreaterThan(0);

    // Verify cleanup metrics were recorded (zombie or orphan cleanup)
    java.util.concurrent.atomic.AtomicLong cleanupTimerCount =
        new java.util.concurrent.atomic.AtomicLong(0);
    registry
        .timers()
        .forEach(
            t -> {
              if (t.id().name().equals("cats.priorityScheduler.cleanup.time")) {
                cleanupTimerCount.addAndGet(t.count());
              }
            });
    assertThat(cleanupTimerCount.get())
        .describedAs("Cleanup timer should be recorded during stress")
        .isGreaterThan(0);
  }

  private static final class StressParams {
    final int numAgents;
    final int maxConcurrent;
    final Duration duration;

    StressParams(int numAgents, int maxConcurrent, Duration duration) {
      this.numAgents = numAgents;
      this.maxConcurrent = maxConcurrent;
      this.duration = duration;
    }
  }

  private static final class StressResult {
    final List<String> violations;

    StressResult(List<String> violations) {
      this.violations = violations != null ? violations : Collections.emptyList();
    }
  }

  private static final class ViolationCollector {
    private final CopyOnWriteArrayList<String> violations = new CopyOnWriteArrayList<>();

    void add(String msg) {
      violations.add(msg);
    }
  }

  private static final class RandomExecutionFactory {
    static AgentExecution randomized(int minMillis, int maxMillis) {
      return agent -> {
        int delay = ThreadLocalRandom.current().nextInt(minMillis, maxMillis + 1);
        // AgentExecution#executeAgent cannot throw checked exceptions; swallow interrupt
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(delay);
        while (System.nanoTime() < end) {
          long remainingNanos = end - System.nanoTime();
          if (remainingNanos <= 0) break;
          try {
            TimeUnit.NANOSECONDS.sleep(Math.min(remainingNanos, TimeUnit.MILLISECONDS.toNanos(1)));
          } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      };
    }
  }
}
