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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

/**
 * Test fixtures and helper methods for Priority Scheduler tests.
 *
 * <p>Provides shared utility methods to reduce code duplication across test files. All methods are
 * static and thread-safe for use in parallel test execution.
 *
 * <p>Includes:
 *
 * <ul>
 *   <li>Mock object creation (agents, properties)
 *   <li>Controllable test execution implementations
 *   <li>Condition-based polling utilities (replaces fixed Thread.sleep() delays)
 *   <li>Standardized timeout constants for consistent test behavior
 *   <li>Redis sorted set assertions and time helpers
 *   <li>Service factory methods for test setup
 * </ul>
 */
public final class TestFixtures {

  // ============================================================================
  // STANDARDIZED TIMEOUT CONSTANTS
  // ============================================================================
  // Use these constants instead of hardcoded values for consistent test behavior
  // across different environments (local dev, CI, slow containers).

  /** Default timeout for condition-based polling (5 seconds). */
  public static final long DEFAULT_TIMEOUT_MS = 5000L;

  /** Short timeout for fast operations (1 second). */
  public static final long SHORT_TIMEOUT_MS = 1000L;

  /** Long timeout for slow operations or stress tests (30 seconds). */
  public static final long LONG_TIMEOUT_MS = 30000L;

  /** Default poll interval for condition checking (10ms). */
  public static final long DEFAULT_POLL_INTERVAL_MS = 10L;

  /** Fast poll interval for time-sensitive tests (5ms). */
  public static final long FAST_POLL_INTERVAL_MS = 5L;

  /** Slow poll interval to reduce CPU usage in long-running tests (50ms). */
  public static final long SLOW_POLL_INTERVAL_MS = 50L;

  private TestFixtures() {
    // Utility class - prevent instantiation
  }

  /**
   * Creates a mock Agent with the specified type and provider name.
   *
   * @param agentType The agent type identifier
   * @param providerName The provider name
   * @return A mocked Agent instance
   */
  public static Agent createMockAgent(String agentType, String providerName) {
    Agent agent = mock(Agent.class);
    when(agent.getAgentType()).thenReturn(agentType);
    when(agent.getProviderName()).thenReturn(providerName);
    return agent;
  }

  /**
   * Creates a mock Agent with the specified type and default provider name.
   *
   * @param agentType The agent type identifier
   * @return A mocked Agent instance with provider name "test-provider"
   */
  public static Agent createMockAgent(String agentType) {
    return createMockAgent(agentType, "test-provider");
  }

  /**
   * Creates a mock ExecutionInstrumentation for testing. This is the most common instrumentation
   * pattern used in tests where verification of instrumentation calls is not needed.
   *
   * @return A mocked ExecutionInstrumentation instance
   */
  public static ExecutionInstrumentation createMockInstrumentation() {
    return mock(ExecutionInstrumentation.class);
  }

  /**
   * Creates a no-op ExecutionInstrumentation implementation for testing. Use this when you need a
   * real implementation that does nothing, avoiding mock overhead and providing cleaner stack
   * traces.
   *
   * @return A no-op ExecutionInstrumentation instance
   */
  public static ExecutionInstrumentation createNoOpInstrumentation() {
    return new ExecutionInstrumentation() {
      @Override
      public void executionStarted(Agent agent) {}

      @Override
      public void executionCompleted(Agent agent, long elapsedMs) {}

      @Override
      public void executionFailed(Agent agent, Throwable cause, long elapsedMs) {}
    };
  }

  /**
   * Creates default PriorityAgentProperties for testing.
   *
   * @return PriorityAgentProperties with standard test configuration
   */
  public static PriorityAgentProperties createDefaultAgentProperties() {
    PriorityAgentProperties props = new PriorityAgentProperties();
    props.setMaxConcurrentAgents(100);
    props.setEnabledPattern(".*");
    props.setDisabledPattern("");
    return props;
  }

  /**
   * Creates default PriorityAgentProperties with custom concurrency limit.
   *
   * @param maxConcurrent Maximum concurrent agents
   * @return PriorityAgentProperties with specified concurrency limit
   */
  public static PriorityAgentProperties createDefaultAgentProperties(int maxConcurrent) {
    PriorityAgentProperties props = createDefaultAgentProperties();
    props.setMaxConcurrentAgents(maxConcurrent);
    return props;
  }

  /**
   * Creates default PrioritySchedulerProperties for testing.
   *
   * @return PrioritySchedulerProperties with standard test configuration
   */
  public static PrioritySchedulerProperties createDefaultSchedulerProperties() {
    PrioritySchedulerProperties props = new PrioritySchedulerProperties();
    props.setIntervalMs(1000L);
    props.setRefreshPeriodSeconds(30);
    props.getKeys().setWaitingSet("waiting");
    props.getKeys().setWorkingSet("working");
    props.getKeys().setCleanupLeaderKey("cleanup-leader");
    props.getZombieCleanup().setThresholdMs(1800000L); // 30 minutes
    props.getZombieCleanup().setIntervalMs(300000L); // 5 minutes
    props.getOrphanCleanup().setThresholdMs(7200000L); // 2 hours
    props.getOrphanCleanup().setIntervalMs(3600000L); // 1 hour
    props.getBatchOperations().setEnabled(false);
    return props;
  }

  /**
   * Creates PrioritySchedulerProperties with batch operations enabled.
   *
   * @return PrioritySchedulerProperties with batch operations enabled
   */
  public static PrioritySchedulerProperties createBatchEnabledSchedulerProperties() {
    PrioritySchedulerProperties props = createDefaultSchedulerProperties();
    props.getBatchOperations().setEnabled(true);
    props.getBatchOperations().setBatchSize(10);
    return props;
  }

  /**
   * A controllable AgentExecution implementation for testing.
   *
   * <p>Provides test-controlled execution completion via CountDownLatch or fixed duration. This
   * allows tests to verify intermediate state (execution started but not completed) and eliminates
   * unnecessary fixed delays.
   *
   * <p>Usage examples:
   *
   * <pre>{@code
   * // Test-controlled completion (preferred)
   * CountDownLatch latch = new CountDownLatch(1);
   * ControllableAgentExecution exec = new ControllableAgentExecution()
   *     .withCompletionLatch(latch);
   *
   * // Register and acquire agent...
   * assertThat(exec.isExecuting()).isTrue();
   *
   * // Test controls completion
   * latch.countDown();
   *
   * // Fixed duration fallback (for simple cases)
   * ControllableAgentExecution exec2 = new ControllableAgentExecution()
   *     .withFixedDuration(100);
   * }</pre>
   */
  public static class ControllableAgentExecution implements AgentExecution {
    private final AtomicBoolean executing = new AtomicBoolean(false);
    private final AtomicInteger executionCount = new AtomicInteger(0);
    private volatile CountDownLatch completionLatch;
    private volatile long fixedDurationMs = 0;
    private volatile boolean shouldFail = false;
    private volatile Runnable startCallback;

    @Override
    public void executeAgent(Agent agent) {
      executing.set(true);
      executionCount.incrementAndGet();
      if (startCallback != null) {
        startCallback.run();
      }
      try {
        if (completionLatch != null) {
          try {
            completionLatch.await(); // Test-controlled completion
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
        } else if (fixedDurationMs > 0) {
          try {
            Thread.sleep(fixedDurationMs); // Fixed duration fallback
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
        }

        if (shouldFail) {
          throw new RuntimeException("Simulated agent failure");
        }
      } finally {
        executing.set(false);
      }
    }

    /**
     * Sets a CountDownLatch to control when execution completes. Execution will block until the
     * latch is counted down. This is the preferred method for tests that need to verify
     * intermediate state.
     *
     * @param latch The latch that controls completion
     * @return This instance for method chaining
     */
    public ControllableAgentExecution withCompletionLatch(CountDownLatch latch) {
      this.completionLatch = latch;
      return this;
    }

    /**
     * Sets a fixed duration for execution. Execution will sleep for the specified duration. Use
     * this only for simple cases where test-controlled completion is not needed.
     *
     * @param ms Duration in milliseconds
     * @return This instance for method chaining
     */
    public ControllableAgentExecution withFixedDuration(long ms) {
      this.fixedDurationMs = ms;
      return this;
    }

    /**
     * Sets execution to fail with a RuntimeException.
     *
     * @return This instance for method chaining
     */
    public ControllableAgentExecution withFailure() {
      this.shouldFail = true;
      return this;
    }

    /**
     * Sets a callback to run when execution starts. Useful for tracking when agents begin
     * executing.
     *
     * @param callback The callback to run on start
     * @return This instance for method chaining
     */
    public ControllableAgentExecution withStartCallback(Runnable callback) {
      this.startCallback = callback;
      return this;
    }

    /**
     * Returns whether execution is currently in progress.
     *
     * @return true if execution is in progress, false otherwise
     */
    public boolean isExecuting() {
      return executing.get();
    }

    /**
     * Returns the number of times executeAgent has been called.
     *
     * @return Execution count
     */
    public int getExecutionCount() {
      return executionCount.get();
    }
  }

  /**
   * Waits for a Redis key to expire by polling its TTL or existence.
   *
   * <p>This replaces fixed Thread.sleep() delays and can reveal bugs if TTL doesn't work correctly.
   *
   * @param jedis Jedis connection to Redis
   * @param key The key to wait for expiration
   * @param timeoutMs Maximum time to wait in milliseconds
   * @param pollIntervalMs Interval between checks in milliseconds
   * @return true if key expired within timeout, false otherwise
   */
  public static boolean waitForKeyExpiration(
      Jedis jedis, String key, long timeoutMs, long pollIntervalMs) {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < timeoutMs) {
      // Check if key exists - if it doesn't, it has expired
      if (!jedis.exists(key)) {
        return true;
      }
      // Also check TTL - if it's -2, key doesn't exist (expired)
      long ttl = jedis.ttl(key);
      if (ttl == -2) {
        return true;
      }
      try {
        Thread.sleep(pollIntervalMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }

  /**
   * Waits for a Redis key to expire (convenience method with default poll interval).
   *
   * @param jedis Jedis connection to Redis
   * @param key The key to wait for expiration
   * @param timeoutMs Maximum time to wait in milliseconds
   * @return true if key expired within timeout, false otherwise
   */
  public static boolean waitForKeyExpiration(Jedis jedis, String key, long timeoutMs) {
    return waitForKeyExpiration(jedis, key, timeoutMs, 50);
  }

  /**
   * Waits for a background task to complete by polling a completion condition.
   *
   * <p>This replaces fixed Thread.sleep() delays and can reveal race conditions if tasks don't
   * complete as expected.
   *
   * @param completionCheck Supplier that returns true when task is complete
   * @param timeoutMs Maximum time to wait in milliseconds
   * @param pollIntervalMs Interval between checks in milliseconds
   * @return true if task completed within timeout, false otherwise
   */
  public static boolean waitForBackgroundTask(
      Supplier<Boolean> completionCheck, long timeoutMs, long pollIntervalMs) {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < timeoutMs) {
      if (completionCheck.get()) {
        return true;
      }
      try {
        Thread.sleep(pollIntervalMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }

  /**
   * Waits for a background task to complete (convenience method with default poll interval).
   *
   * @param completionCheck Supplier that returns true when task is complete
   * @param timeoutMs Maximum time to wait in milliseconds
   * @return true if task completed within timeout, false otherwise
   */
  public static boolean waitForBackgroundTask(Supplier<Boolean> completionCheck, long timeoutMs) {
    return waitForBackgroundTask(completionCheck, timeoutMs, DEFAULT_POLL_INTERVAL_MS);
  }

  // ============================================================================
  // CONDITION-BASED POLLING (waitForCondition)
  // ============================================================================
  // Aliases for waitForBackgroundTask with optional scheduler support.
  // Many test files use "waitForCondition" naming; these methods provide consistency.

  /**
   * Waits for a condition to become true by polling at regular intervals.
   *
   * <p>This is an alias for {@link #waitForBackgroundTask(Supplier, long, long)} provided for
   * consistency with test files that use the "waitForCondition" naming convention.
   *
   * @param condition Supplier that returns true when condition is met
   * @param timeoutMs Maximum time to wait in milliseconds
   * @param pollIntervalMs Interval between condition checks in milliseconds
   * @return true if condition was met within timeout, false otherwise
   */
  public static boolean waitForCondition(
      Supplier<Boolean> condition, long timeoutMs, long pollIntervalMs) {
    return waitForBackgroundTask(condition, timeoutMs, pollIntervalMs);
  }

  /**
   * Waits for a condition to become true, optionally running a scheduler between checks.
   *
   * <p>This version is useful when tests need to drive state changes (e.g., running scheduler
   * cycles) while waiting for a condition. The scheduler is executed after each failed condition
   * check, before sleeping for the poll interval.
   *
   * @param condition Supplier that returns true when condition is met
   * @param timeoutMs Maximum time to wait in milliseconds
   * @param pollIntervalMs Interval between condition checks in milliseconds
   * @param scheduler Optional runnable to execute between checks (may be null)
   * @return true if condition was met within timeout, false otherwise
   */
  public static boolean waitForCondition(
      Supplier<Boolean> condition, long timeoutMs, long pollIntervalMs, Runnable scheduler) {
    long startTime = System.currentTimeMillis();
    while (System.currentTimeMillis() - startTime < timeoutMs) {
      if (condition.get()) {
        return true;
      }
      if (scheduler != null) {
        scheduler.run();
      }
      try {
        Thread.sleep(pollIntervalMs);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return false;
      }
    }
    return false;
  }

  /**
   * Waits for a condition with default timeout and poll interval.
   *
   * @param condition Supplier that returns true when condition is met
   * @return true if condition was met within default timeout, false otherwise
   */
  public static boolean waitForCondition(Supplier<Boolean> condition) {
    return waitForCondition(condition, DEFAULT_TIMEOUT_MS, DEFAULT_POLL_INTERVAL_MS);
  }

  // ============================================================================
  // REFLECTION HELPERS
  // ============================================================================
  // Helpers for accessing/setting fields via reflection in tests.
  // Intentional to avoid polluting production code with test-only accessors.

  /**
   * Helper method to access a field via reflection for test doubles.
   *
   * <p>Reduces code duplication when injecting test doubles. Reflection is acceptable for test
   * doubles (error/performance testing).
   *
   * @param target The object to access the field from
   * @param clazz The class that declares the field
   * @param fieldName The name of the field
   * @return The field value
   * @throws RuntimeException if reflection fails
   */
  @SuppressWarnings("unchecked")
  public static <T> T getField(Object target, Class<?> clazz, String fieldName) {
    try {
      java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
      field.setAccessible(true);
      return (T) field.get(target);
    } catch (Exception e) {
      throw new RuntimeException("Failed to access field " + fieldName + " via reflection", e);
    }
  }

  /**
   * Helper method to set a field via reflection for test doubles.
   *
   * <p>Reduces code duplication when injecting test doubles. Reflection is acceptable for test
   * doubles (error/performance testing).
   *
   * @param target The object to set the field on
   * @param clazz The class that declares the field
   * @param fieldName The name of the field
   * @param value The value to set
   * @throws RuntimeException if reflection fails
   */
  public static void setField(Object target, Class<?> clazz, String fieldName, Object value) {
    try {
      java.lang.reflect.Field field = clazz.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (Exception e) {
      throw new RuntimeException("Failed to set field " + fieldName + " via reflection", e);
    }
  }

  // ============================================================================
  // REDIS TIME HELPERS
  // ============================================================================
  // Redis sorted sets in Priority Scheduler use epoch seconds (not milliseconds).
  // These helpers reduce duplication and clarify intent for time-based score calculations.

  /**
   * Gets current time in seconds (Redis sorted set score convention).
   *
   * <p>Redis sorted sets in Priority Scheduler use epoch seconds, not milliseconds.
   *
   * @return Current time as seconds since epoch
   */
  public static long nowSeconds() {
    return System.currentTimeMillis() / 1000;
  }

  /**
   * Gets Redis server time in seconds for accurate cross-instance comparisons.
   *
   * <p>Use this when testing multi-instance coordination or clock skew handling.
   *
   * @param jedis Active Jedis connection
   * @return Redis server time as seconds since epoch
   */
  public static long getRedisTimeSeconds(Jedis jedis) {
    List<String> time = jedis.time();
    return Long.parseLong(time.get(0));
  }

  /**
   * Returns a score representing N seconds in the past.
   *
   * <p>Useful for creating "overdue" agents in tests.
   *
   * @param seconds Number of seconds ago
   * @return Epoch seconds representing that time
   */
  public static long secondsAgo(long seconds) {
    return nowSeconds() - seconds;
  }

  /**
   * Returns a score representing N seconds in the future.
   *
   * <p>Useful for creating agents with future execution times.
   *
   * @param seconds Number of seconds from now
   * @return Epoch seconds representing that time
   */
  public static long secondsFromNow(long seconds) {
    return nowSeconds() + seconds;
  }

  /**
   * Returns a score representing N minutes in the past.
   *
   * <p>Convenience method for longer time deltas.
   *
   * @param minutes Number of minutes ago
   * @return Epoch seconds representing that time
   */
  public static long minutesAgo(long minutes) {
    return secondsAgo(minutes * 60);
  }

  // ============================================================================
  // REDIS SORTED SET HELPERS
  // ============================================================================
  // Assertions and utilities for Redis sorted set operations in tests.
  // Reduces duplication of zscore/zadd patterns across test files.

  /**
   * Asserts that an agent exists in a Redis sorted set.
   *
   * @param jedis Active Jedis connection
   * @param set The sorted set name (e.g., "waiting" or "working")
   * @param agentType The agent type identifier
   */
  public static void assertAgentInSet(Jedis jedis, String set, String agentType) {
    assertThat(jedis.zscore(set, agentType))
        .describedAs("Agent '%s' should exist in '%s' set", agentType, set)
        .isNotNull();
  }

  /**
   * Asserts that an agent does NOT exist in a Redis sorted set.
   *
   * @param jedis Active Jedis connection
   * @param set The sorted set name (e.g., "waiting" or "working")
   * @param agentType The agent type identifier
   */
  public static void assertAgentNotInSet(Jedis jedis, String set, String agentType) {
    assertThat(jedis.zscore(set, agentType))
        .describedAs("Agent '%s' should not exist in '%s' set", agentType, set)
        .isNull();
  }

  /**
   * Adds an agent to a sorted set with a "ready now" score (10 seconds ago).
   *
   * <p>Common pattern for making agents immediately eligible for acquisition.
   *
   * @param jedis Active Jedis connection
   * @param waitingSet The waiting set name
   * @param agentType The agent type identifier
   */
  public static void addReadyAgent(Jedis jedis, String waitingSet, String agentType) {
    long readyScore = secondsAgo(10);
    jedis.zadd(waitingSet, readyScore, agentType);
  }

  /**
   * Adds an agent to a sorted set with a custom score.
   *
   * @param jedis Active Jedis connection
   * @param set The sorted set name
   * @param agentType The agent type identifier
   * @param score The score to assign
   */
  public static void addAgentWithScore(Jedis jedis, String set, String agentType, long score) {
    jedis.zadd(set, score, agentType);
  }

  /**
   * Cleans up Redis sorted sets between tests.
   *
   * <p>Use in @AfterEach or @BeforeEach to ensure test isolation.
   *
   * @param jedis Active Jedis connection
   * @param sets The set names to delete
   */
  public static void cleanupRedisSets(Jedis jedis, String... sets) {
    for (String set : sets) {
      jedis.del(set);
    }
  }

  // ============================================================================
  // SERVICE FACTORY HELPERS
  // ============================================================================
  // Factory methods for creating test instances of scheduler services.
  // Reduces boilerplate in test setUp() methods.

  /**
   * Creates a fresh PrioritySchedulerMetrics instance with a new DefaultRegistry.
   *
   * <p>Useful for tests that need isolated metrics.
   *
   * @return New PrioritySchedulerMetrics instance
   */
  public static PrioritySchedulerMetrics createTestMetrics() {
    return new PrioritySchedulerMetrics(new DefaultRegistry());
  }

  /**
   * Creates and initializes a RedisScriptManager for testing.
   *
   * <p>Scripts are loaded immediately, ready for use.
   *
   * @param jedisPool Active JedisPool connection
   * @return Initialized RedisScriptManager
   */
  public static RedisScriptManager createTestScriptManager(JedisPool jedisPool) {
    RedisScriptManager manager = new RedisScriptManager(jedisPool, createTestMetrics());
    manager.initializeScripts();
    return manager;
  }

  /**
   * Creates and initializes a RedisScriptManager with custom metrics.
   *
   * <p>Use when you need to verify metrics in tests.
   *
   * @param jedisPool Active JedisPool connection
   * @param metrics Metrics instance for verification
   * @return Initialized RedisScriptManager
   */
  public static RedisScriptManager createTestScriptManager(
      JedisPool jedisPool, PrioritySchedulerMetrics metrics) {
    RedisScriptManager manager = new RedisScriptManager(jedisPool, metrics);
    manager.initializeScripts();
    return manager;
  }

  /**
   * Creates a ZombieCleanupService with standard test configuration.
   *
   * @param jedisPool Active JedisPool connection
   * @param scriptManager Initialized script manager
   * @param props Scheduler properties
   * @return Configured ZombieCleanupService
   */
  public static ZombieCleanupService createTestZombieService(
      JedisPool jedisPool, RedisScriptManager scriptManager, PrioritySchedulerProperties props) {
    return new ZombieCleanupService(jedisPool, scriptManager, props, createTestMetrics());
  }

  // ============================================================================
  // LOG CAPTURE HELPERS
  // ============================================================================
  // Utilities for capturing and asserting log output in tests.
  // Reduces boilerplate for Logback appender setup.

  /**
   * Sets up log capture for a specific class and returns the appender.
   *
   * <p>Remember to call {@link #detachLogs(ListAppender, Class)} in a finally block after test
   * completion to prevent memory leaks.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * ListAppender<ILoggingEvent> appender = captureLogsFor(MyService.class);
   * try {
   *   // Run test code that logs...
   *   assertThat(countLogsAtLevelContaining(appender, Level.WARN, "expected")).isGreaterThan(0);
   * } finally {
   *   detachLogs(appender, MyService.class);
   * }
   * }</pre>
   *
   * @param clazz The class whose logger to capture
   * @return ListAppender configured and started for log capture
   */
  public static ListAppender<ILoggingEvent> captureLogsFor(Class<?> clazz) {
    ListAppender<ILoggingEvent> appender = new ListAppender<>();
    Logger logger = (Logger) LoggerFactory.getLogger(clazz);
    logger.addAppender(appender);
    appender.start();
    return appender;
  }

  /**
   * Counts log messages at a specific level containing the specified substring.
   *
   * <p>Combines level filtering with message content filtering in one call.
   *
   * @param appender The log appender from captureLogsFor()
   * @param level The log level to filter by (e.g., Level.WARN)
   * @param substring The substring to search for in log messages
   * @return Number of matching log messages
   */
  public static long countLogsAtLevelContaining(
      ListAppender<ILoggingEvent> appender, Level level, String substring) {
    return appender.list.stream()
        .filter(e -> e.getLevel() == level && e.getFormattedMessage().contains(substring))
        .count();
  }

  /**
   * Detaches a log appender from a class's logger.
   *
   * <p>Use in finally blocks to clean up after log capture.
   *
   * @param appender The appender to detach
   * @param clazz The class whose logger the appender was attached to
   */
  public static void detachLogs(ListAppender<ILoggingEvent> appender, Class<?> clazz) {
    Logger logger = (Logger) LoggerFactory.getLogger(clazz);
    logger.detachAppender(appender);
  }

  // ============================================================================
  // REDIS CONNECTION HELPERS
  // ============================================================================
  // Helpers for creating JedisPool connections to testcontainers.
  // Centralizes connection parameters for consistency across tests.

  /**
   * Creates a JedisPool configured for testcontainer usage.
   *
   * <p>Centralizes connection parameters for consistency across tests.
   *
   * @param redisContainer The testcontainer Redis instance
   * @param password Redis password
   * @param maxTotal Maximum pool size
   * @return Configured JedisPool
   */
  public static JedisPool createTestJedisPool(
      GenericContainer<?> redisContainer, String password, int maxTotal) {
    JedisPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(maxTotal);
    return new JedisPool(
        config, redisContainer.getHost(), redisContainer.getMappedPort(6379), 2000, password);
  }

  /**
   * Creates a JedisPool configured for testcontainer usage with default maxTotal (10).
   *
   * @param redisContainer The testcontainer Redis instance
   * @param password Redis password
   * @return Configured JedisPool
   */
  public static JedisPool createTestJedisPool(GenericContainer<?> redisContainer, String password) {
    return createTestJedisPool(redisContainer, password, 10);
  }

  /**
   * Creates a JedisPool with default test password ("testpass") and default maxTotal (10).
   *
   * @param redisContainer The testcontainer Redis instance
   * @return Configured JedisPool
   */
  public static JedisPool createTestJedisPool(GenericContainer<?> redisContainer) {
    return createTestJedisPool(redisContainer, "testpass", 10);
  }

  /**
   * Creates a JedisPool pointing to localhost for unit tests.
   *
   * <p>Use this for unit tests that need a JedisPool instance but don't require actual Redis
   * connectivity (e.g., tests with mocked interactions or tests that expect connection failures).
   *
   * @return JedisPool configured for localhost
   */
  public static JedisPool createLocalhostJedisPool() {
    // Jedis 4.x requires explicit host/port - single-string constructor interprets as URI
    return new JedisPool(new JedisPoolConfig(), "localhost", 6379);
  }

  // ============================================================================
  // TEST CLEANUP UTILITIES
  // ============================================================================
  // Safe cleanup methods for test teardown to prevent resource leaks and flakiness.

  /** Generous timeout for CI environments where operations may be slow. */
  public static final long CI_GENEROUS_TIMEOUT_MS = 30_000;

  /** Short timeout for quick operations in CI. */
  public static final long CI_SHORT_TIMEOUT_MS = 5_000;

  /** Default timeout for executor shutdown in tests. */
  public static final long EXECUTOR_SHUTDOWN_TIMEOUT_MS = 5_000;

  /**
   * Safely shuts down an ExecutorService and waits for termination.
   *
   * <p>This method prevents test flakiness by ensuring all submitted tasks complete before the next
   * test runs. It uses shutdownNow() to interrupt running tasks, then awaits termination.
   *
   * @param executor The executor service to shut down (may be null)
   * @param timeoutMs Maximum time to wait for termination in milliseconds
   */
  public static void shutdownExecutorSafely(ExecutorService executor, long timeoutMs) {
    if (executor == null) {
      return;
    }
    executor.shutdownNow();
    try {
      if (!executor.awaitTermination(timeoutMs, TimeUnit.MILLISECONDS)) {
        // Log but don't fail - CI environments may be slow
        org.slf4j.LoggerFactory.getLogger(TestFixtures.class)
            .warn("Executor did not terminate within {}ms", timeoutMs);
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Safely shuts down an ExecutorService with default timeout.
   *
   * @param executor The executor service to shut down (may be null)
   */
  public static void shutdownExecutorSafely(ExecutorService executor) {
    shutdownExecutorSafely(executor, EXECUTOR_SHUTDOWN_TIMEOUT_MS);
  }

  /**
   * Safely closes a JedisPool after flushing all data.
   *
   * <p>This method prevents connection leaks by properly closing the pool. It first attempts to
   * flush all Redis data to ensure test isolation, then closes the pool to release connections.
   *
   * @param pool The JedisPool to close (may be null or already closed)
   */
  public static void closePoolSafely(JedisPool pool) {
    if (pool == null || pool.isClosed()) {
      return;
    }
    try {
      try (Jedis jedis = pool.getResource()) {
        jedis.flushAll();
      }
    } catch (Exception ignored) {
      // Ignore flush errors - pool may already be in bad state
    }
    try {
      pool.close();
    } catch (Exception ignored) {
      // Ignore close errors
    }
  }

  /**
   * Waits for a thread to complete with timeout.
   *
   * <p>Use this instead of Thread.join() without timeout to prevent hung tests.
   *
   * @param thread The thread to wait for (may be null)
   * @param timeoutMs Maximum time to wait in milliseconds
   * @return true if thread completed, false if timeout or interrupted
   */
  public static boolean joinThreadSafely(Thread thread, long timeoutMs) {
    if (thread == null || !thread.isAlive()) {
      return true;
    }
    try {
      thread.join(timeoutMs);
      return !thread.isAlive();
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return false;
    }
  }
}
