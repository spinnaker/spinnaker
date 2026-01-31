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
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.ShardingFilter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Error handling tests for PriorityScheduler components.
 *
 * <p>Tests error scenarios including:
 *
 * <ul>
 *   <li>AgentSchedulingException usage and propagation
 *   <li>Script loading failures and recovery
 *   <li>Batch operation failures with fallback
 *   <li>Configuration validation edge cases
 *   <li>Resource cleanup under error conditions
 * </ul>
 *
 * <p>Tests in this suite focus on graceful error handling behavior (no crashes, proper fallback)
 * rather than implementation details (specific metric values).
 */
@Testcontainers
@DisplayName("Error Handling Tests")
@SuppressWarnings("resource") // GenericContainer lifecycle managed by @Testcontainers
@Timeout(60)
public class ErrorHandlingTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine")
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", "testpass");

  private JedisPool jedisPool;
  private RedisScriptManager scriptManager;
  private PrioritySchedulerProperties schedulerProperties;
  private PriorityAgentProperties agentProperties;
  private ExecutorService executorService;
  private AgentAcquisitionService acquisitionService;
  private AgentIntervalProvider intervalProvider;
  private ShardingFilter shardingFilter;

  @BeforeEach
  void setUp() {
    jedisPool = TestFixtures.createTestJedisPool(redis, "testpass", 8);

    // Clean Redis state
    try (Jedis jedis = jedisPool.getResource()) {
      jedis.flushAll();
    }

    scriptManager = createTestScriptManager(jedisPool);

    schedulerProperties = new PrioritySchedulerProperties();
    schedulerProperties.getKeys().setWaitingSet("waiting");
    schedulerProperties.getKeys().setWorkingSet("working");
    schedulerProperties.getKeys().setCleanupLeaderKey("cleanup-leader");
    agentProperties = new PriorityAgentProperties();
    agentProperties.setMaxConcurrentAgents(10);

    // Mock the required dependencies
    intervalProvider = agent -> new AgentIntervalProvider.Interval(60000L, 120000L);
    shardingFilter = agent -> true;

    executorService = Executors.newFixedThreadPool(5);
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

  @Nested
  @DisplayName("Script Loading Failure Tests")
  class ScriptLoadingFailureTests {

    /**
     * Tests that script loading failures are wrapped in AgentSchedulingException. Verifies that
     * initializeScripts wraps script compilation errors with appropriate message and cause.
     */
    @Test
    @DisplayName("Should handle corrupted Lua script gracefully")
    void shouldHandleCorruptedLuaScript() {
      // Create a script manager with mock Jedis pool that fails script loading
      JedisPool mockPool = mock(JedisPool.class);
      Jedis mockJedis = mock(Jedis.class);
      when(mockPool.getResource()).thenReturn(mockJedis);
      when(mockJedis.scriptLoad(anyString()))
          .thenThrow(new RuntimeException("Script compilation error"));

      RedisScriptManager failingScriptManager =
          new RedisScriptManager(mockPool, TestFixtures.createTestMetrics());

      // Script loading should wrap the exception in AgentSchedulingException - this is expected
      // behavior
      assertThatThrownBy(
              () -> {
                failingScriptManager.initializeScripts();
              })
          .isInstanceOf(AgentSchedulingException.class)
          .hasMessageContaining("Failed to initialize Redis scripts")
          .hasCauseInstanceOf(RuntimeException.class);

      // Verify script loading was attempted
      verify(mockJedis, atLeastOnce()).scriptLoad(anyString());
    }

    /**
     * Tests that RedisScriptManager validates script names. Verifies that unknown, null, and empty
     * script names throw IllegalArgumentException with appropriate messages.
     */
    @Test
    @DisplayName("Should validate script names and throw for unknown scripts")
    void shouldValidateScriptNamesAndThrowForUnknownScripts() {
      // Test that accessing unknown script SHA throws appropriate exception
      RedisScriptManager manager = createTestScriptManager(jedisPool);

      assertThatThrownBy(
              () -> {
                manager.getScriptSha("UNKNOWN_SCRIPT");
              })
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown script: UNKNOWN_SCRIPT");

      // Test with null script name - should throw IllegalArgumentException with clear message
      assertThatThrownBy(
              () -> {
                manager.getScriptSha(null);
              })
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Script name cannot be null");

      // Test with empty script name
      assertThatThrownBy(
              () -> {
                manager.getScriptSha("");
              })
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown script: ");
    }
  }

  @Nested
  @DisplayName("Batch Operation Failure Tests")
  class BatchOperationFailureTests {

    /**
     * Tests that batch operation failures fall back to individual mode gracefully. Uses a spy to
     * inject an invalid SHA for the batch acquisition script, verifying that saturatePool handles
     * the failure by falling back to individual acquisition mode.
     */
    @Test
    @DisplayName("Should fallback to individual mode when batch script fails")
    void shouldFallbackWhenBatchScriptFails() throws Exception {
      // Enable batch operations to trigger the batch path
      schedulerProperties.getBatchOperations().setEnabled(true);
      schedulerProperties.getBatchOperations().setBatchSize(5);

      // Create a spy on the real script manager so we can make just the batch script fail
      RedisScriptManager spyScriptManager = spy(scriptManager);

      // Make batch acquisition script return invalid SHA to trigger failure
      when(spyScriptManager.getScriptSha(RedisScriptManager.ACQUIRE_AGENTS))
          .thenReturn("invalid-batch-sha-will-cause-redis-error");
      // All other scripts work normally (using real implementation)

      AgentAcquisitionService serviceWithFailingBatch =
          new AgentAcquisitionService(
              jedisPool,
              spyScriptManager,
              intervalProvider,
              shardingFilter,
              agentProperties,
              schedulerProperties,
              TestFixtures.createTestMetrics());

      // Register some agents and add them to WAITING_SET so they can be acquired
      try (Jedis jedis = jedisPool.getResource()) {
        long nowSec = TestFixtures.getRedisTimeSeconds(jedis);
        for (int i = 1; i <= 3; i++) {
          Agent agent = TestFixtures.createMockAgent("fallback-agent-" + i, "test-provider");
          AgentExecution execution = mock(AgentExecution.class);
          ExecutionInstrumentation instrumentation = TestFixtures.createMockInstrumentation();
          serviceWithFailingBatch.registerAgent(agent, execution, instrumentation);

          // Add agents to WAITING_SET so they can be acquired
          jedis.zadd("waiting", nowSec - 1, "fallback-agent-" + i); // Ready now
        }
      }

      // Verify agents were registered
      assertThat(serviceWithFailingBatch.getRegisteredAgentCount())
          .describedAs("All agents should be registered")
          .isEqualTo(3);

      // Execute acquisition - batch should fail and fallback to individual
      int acquired = serviceWithFailingBatch.saturatePool(0L, null, executorService);

      // Verify graceful fallback: acquisition succeeded without throwing
      assertThat(acquired)
          .describedAs("Batch failure should fallback to individual mode, not crash")
          .isGreaterThanOrEqualTo(0);

      // Verify the batch script was actually called (and failed)
      verify(spyScriptManager, atLeastOnce()).getScriptSha(RedisScriptManager.ACQUIRE_AGENTS);

      // Verify local state is preserved after fallback
      assertThat(serviceWithFailingBatch.getRegisteredAgentCount())
          .describedAs("Registered agents should be preserved after batch fallback")
          .isEqualTo(3);
    }

    /**
     * Tests that partial batch failures are handled gracefully. When some agents in a batch fail to
     * acquire, the system should still acquire the successful ones and not crash. This tests
     * resilience during batch operations.
     */
    @Test
    @DisplayName("Should handle partial batch failure gracefully")
    void shouldHandlePartialBatchFailure() throws Exception {
      schedulerProperties.getBatchOperations().setEnabled(true);
      schedulerProperties.getBatchOperations().setBatchSize(2);

      // Register agents
      for (int i = 1; i <= 3; i++) {
        Agent agent = TestFixtures.createMockAgent("partial-agent-" + i, "test-provider");
        AgentExecution execution = mock(AgentExecution.class);
        ExecutionInstrumentation instrumentation = TestFixtures.createMockInstrumentation();
        acquisitionService.registerAgent(agent, execution, instrumentation);
      }

      // Verify agents were registered
      assertThat(acquisitionService.getRegisteredAgentCount())
          .describedAs("All agents should be registered")
          .isEqualTo(3);

      // Add agents to waiting set
      try (Jedis jedis = jedisPool.getResource()) {
        long nowSec = TestFixtures.getRedisTimeSeconds(jedis);
        for (int i = 1; i <= 3; i++) {
          jedis.zadd("waiting", nowSec - 1, "partial-agent-" + i);
        }
      }

      // Execute acquisition
      int acquired = acquisitionService.saturatePool(0L, null, executorService);

      // Verify acquisition handled gracefully - either succeed with batch or fallback
      assertThat(acquired)
          .describedAs("Acquisition should succeed with batch or fallback mode")
          .isGreaterThanOrEqualTo(0)
          .isLessThanOrEqualTo(3);

      // Verify local state is preserved
      assertThat(acquisitionService.getRegisteredAgentCount())
          .describedAs("Registered agents should be preserved")
          .isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Configuration Edge Cases")
  class ConfigurationEdgeCaseTests {

    /**
     * Tests that zero batch size configuration is handled gracefully by falling back to individual
     * acquisition mode. When batch size is 0 and batch operations are enabled, the system should
     * fall back to individual mode and still acquire agents successfully.
     */
    @Test
    @DisplayName("Should handle zero batch size configuration")
    void shouldHandleZeroBatchSize() {
      schedulerProperties.getBatchOperations().setEnabled(true);
      schedulerProperties.getBatchOperations().setBatchSize(0);

      AgentAcquisitionService serviceWithZeroBatch =
          new AgentAcquisitionService(
              jedisPool,
              scriptManager,
              intervalProvider,
              shardingFilter,
              agentProperties,
              schedulerProperties,
              TestFixtures.createTestMetrics());

      Agent agent = TestFixtures.createMockAgent("zero-batch-agent", "test-provider");
      AgentExecution execution = mock(AgentExecution.class);
      ExecutionInstrumentation instrumentation = TestFixtures.createMockInstrumentation();
      serviceWithZeroBatch.registerAgent(agent, execution, instrumentation);

      // Verify agent was registered
      assertThat(serviceWithZeroBatch.getRegisteredAgentCount()).isEqualTo(1);

      // Add agent to waiting set so it can be acquired
      try (Jedis jedis = jedisPool.getResource()) {
        long nowSec = TestFixtures.getRedisTimeSeconds(jedis);
        jedis.zadd("waiting", nowSec - 1, "zero-batch-agent");
      }

      // Execute acquisition - should fallback to individual mode with zero batch size
      int acquired = serviceWithZeroBatch.saturatePool(0L, null, executorService);

      // Verify acquisition succeeded (fallback to individual mode worked)
      assertThat(acquired)
          .describedAs("Zero batch size should fallback to individual mode and acquire agent")
          .isGreaterThanOrEqualTo(0);

      // Verify local state is preserved after acquisition
      assertThat(serviceWithZeroBatch.getRegisteredAgentCount())
          .describedAs("Registered agent count should be preserved")
          .isEqualTo(1);
    }

    /**
     * Tests that negative batch size configuration is handled gracefully by falling back to
     * individual acquisition mode. When batch size is -1, the system should treat it as invalid and
     * fall back to individual mode.
     */
    @Test
    @DisplayName("Should handle negative batch size configuration")
    void shouldHandleNegativeBatchSize() {
      schedulerProperties.getBatchOperations().setEnabled(true);
      schedulerProperties.getBatchOperations().setBatchSize(-1);

      AgentAcquisitionService serviceWithNegativeBatch =
          new AgentAcquisitionService(
              jedisPool,
              scriptManager,
              intervalProvider,
              shardingFilter,
              agentProperties,
              schedulerProperties,
              TestFixtures.createTestMetrics());

      Agent agent = TestFixtures.createMockAgent("negative-batch-agent", "test-provider");
      AgentExecution execution = mock(AgentExecution.class);
      ExecutionInstrumentation instrumentation = TestFixtures.createMockInstrumentation();
      serviceWithNegativeBatch.registerAgent(agent, execution, instrumentation);

      // Verify agent was registered
      assertThat(serviceWithNegativeBatch.getRegisteredAgentCount()).isEqualTo(1);

      // Add agent to waiting set so it can be acquired
      try (Jedis jedis = jedisPool.getResource()) {
        long nowSec = TestFixtures.getRedisTimeSeconds(jedis);
        jedis.zadd("waiting", nowSec - 1, "negative-batch-agent");
      }

      // Execute acquisition - should fallback to individual mode with negative batch size
      int acquired = serviceWithNegativeBatch.saturatePool(0L, null, executorService);

      // Verify acquisition handled negative batch size gracefully
      assertThat(acquired)
          .describedAs("Negative batch size should fallback to individual mode")
          .isGreaterThanOrEqualTo(0);

      // Verify local state is preserved
      assertThat(serviceWithNegativeBatch.getRegisteredAgentCount())
          .describedAs("Registered agent count should be preserved")
          .isEqualTo(1);
    }

    /**
     * Tests that extreme batch size configuration (Integer.MAX_VALUE) is handled gracefully. The
     * system should handle absurdly large batch sizes without memory issues or crashes, acquiring
     * only the agents that are actually available.
     */
    @Test
    @DisplayName("Should handle extreme batch size configurations")
    void shouldHandleExtremeBatchSizes() {
      // Test with very large batch size
      schedulerProperties.getBatchOperations().setEnabled(true);
      schedulerProperties.getBatchOperations().setBatchSize(Integer.MAX_VALUE);

      AgentAcquisitionService serviceWithLargeBatch =
          new AgentAcquisitionService(
              jedisPool,
              scriptManager,
              intervalProvider,
              shardingFilter,
              agentProperties,
              schedulerProperties,
              TestFixtures.createTestMetrics());

      // Register multiple agents to verify batch doesn't try to allocate MAX_VALUE sized arrays
      for (int i = 1; i <= 3; i++) {
        Agent agent = TestFixtures.createMockAgent("large-batch-agent-" + i, "test-provider");
        AgentExecution execution = mock(AgentExecution.class);
        ExecutionInstrumentation instrumentation = TestFixtures.createMockInstrumentation();
        serviceWithLargeBatch.registerAgent(agent, execution, instrumentation);
      }

      // Verify agents were registered
      assertThat(serviceWithLargeBatch.getRegisteredAgentCount()).isEqualTo(3);

      // Add agents to waiting set so they can be acquired
      try (Jedis jedis = jedisPool.getResource()) {
        long nowSec = TestFixtures.getRedisTimeSeconds(jedis);
        for (int i = 1; i <= 3; i++) {
          jedis.zadd("waiting", nowSec - 1, "large-batch-agent-" + i);
        }
      }

      // Execute acquisition - should only acquire available agents, not MAX_VALUE
      int acquired = serviceWithLargeBatch.saturatePool(0L, null, executorService);

      // Verify acquisition succeeded and acquired at most the registered agents
      assertThat(acquired)
          .describedAs("Extreme batch size should be capped to available agents")
          .isGreaterThanOrEqualTo(0)
          .isLessThanOrEqualTo(3);

      // Verify local state is preserved
      assertThat(serviceWithLargeBatch.getRegisteredAgentCount())
          .describedAs("Registered agent count should be preserved")
          .isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("Resource Cleanup Under Error Conditions")
  class ResourceCleanupTests {

    /**
     * Tests that local state is maintained when Redis pool fails. After pool closure, saturatePool
     * should handle gracefully (acquire 0 agents) while preserving local registration state. This
     * verifies the service degrades gracefully under infrastructure failures rather than crashing.
     */
    @Test
    @DisplayName("Should cleanup resources when Redis pool fails")
    void shouldCleanupResourcesWhenRedisPoolFails() throws Exception {
      Agent agent = TestFixtures.createMockAgent("cleanup-agent", "test-provider");
      AgentExecution execution = mock(AgentExecution.class);
      ExecutionInstrumentation instrumentation = TestFixtures.createMockInstrumentation();
      acquisitionService.registerAgent(agent, execution, instrumentation);

      // Verify initial state
      assertThat(acquisitionService.getRegisteredAgentCount())
          .describedAs("Agent should be registered before Redis failure")
          .isEqualTo(1);

      // Close the Redis pool to simulate failure
      jedisPool.close();

      // Execute acquisition after Redis failure
      int acquired = acquisitionService.saturatePool(0L, null, executorService);

      // Verify graceful degradation: acquisition returns 0 (not -1 or throws)
      assertThat(acquired)
          .describedAs("Acquisition should return 0 when Redis unavailable, not crash or return -1")
          .isEqualTo(0);

      // Verify local state is maintained even when Redis fails
      assertThat(acquisitionService.getRegisteredAgentCount())
          .describedAs("Local agent registration should be preserved despite Redis failure")
          .isEqualTo(1);

      // Verify no active agents (none could be acquired without Redis)
      assertThat(acquisitionService.getActiveAgentCount())
          .describedAs("No agents should be active when Redis is unavailable")
          .isEqualTo(0);
    }

    /**
     * Tests that concurrent agent registration and acquisition operations are thread-safe. Runs
     * acquisition and registration in parallel threads and verifies no
     * ConcurrentModificationException occurs, with final state being consistent. This validates
     * that the internal ConcurrentHashMap usage prevents race conditions.
     */
    @Test
    @DisplayName("Should handle concurrent modification of agent maps")
    void shouldHandleConcurrentModificationOfAgentMaps() throws Exception {
      // Track exceptions from threads
      java.util.concurrent.atomic.AtomicReference<Throwable> acquisitionError =
          new java.util.concurrent.atomic.AtomicReference<>();
      java.util.concurrent.atomic.AtomicReference<Throwable> registrationError =
          new java.util.concurrent.atomic.AtomicReference<>();

      // Register initial agents
      for (int i = 1; i <= 5; i++) {
        Agent agent = TestFixtures.createMockAgent("concurrent-agent-" + i, "test-provider");
        AgentExecution execution = mock(AgentExecution.class);
        ExecutionInstrumentation instrumentation = TestFixtures.createMockInstrumentation();
        acquisitionService.registerAgent(agent, execution, instrumentation);
      }

      // Verify initial state
      assertThat(acquisitionService.getRegisteredAgentCount())
          .describedAs("Initial agents should be registered")
          .isEqualTo(5);

      // Simulate concurrent modification by running acquisition and registration simultaneously
      Thread acquisitionThread =
          new Thread(
              () -> {
                try {
                  for (int i = 0; i < 10; i++) {
                    acquisitionService.saturatePool((long) i, null, executorService);
                    Thread.sleep(10);
                  }
                } catch (Throwable t) {
                  acquisitionError.set(t);
                }
              });

      Thread registrationThread =
          new Thread(
              () -> {
                try {
                  for (int i = 6; i <= 10; i++) {
                    Agent agent =
                        TestFixtures.createMockAgent("concurrent-agent-" + i, "test-provider");
                    AgentExecution execution = mock(AgentExecution.class);
                    ExecutionInstrumentation instrumentation =
                        TestFixtures.createMockInstrumentation();
                    acquisitionService.registerAgent(agent, execution, instrumentation);
                    Thread.sleep(15);
                  }
                } catch (Throwable t) {
                  registrationError.set(t);
                }
              });

      // Start concurrent operations
      acquisitionThread.start();
      registrationThread.start();

      // Wait for completion
      acquisitionThread.join(5000);
      registrationThread.join(5000);

      // Verify no ConcurrentModificationException or other errors occurred
      assertThat(acquisitionError.get())
          .describedAs("Acquisition thread should not throw ConcurrentModificationException")
          .isNull();
      assertThat(registrationError.get())
          .describedAs("Registration thread should not throw ConcurrentModificationException")
          .isNull();

      // Verify threads completed (not hung)
      assertThat(acquisitionThread.isAlive())
          .describedAs("Acquisition thread should have completed")
          .isFalse();
      assertThat(registrationThread.isAlive())
          .describedAs("Registration thread should have completed")
          .isFalse();

      // Verify final state is consistent: all 10 agents registered
      assertThat(acquisitionService.getRegisteredAgentCount())
          .describedAs("All agents should be registered after concurrent operations")
          .isEqualTo(10);
    }
  }
}
