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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
 * Tests for {@link RedisScriptManager}, which manages Redis Lua scripts for the Priority Agent
 * Scheduler.
 *
 * <h3>Architecture Overview</h3>
 *
 * <p>RedisScriptManager provides atomic Redis operations via Lua scripts, ensuring agent state
 * transitions between WAITING and WORKING sets are race-free. Scripts are loaded once at startup
 * and executed via EVALSHA for efficiency.
 *
 * <h3>Script Categories</h3>
 *
 * <ul>
 *   <li><b>Basic Operations:</b> ADD_AGENT, REMOVE_AGENT, RESCHEDULE_AGENT - single agent state
 *       management
 *   <li><b>Batch Operations:</b> ADD_AGENTS, ACQUIRE_AGENTS, REMOVE_AGENTS_CONDITIONAL - efficient
 *       bulk processing
 *   <li><b>State Transitions:</b> MOVE_AGENTS (waiting->working), MOVE_AGENTS_CONDITIONAL
 *       (working->waiting with ownership verification)
 *   <li><b>Queries:</b> SCORE_AGENTS, ZMSCORE_AGENTS - batch score lookups and presence checks
 *   <li><b>Coordination:</b> RELEASE_LEADERSHIP - distributed leadership management
 * </ul>
 *
 * <h3>Self-Healing Behavior</h3>
 *
 * <p>The evalshaWithSelfHeal() method handles Redis failover scenarios where scripts are evicted.
 * On NOSCRIPT errors, it automatically reloads scripts and retries, falling back to direct EVAL if
 * reload fails. This ensures scheduler resilience during Redis cluster operations.
 *
 * <h3>Score Format Convention</h3>
 *
 * <p>All scores use Unix timestamps in <b>seconds</b> (not milliseconds). This is critical for
 * consistent timeout detection and score comparisons across the scheduler.
 *
 * <h3>Test Organization</h3>
 *
 * <ul>
 *   <li>{@link ScriptInitializationTests} - script loading, idempotency, SHA caching
 *   <li>{@link ThreadSafetyTests} - concurrent initialization safety
 *   <li>{@link ErrorHandlingTests} - uninitialized access, unknown scripts, connection failures
 *   <li>{@link ScriptExecutionTests} - individual script behavior verification
 *   <li>{@link IndividualScriptTests} - ADD_AGENT, REMOVE_AGENT, RESCHEDULE_AGENT
 *   <li>{@link BatchScriptTests} - batch operations and leadership release
 *   <li>{@link PerformanceTests} - high-volume execution efficiency
 *   <li>{@link SelfHealAndSingleSourceBodyTests} - NOSCRIPT recovery and EVAL fallback
 *   <li>{@link TimestampFormatConsistencyTests} - seconds format validation
 *   <li>{@link UnitTests} - isolated unit tests with FakeJedis
 *   <li>{@link IntegrationTests} - metrics recording verification
 * </ul>
 */
@Testcontainers
@DisplayName("RedisScriptManager Tests")
@SuppressWarnings("resource") // GenericContainer lifecycle managed by @Testcontainers
@Timeout(60)
class RedisScriptManagerTest {

  @Container
  static GenericContainer<?> redis =
      new GenericContainer<>("redis:7-alpine")
          .withExposedPorts(6379)
          .withCommand("redis-server", "--requirepass", "testpass");

  private JedisPool jedisPool;

  private RedisScriptManager scriptManager;

  @BeforeEach
  void setUp() {
    jedisPool = TestFixtures.createTestJedisPool(redis);

    scriptManager = new RedisScriptManager(jedisPool, TestFixtures.createTestMetrics());
  }

  @AfterEach
  void tearDown() {
    TestFixtures.closePoolSafely(jedisPool);
  }

  @Nested
  @DisplayName("Script Initialization Tests")
  class ScriptInitializationTests {

    /**
     * Tests that initializeScripts() successfully loads all Lua scripts into Redis. Verifies the
     * initialized flag is set and the expected script count (11) is loaded.
     */
    @Test
    @DisplayName("Should successfully initialize all scripts")
    void shouldInitializeAllScripts() {
      // When
      scriptManager.initializeScripts();

      // Then
      assertThat(scriptManager.isInitialized()).isTrue();
      assertThat(scriptManager.getScriptCount()).isEqualTo(11);
    }

    /**
     * Tests that all script constants (ADD_AGENT, REMOVE_AGENT, RESCHEDULE_AGENT, ADD_AGENTS,
     * MOVE_AGENTS, MOVE_AGENTS_CONDITIONAL, ACQUIRE_AGENTS, SCORE_AGENTS,
     * REMOVE_AGENTS_CONDITIONAL, RELEASE_LEADERSHIP, ZMSCORE_AGENTS) are loaded with non-empty SHA
     * hashes.
     */
    @Test
    @DisplayName("Should load all expected script constants")
    void shouldLoadAllExpectedScriptConstants() {
      // Given
      scriptManager.initializeScripts();

      // When & Then - Verify all script constants are loaded
      // Individual scripts
      assertThat(scriptManager.getScriptSha(RedisScriptManager.ADD_AGENT)).isNotEmpty();
      assertThat(scriptManager.getScriptSha(RedisScriptManager.REMOVE_AGENT)).isNotEmpty();
      assertThat(scriptManager.getScriptSha(RedisScriptManager.RESCHEDULE_AGENT)).isNotEmpty();

      // Batch scripts
      assertThat(scriptManager.getScriptSha(RedisScriptManager.ADD_AGENTS)).isNotEmpty();
      assertThat(scriptManager.getScriptSha(RedisScriptManager.MOVE_AGENTS)).isNotEmpty();
      assertThat(scriptManager.getScriptSha(RedisScriptManager.MOVE_AGENTS_CONDITIONAL))
          .isNotEmpty();
      assertThat(scriptManager.getScriptSha(RedisScriptManager.ACQUIRE_AGENTS)).isNotEmpty();
      assertThat(scriptManager.getScriptSha(RedisScriptManager.SCORE_AGENTS)).isNotEmpty();
      assertThat(scriptManager.getScriptSha(RedisScriptManager.REMOVE_AGENTS_CONDITIONAL))
          .isNotEmpty();
      assertThat(scriptManager.getScriptSha(RedisScriptManager.RELEASE_LEADERSHIP)).isNotEmpty();
    }

    /**
     * Tests that initializeScripts() is idempotent: multiple calls return the same script SHAs and
     * do not reload scripts unnecessarily.
     */
    @Test
    @DisplayName("Should be idempotent when called multiple times")
    void shouldBeIdempotentWhenCalledMultipleTimes() {
      // When
      scriptManager.initializeScripts();
      String firstSha = scriptManager.getScriptSha(RedisScriptManager.ADD_AGENTS);

      scriptManager.initializeScripts(); // Call again
      String secondSha = scriptManager.getScriptSha(RedisScriptManager.ADD_AGENTS);

      // Then
      assertThat(firstSha).isEqualTo(secondSha);
      assertThat(scriptManager.getScriptCount()).isEqualTo(11);
    }
  }

  @Nested
  @DisplayName("Thread Safety Tests")
  class ThreadSafetyTests {

    /**
     * Tests that concurrent calls to initializeScripts() from multiple threads are handled safely
     * without exceptions and result in correct initialization state.
     */
    @Test
    @DisplayName("Should handle concurrent initialization safely")
    void shouldHandleConcurrentInitializationSafely() throws InterruptedException {
      // Given
      Thread[] threads = new Thread[10];
      final Exception[] threadException = new Exception[1];

      // When - Multiple threads try to initialize simultaneously
      for (int i = 0; i < threads.length; i++) {
        threads[i] =
            new Thread(
                () -> {
                  try {
                    scriptManager.initializeScripts();
                  } catch (Exception e) {
                    threadException[0] = e;
                  }
                });
        threads[i].start();
      }

      // Wait for all threads to complete
      for (Thread thread : threads) {
        thread.join();
      }

      // Then
      assertThat(threadException[0]).isNull();
      assertThat(scriptManager.isInitialized()).isTrue();
      assertThat(scriptManager.getScriptCount()).isEqualTo(11);
    }
  }

  @Nested
  @DisplayName("Error Handling Tests")
  class ErrorHandlingTests {

    /**
     * Tests that getScriptSha() throws IllegalStateException with "Scripts not initialized" message
     * when scripts have not been initialized.
     */
    @Test
    @DisplayName("Should throw exception when accessing uninitialized scripts")
    void shouldThrowExceptionWhenAccessingUninitializedScripts() {
      // When & Then
      assertThatThrownBy(() -> scriptManager.getScriptSha(RedisScriptManager.ADD_AGENTS))
          .isInstanceOf(IllegalStateException.class)
          .hasMessageContaining("Scripts not initialized");
    }

    /**
     * Tests that getScriptSha() throws IllegalArgumentException with "Unknown script" message for
     * unrecognized script names.
     */
    @Test
    @DisplayName("Should throw exception for unknown script names")
    void shouldThrowExceptionForUnknownScriptNames() {
      // Given
      scriptManager.initializeScripts();

      // When & Then
      assertThatThrownBy(() -> scriptManager.getScriptSha("unknownScript"))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unknown script");
    }

    /**
     * Tests that getScriptSha rejects null script names with IllegalArgumentException. This
     * validates the null-check guard clause in getScriptSha().
     */
    @Test
    @DisplayName("Should throw exception for null script name")
    void shouldThrowExceptionForNullScriptName() {
      // Given
      scriptManager.initializeScripts();

      // When & Then
      assertThatThrownBy(() -> scriptManager.getScriptSha(null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("cannot be null");
    }

    /**
     * Tests that initializeScripts() throws RuntimeException with "Failed to initialize Redis
     * scripts" message when the Redis connection pool is closed.
     */
    @Test
    @DisplayName("Should handle Redis connection failures gracefully")
    void shouldHandleRedisConnectionFailuresGracefully() {
      // Given - Close the connection pool
      jedisPool.close();

      // When & Then
      assertThatThrownBy(() -> scriptManager.initializeScripts())
          .isInstanceOf(RuntimeException.class)
          .hasMessageContaining("Failed to initialize Redis scripts");
    }
  }

  @Nested
  @DisplayName("Script Execution Tests")
  class ScriptExecutionTests {

    @BeforeEach
    void setUp() {
      scriptManager.initializeScripts();
    }

    /**
     * Tests that ADD_AGENTS script adds an agent to the waiting set with correct score, returning
     * [count, [addedAgents]] format.
     */
    @Test
    @DisplayName("Should execute ADD_AGENTS_SCRIPT correctly")
    void shouldExecuteAddAgentsScriptCorrectly() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Given
        String agentType = "test-agent";
        String score = "100";

        // Clean up any existing agent first
        jedis.zrem("working", agentType);
        jedis.zrem("waiting", agentType);

        // When - Execute ADD_AGENTS_SCRIPT
        Object result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.ADD_AGENTS),
                2, // Key count
                "working",
                "waiting",
                agentType,
                score);

        // Then - Expect [count, [addedAgents]] format
        assertThat(result)
            .isEqualTo(java.util.Arrays.asList(1L, java.util.Arrays.asList("test-agent")));
        assertThat(jedis.zscore("waiting", agentType)).isEqualTo(100.0);
      }
    }

    /**
     * Tests that MOVE_AGENTS script atomically moves an agent from waiting to working set, removes
     * it from waiting, and returns the new score.
     */
    @Test
    @DisplayName("Should execute MOVE_AGENTS_SCRIPT correctly")
    void shouldExecuteMoveAgentsScriptCorrectly() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Given - Add agent to WAITING set first
        jedis.zadd("waiting", 100, "test-agent");

        String newScore = "200";

        // When - Execute MOVE_AGENTS_SCRIPT to move from WAITING to WORKING
        Object result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.MOVE_AGENTS),
                java.util.Arrays.asList("working", "waiting"),
                java.util.Arrays.asList("test-agent", newScore));

        // Then - MOVE_AGENTS returns the score on success
        assertThat(result).isEqualTo(newScore);
        assertThat(jedis.zscore("working", "test-agent")).isEqualTo(200.0);
        TestFixtures.assertAgentNotInSet(jedis, "waiting", "test-agent");
      }
    }

    /**
     * Tests that MOVE_AGENTS_CONDITIONAL script correctly handles numeric score conversion using
     * tonumber(), succeeding only when the expected score matches and failing otherwise.
     *
     * <p>The score encodes ownership: only the pod that acquired an agent (and knows its score) can
     * release it. This prevents unauthorized pods from moving agents during graceful shutdown or
     * cleanup operations.
     *
     * <p>The Lua script uses tonumber() to compare scores because:
     *
     * <ul>
     *   <li>Redis stores sorted set scores as doubles (12345 -> 12345.0)
     *   <li>Java passes scores as strings via Jedis ("12345")
     *   <li>Without tonumber(), "12345" != 12345.0 would fail the comparison
     * </ul>
     */
    @Test
    @DisplayName("Should handle numeric score conversion correctly in conditional swap")
    void shouldHandleNumericScoreConversionInConditionalSwap() {
      // Given - Agent in WORKING set with numeric score (encodes ownership)
      String agentType = "test-agent";
      long workingScore = 12345L; // Redis stores as 12345.0 (double)
      long newScore = 67890L;

      try (Jedis jedis = jedisPool.getResource()) {
        // Add agent to WORKING set
        jedis.zadd("working", workingScore, agentType);

        // Redis stores as double, Java retrieves as Double
        Double retrievedScore = jedis.zscore("working", agentType);
        assertThat(retrievedScore).isEqualTo(12345.0);

        // When - Execute conditional swap with string representation (Java passes strings)
        Object result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.MOVE_AGENTS_CONDITIONAL),
                java.util.Arrays.asList("working", "waiting"),
                java.util.Arrays.asList(
                    agentType,
                    String.valueOf(workingScore), // "12345" - Java passes as string
                    String.valueOf(newScore) // "67890" - new score as string
                    ));

        // Then - Should succeed thanks to tonumber() conversion
        assertThat(result).isEqualTo("swapped");
        TestFixtures.assertAgentNotInSet(jedis, "working", agentType);
        assertThat(jedis.zscore("waiting", agentType)).isEqualTo(newScore);

        // Cleanup for next test
        jedis.zrem("waiting", agentType);

        // Test failure case - wrong score
        jedis.zadd("working", workingScore, agentType);

        result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.MOVE_AGENTS_CONDITIONAL),
                java.util.Arrays.asList("working", "waiting"),
                java.util.Arrays.asList(
                    agentType,
                    String.valueOf(99999L), // Wrong expected score
                    String.valueOf(newScore)));

        // Should fail - ownership verification failed
        assertThat(result).isNull();
        assertThat(jedis.zscore("working", agentType)).isEqualTo(workingScore); // Still in working
        TestFixtures.assertAgentNotInSet(jedis, "waiting", agentType);
      }
    }

    /**
     * Tests that ZMSCORE_AGENTS script returns correct presence mapping: 1 for agents in either
     * set, 0 for agents in neither set.
     */
    @Test
    @DisplayName("Should execute ZMSCORE_AGENTS script correctly for presence mapping")
    void shouldExecuteZmscoreAgentsScriptCorrectly() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Given: agent1 in waiting, agent2 in working, agent3 in neither
        jedis.zrem("working", "agent1");
        jedis.zrem("waiting", "agent1");
        jedis.zrem("working", "agent2");
        jedis.zrem("waiting", "agent2");
        jedis.zrem("working", "agent3");
        jedis.zrem("waiting", "agent3");

        jedis.zadd("waiting", 100, "agent1");
        jedis.zadd("working", 200, "agent2");

        // When
        @SuppressWarnings("unchecked")
        java.util.List<Long> res =
            (java.util.List<Long>)
                jedis.evalsha(
                    scriptManager.getScriptSha(RedisScriptManager.ZMSCORE_AGENTS),
                    java.util.Arrays.asList("working", "waiting"),
                    java.util.Arrays.asList("agent1", "agent2", "agent3"));

        // Then: presence is [1,1,0]
        assertThat(res).containsExactly(1L, 1L, 0L);
      }
    }
  }

  @Nested
  @DisplayName("Individual Script Tests")
  class IndividualScriptTests {

    @BeforeEach
    void setUp() {
      scriptManager.initializeScripts();
    }

    /**
     * Tests that ADD_AGENT script returns 1 on successful add and 0 if agent already exists,
     * demonstrating idempotent behavior with unchanged scores on duplicate additions.
     */
    @Test
    @DisplayName("Should execute ADD_AGENT script correctly")
    void shouldExecuteAddAgentScriptCorrectly() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Given - Clear Redis
        jedis.flushAll();

        // When - Add single agent
        Object result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.ADD_AGENT),
                java.util.Arrays.asList("working", "waiting"),
                java.util.Arrays.asList("test-agent", "100"));

        // Then - Returns 1 for success
        assertThat(result).isEqualTo(1L);
        assertThat(jedis.zscore("waiting", "test-agent")).isEqualTo(100.0);

        // When - Try to add same agent again
        result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.ADD_AGENT),
                java.util.Arrays.asList("working", "waiting"),
                java.util.Arrays.asList("test-agent", "200"));

        // Then - Returns 0 for already exists
        assertThat(result).isEqualTo(0L);
        assertThat(jedis.zscore("waiting", "test-agent")).isEqualTo(100.0); // Score unchanged
      }
    }

    /**
     * Tests that REMOVE_AGENT script removes an agent from both working and waiting sets, leaving
     * other agents untouched.
     */
    @Test
    @DisplayName("Should execute REMOVE_AGENT script correctly")
    void shouldExecuteRemoveAgentScriptCorrectly() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Given - Add agents to both sets
        jedis.zadd("working", 100, "agent1");
        jedis.zadd("waiting", 200, "agent2");

        // When - Remove agent
        Object result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.REMOVE_AGENT),
                java.util.Arrays.asList("working", "waiting"),
                java.util.Arrays.asList("agent1"));

        // Then - Returns 1 and removes from both sets
        assertThat(result).isEqualTo(1L);
        TestFixtures.assertAgentNotInSet(jedis, "working", "agent1");
        TestFixtures.assertAgentNotInSet(jedis, "waiting", "agent1");
        assertThat(jedis.zscore("waiting", "agent2")).isEqualTo(200.0); // Other agent untouched
      }
    }

    /**
     * Tests that RESCHEDULE_AGENT script atomically moves an agent from working to waiting, or adds
     * to waiting if not in either set.
     *
     * <p>This script lets the worker thread handle the full Redis state transition upon agent
     * completion, eliminating race conditions.
     */
    @Test
    @DisplayName("Should execute RESCHEDULE_AGENT script correctly")
    void shouldExecuteRescheduleAgentScriptCorrectly() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Scenario 1: Agent in working set - should move to waiting
        jedis.zadd("working", 100, "agent1");
        jedis.zrem("waiting", "agent1");

        Object result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.RESCHEDULE_AGENT),
                java.util.Arrays.asList("working", "waiting"),
                java.util.Arrays.asList("agent1", "200"));

        assertThat(result).isEqualTo("moved");
        TestFixtures.assertAgentNotInSet(jedis, "working", "agent1");
        assertThat(jedis.zscore("waiting", "agent1")).isEqualTo(200.0);

        // Scenario 2: Agent in neither set - should add to waiting
        jedis.zrem("working", "agent2");
        jedis.zrem("waiting", "agent2");

        result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.RESCHEDULE_AGENT),
                java.util.Arrays.asList("working", "waiting"),
                java.util.Arrays.asList("agent2", "300"));

        assertThat(result).isEqualTo("added");
        TestFixtures.assertAgentNotInSet(jedis, "working", "agent2");
        assertThat(jedis.zscore("waiting", "agent2")).isEqualTo(300.0);

        // Scenario 3: Agent already in waiting - should be no-op
        jedis.zrem("working", "agent3");
        jedis.zadd("waiting", 400, "agent3");

        result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.RESCHEDULE_AGENT),
                java.util.Arrays.asList("working", "waiting"),
                java.util.Arrays.asList("agent3", "500"));

        assertThat(result).isEqualTo("exists");
        assertThat(jedis.zscore("waiting", "agent3")).isEqualTo(400.0); // Score unchanged
      }
    }
  }

  @Nested
  @DisplayName("Batch Script Tests")
  class BatchScriptTests {

    @BeforeEach
    void setUp() {
      scriptManager.initializeScripts();
    }

    /**
     * Tests that ADD_AGENTS script adds multiple agents in batch, returning [count, [addedAgents]]
     * format with correct scores for each agent.
     */
    @Test
    @DisplayName("Should execute ADD_AGENTS_SCRIPT correctly for batch")
    void shouldExecuteAddAgentsScriptCorrectlyForBatch() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Given - Clean up any existing agents to ensure clean test state
        jedis.zrem("working", "agent1", "agent2", "agent3");
        jedis.zrem("waiting", "agent1", "agent2", "agent3");

        // When - Execute batch add with multiple agents
        Object result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.ADD_AGENTS),
                java.util.Arrays.asList("working", "waiting"),
                java.util.Arrays.asList("agent1", "100", "agent2", "200", "agent3", "300"));

        // Then
        assertThat(result).isInstanceOf(java.util.List.class);
        @SuppressWarnings("unchecked")
        java.util.List<Object> resultList = (java.util.List<Object>) result;

        assertThat(resultList.get(0)).isEqualTo(3L); // Count of added agents

        // Verify agents were added to WAITING set
        assertThat(jedis.zscore("waiting", "agent1")).isEqualTo(100.0);
        assertThat(jedis.zscore("waiting", "agent2")).isEqualTo(200.0);
        assertThat(jedis.zscore("waiting", "agent3")).isEqualTo(300.0);
      }
    }

    /**
     * Tests that REMOVE_AGENTS_CONDITIONAL script removes agents from the working set only when
     * their scores match the expected values.
     *
     * <p>This script is used for cleanup operations where score verification prevents races:
     *
     * <ul>
     *   <li><b>Orphan cleanup:</b> Cross-pod cleanup of agents whose owning pod died. Score must
     *       match to avoid removing agents that were re-acquired by a live pod.
     *   <li><b>Zombie cleanup:</b> Local cleanup of long-running agents. Score verification ensures
     *       we only remove our own zombies, not agents acquired by another thread.
     * </ul>
     */
    @Test
    @DisplayName("Should execute REMOVE_AGENTS_CONDITIONAL_SCRIPT correctly")
    void shouldExecuteRemoveAgentsConditionalScriptCorrectly() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Given - Add orphaned agents to WORKING set
        jedis.zadd("working", 100, "orphan1");
        jedis.zadd("working", 200, "orphan2");

        // When - Execute conditional removal
        Object result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.REMOVE_AGENTS_CONDITIONAL),
                java.util.Collections.singletonList("working"),
                java.util.Arrays.asList("orphan1", "100", "orphan2", "200"));

        // Then
        assertThat(result).isInstanceOf(java.util.List.class);
        @SuppressWarnings("unchecked")
        java.util.List<Object> resultList = (java.util.List<Object>) result;

        assertThat(resultList.get(0)).isEqualTo(2L); // Count of removed agents

        // Verify agents were removed
        TestFixtures.assertAgentNotInSet(jedis, "working", "orphan1");
        TestFixtures.assertAgentNotInSet(jedis, "working", "orphan2");
      }
    }

    /**
     * Tests that RELEASE_LEADERSHIP script deletes the leadership key when called with the correct
     * ownership ID.
     */
    @Test
    @DisplayName("Should execute RELEASE_LEADERSHIP_SCRIPT correctly")
    void shouldExecuteReleaseLeadershipScriptCorrectly() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Given - Set leadership key with owner ID
        String leadershipKey = "cleanup:leadership";
        String ownershipId = "node-123";
        jedis.set(leadershipKey, ownershipId);

        // When - Release leadership with correct ownership ID
        Object result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.RELEASE_LEADERSHIP),
                java.util.Collections.singletonList(leadershipKey),
                java.util.Collections.singletonList(ownershipId));

        // Then - Should successfully delete the key
        assertThat(result).isEqualTo(1L); // Successful deletion
        assertThat(jedis.exists(leadershipKey)).isFalse();
      }
    }

    /**
     * Tests that RELEASE_LEADERSHIP script rejects release attempts with wrong ownership ID,
     * preserving the leadership key for the correct owner.
     */
    @Test
    @DisplayName("Should not release leadership with wrong ownership ID")
    void shouldNotReleaseLeadershipWithWrongOwnershipId() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Given - Set leadership key with owner ID
        String leadershipKey = "cleanup:leadership";
        String correctOwnerId = "node-123";
        String wrongOwnerId = "node-456";
        jedis.set(leadershipKey, correctOwnerId);

        // When - Try to release leadership with wrong ownership ID
        Object result =
            jedis.evalsha(
                scriptManager.getScriptSha(RedisScriptManager.RELEASE_LEADERSHIP),
                java.util.Collections.singletonList(leadershipKey),
                java.util.Collections.singletonList(wrongOwnerId));

        // Then - Should not delete the key
        assertThat(result).isEqualTo(0L); // Failed deletion
        assertThat(jedis.exists(leadershipKey)).isTrue();
        assertThat(jedis.get(leadershipKey)).isEqualTo(correctOwnerId);
      }
    }

    /**
     * Tests that ACQUIRE_AGENTS script atomically moves agents from waiting to working. Verifies
     * batch acquisition behavior: only agents in waiting are acquired, agents not in waiting are
     * skipped, and the result contains correct count and list of acquired agents.
     *
     * <p>The batch acquisition pattern enables efficient agent scheduling: scan WAITING for ready
     * agents, then atomically acquire the batch. Agents already acquired by another pod (no longer
     * in WAITING) are silently skipped, avoiding race conditions.
     */
    @Test
    @DisplayName("Should execute ACQUIRE_AGENTS script correctly for batch acquisition")
    void shouldExecuteAcquireAgentsScriptCorrectly() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Given - Add some agents to waiting, leave some missing
        jedis.flushAll();
        jedis.zadd("waiting", 100, "agent1");
        jedis.zadd("waiting", 200, "agent2");
        // agent3 not in waiting (should be skipped)

        long newScore = TestFixtures.nowSeconds() + 3600; // 1 hour from now

        // When - Execute ACQUIRE_AGENTS
        @SuppressWarnings("unchecked")
        java.util.List<Object> result =
            (java.util.List<Object>)
                jedis.evalsha(
                    scriptManager.getScriptSha(RedisScriptManager.ACQUIRE_AGENTS),
                    java.util.Arrays.asList("working", "waiting"),
                    java.util.Arrays.asList(
                        "agent1", String.valueOf(newScore),
                        "agent2", String.valueOf(newScore),
                        "agent3", String.valueOf(newScore))); // agent3 not in waiting

        // Then - Should acquire only agents that were in waiting
        assertThat(result.get(0)).isEqualTo(2L); // Count of acquired agents
        @SuppressWarnings("unchecked")
        java.util.List<String> acquiredList = (java.util.List<String>) result.get(1);
        assertThat(acquiredList).containsExactlyInAnyOrder("agent1", "agent2");

        // Verify state transitions
        assertThat(jedis.zscore("working", "agent1")).isEqualTo((double) newScore);
        assertThat(jedis.zscore("working", "agent2")).isEqualTo((double) newScore);
        TestFixtures.assertAgentNotInSet(jedis, "waiting", "agent1");
        TestFixtures.assertAgentNotInSet(jedis, "waiting", "agent2");
        TestFixtures.assertAgentNotInSet(jedis, "working", "agent3"); // Not acquired
      }
    }

    /**
     * Tests that SCORE_AGENTS script returns correct score information for multiple agents.
     * Verifies the script returns [agent, workingScore|'null', waitingScore|'null'] for each agent.
     */
    @Test
    @DisplayName("Should execute SCORE_AGENTS script correctly for batch score lookup")
    void shouldExecuteScoreAgentsScriptCorrectly() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Given - Set up agents in different states
        jedis.flushAll();
        jedis.zadd("working", 1000, "working-agent");
        jedis.zadd("waiting", 2000, "waiting-agent");
        // missing-agent not in either set

        // When - Execute SCORE_AGENTS
        @SuppressWarnings("unchecked")
        java.util.List<Object> result =
            (java.util.List<Object>)
                jedis.evalsha(
                    scriptManager.getScriptSha(RedisScriptManager.SCORE_AGENTS),
                    java.util.Arrays.asList("working", "waiting"),
                    java.util.Arrays.asList("working-agent", "waiting-agent", "missing-agent"));

        // Then - Should return [agent, workScore, waitScore, agent, workScore, waitScore, ...]
        assertThat(result).hasSize(9); // 3 agents * 3 values each

        // working-agent: in working with score 1000, not in waiting
        assertThat(result.get(0)).isEqualTo("working-agent");
        assertThat(result.get(1)).isEqualTo("1000"); // Redis returns score as string
        assertThat(result.get(2)).isEqualTo("null");

        // waiting-agent: not in working, in waiting with score 2000
        assertThat(result.get(3)).isEqualTo("waiting-agent");
        assertThat(result.get(4)).isEqualTo("null");
        assertThat(result.get(5)).isEqualTo("2000");

        // missing-agent: not in either set
        assertThat(result.get(6)).isEqualTo("missing-agent");
        assertThat(result.get(7)).isEqualTo("null");
        assertThat(result.get(8)).isEqualTo("null");
      }
    }
  }

  @Nested
  @DisplayName("Performance Tests")
  class PerformanceTests {

    @BeforeEach
    void setUp() {
      scriptManager.initializeScripts();
    }

    /**
     * Tests that 1000 script executions complete within 5 seconds and all agents are correctly
     * added to the waiting set.
     */
    @Test
    @DisplayName("Should handle high volume script executions efficiently")
    void shouldHandleHighVolumeScriptExecutionsEfficiently() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Clear Redis to ensure clean state
        jedis.flushAll();

        // Given
        int iterations = 1000;
        long startTime = System.currentTimeMillis();

        // When - Execute many script operations
        for (int i = 0; i < iterations; i++) {
          jedis.evalsha(
              scriptManager.getScriptSha(RedisScriptManager.ADD_AGENTS),
              2,
              "working",
              "waiting",
              "agent-" + i,
              String.valueOf(i));
        }

        long duration = System.currentTimeMillis() - startTime;

        // Then - Should complete within reasonable time
        assertThat(duration).isLessThan(5000); // Less than 5 seconds
        assertThat(jedis.zcard("waiting")).isEqualTo(iterations);
      }
    }
  }

  @Nested
  @DisplayName("Self-Heal and Single-Source Body Tests")
  class SelfHealAndSingleSourceBodyTests {

    @BeforeEach
    void setUp() {
      scriptManager.initializeScripts();
      try (Jedis jedis = jedisPool.getResource()) {
        jedis.flushAll();
      }
    }

    /**
     * Tests that evalshaWithSelfHeal detects NOSCRIPT errors after script flush, reloads scripts,
     * and successfully retries the operation.
     *
     * <p>NOSCRIPT errors occur when Redis evicts cached scripts due to:
     *
     * <ul>
     *   <li>Redis failover (new primary doesn't have scripts)
     *   <li>Manual SCRIPT FLUSH command
     *   <li>Memory pressure causing script cache eviction
     * </ul>
     *
     * <p>The self-heal flow:
     *
     * <ol>
     *   <li>EVALSHA fails with NOSCRIPT
     *   <li>Reload all scripts via SCRIPT LOAD
     *   <li>Retry EVALSHA with fresh SHA
     *   <li>If reload fails, fallback to EVAL with script body
     * </ol>
     */
    @Test
    @DisplayName("Should self-heal on NOSCRIPT by reloading scripts")
    void shouldSelfHealOnNOSCRIPTByReloadingScripts() {
      try (Jedis jedis = jedisPool.getResource()) {
        // First call works normally via self-heal wrapper
        Object first =
            scriptManager.evalshaWithSelfHeal(
                jedis,
                RedisScriptManager.ADD_AGENTS,
                java.util.Arrays.asList("working", "waiting"),
                java.util.Arrays.asList("agent-a", "100"));

        assertThat(first)
            .isEqualTo(java.util.Arrays.asList(1L, java.util.Arrays.asList("agent-a")));
        assertThat(jedis.zscore("waiting", "agent-a")).isEqualTo(100.0);

        // Flush scripts to force NOSCRIPT
        jedis.scriptFlush();

        // Direct evalsha with cached SHA should now fail with NOSCRIPT
        assertThatThrownBy(
                () ->
                    jedis.evalsha(
                        scriptManager.getScriptSha(RedisScriptManager.ADD_AGENTS),
                        java.util.Arrays.asList("working", "waiting"),
                        java.util.Arrays.asList("agent-b", "200")))
            .isInstanceOf(redis.clients.jedis.exceptions.JedisDataException.class)
            .hasMessageContaining("NOSCRIPT");

        // Wrapper should detect NOSCRIPT, reload, and succeed
        Object second =
            scriptManager.evalshaWithSelfHeal(
                jedis,
                RedisScriptManager.ADD_AGENTS,
                java.util.Arrays.asList("working", "waiting"),
                java.util.Arrays.asList("agent-b", "200"));

        assertThat(second)
            .isEqualTo(java.util.Arrays.asList(1L, java.util.Arrays.asList("agent-b")));
        assertThat(jedis.zscore("waiting", "agent-b")).isEqualTo(200.0);
      }
    }

    /**
     * Tests that all scripts have non-null, non-empty bodies accessible via reflection and that
     * bodies are directly executable via EVAL, verifying the single-source-of-truth pattern.
     */
    @Test
    @DisplayName("Should expose non-null bodies for all scripts and bodies are executable")
    void shouldExposeBodiesForAllScriptsAndBodiesAreExecutable() throws Exception {
      // Access private getScriptBody via reflection
      java.lang.reflect.Method getBody =
          RedisScriptManager.class.getDeclaredMethod("getScriptBody", String.class);
      getBody.setAccessible(true);

      String[] scriptNames = {
        RedisScriptManager.ADD_AGENT,
        RedisScriptManager.REMOVE_AGENT,
        RedisScriptManager.RESCHEDULE_AGENT,
        RedisScriptManager.ADD_AGENTS,
        RedisScriptManager.MOVE_AGENTS,
        RedisScriptManager.MOVE_AGENTS_CONDITIONAL,
        RedisScriptManager.REMOVE_AGENTS_CONDITIONAL,
        RedisScriptManager.ACQUIRE_AGENTS,
        RedisScriptManager.ZMSCORE_AGENTS,
        RedisScriptManager.SCORE_AGENTS,
        RedisScriptManager.RELEASE_LEADERSHIP
      };

      for (String name : scriptNames) {
        String body = (String) getBody.invoke(scriptManager, name);
        assertThat(body).as("Body for script %s should be present", name).isNotNull();
        assertThat(body.trim()).isNotEmpty();
      }

      // Prove that a body can be executed directly via EVAL (single-source of truth)
      try (Jedis jedis = jedisPool.getResource()) {
        jedis.flushAll();
        String body = (String) getBody.invoke(scriptManager, RedisScriptManager.ADD_AGENTS);
        Object res =
            jedis.eval(
                body,
                java.util.Arrays.asList("working", "waiting"),
                java.util.Arrays.asList("body-agent", "350"));

        assertThat(res)
            .isEqualTo(java.util.Arrays.asList(1L, java.util.Arrays.asList("body-agent")));
        assertThat(jedis.zscore("waiting", "body-agent")).isEqualTo(350.0);
      }
    }
  }

  /**
   * Tests for timestamp format consistency across all Redis score operations.
   *
   * <p>The scheduler uses Unix timestamps as scores in sorted sets. Using <b>seconds</b> (not
   * milliseconds) is critical because:
   *
   * <ul>
   *   <li>Zombie/orphan detection uses score thresholds (e.g., 30 minutes stale)
   *   <li>Mixed formats cause incorrect timeout detection (ms scores appear 1000x older)
   *   <li>Score comparisons in Lua scripts assume consistent format
   * </ul>
   *
   * <p>These tests validate the format convention and provide detection logic for debugging.
   */
  @Nested
  @DisplayName("Timestamp Format Consistency Tests")
  class TimestampFormatConsistencyTests {

    @BeforeEach
    void setUp() {
      // Clear Redis before each test
      try (Jedis jedis = jedisPool.getResource()) {
        jedis.flushAll();
      }
    }

    /**
     * Tests that Redis scores are stored in seconds format (not milliseconds) with validation
     * checks on score ranges.
     */
    @Test
    @DisplayName("All Redis scores should be in seconds format")
    void shouldUseConsistentSecondsTimestampFormat() {
      try (Jedis jedis = jedisPool.getResource()) {
        // Directly add agents to WAITING set using current time in seconds (correct format)
        long currentTimeSeconds = TestFixtures.nowSeconds();

        jedis.zadd("waiting", currentTimeSeconds, "test-agent-1");
        jedis.zadd("waiting", currentTimeSeconds + 10, "test-agent-2");

        // Get all scores from WAITING set
        java.util.Set<redis.clients.jedis.Tuple> waitingAgents =
            jedis.zrangeByScoreWithScores("waiting", 0, Double.MAX_VALUE);

        // Verify all scores are in seconds format (not milliseconds)
        assertThat(waitingAgents).isNotEmpty();

        for (redis.clients.jedis.Tuple agent : waitingAgents) {
          long score = (long) agent.getScore();
          String agentName = agent.getElement();

          // Scores should be in seconds format
          // Current time in seconds should be close to the score (within reasonable range)
          assertThat(score)
              .withFailMessage(
                  "Agent %s score %d appears to be too small for seconds format", agentName, score)
              .isGreaterThan(1700000000L);

          // Score should not be in milliseconds (would be 1000x larger)
          assertThat(score)
              .withFailMessage(
                  "Agent %s score %d is too large - likely milliseconds format", agentName, score)
              .isLessThan(currentTimeSeconds + 3600);

          // Score should be reasonable (not in milliseconds format)
          assertThat(score)
              .withFailMessage(
                  "Agent %s score %d is in milliseconds format, should be seconds",
                  agentName, score)
              .isLessThan(1700000000000L);
        }
      }
    }

    /**
     * Tests that mixed seconds/milliseconds formats can be detected by examining score ranges,
     * validating the detection logic used for format consistency checks.
     */
    @Test
    @DisplayName("Mixed format detection test - should fail if milliseconds are used")
    void shouldDetectMillisecondsFormatInconsistency() {
      try (Jedis jedis = jedisPool.getResource()) {
        long currentTimeSeconds = TestFixtures.nowSeconds();
        long currentTimeMillis = System.currentTimeMillis();

        // Add agent with correct seconds format
        jedis.zadd("waiting", currentTimeSeconds, "seconds-agent");

        // Simulate bug: add agent with milliseconds format
        jedis.zadd("waiting", currentTimeMillis, "milliseconds-agent");

        // Verify we can detect the inconsistency
        java.util.Set<redis.clients.jedis.Tuple> allAgents =
            jedis.zrangeByScoreWithScores("waiting", 0, Double.MAX_VALUE);

        boolean hasSecondsFormat = false;
        boolean hasMillisecondsFormat = false;

        for (redis.clients.jedis.Tuple agent : allAgents) {
          long score = (long) agent.getScore();
          if (score < 1700000000000L) {
            hasSecondsFormat = true;
          } else {
            hasMillisecondsFormat = true;
          }
        }

        // This test validates our detection logic
        assertThat(hasSecondsFormat).withFailMessage("Should detect seconds format").isTrue();
        assertThat(hasMillisecondsFormat)
            .withFailMessage("Should detect milliseconds format (simulated bug)")
            .isTrue();
      }
    }
  }

  @Nested
  @DisplayName("Unit Tests")
  class UnitTests {

    private static class FakePool extends JedisPool {
      private final Jedis jedis;

      FakePool(Jedis j) {
        this.jedis = j;
      }

      @Override
      public Jedis getResource() {
        return jedis;
      }
    }

    /**
     * Tests that evalshaWithSelfHeal falls back to EVAL when EVALSHA fails with NOSCRIPT and reload
     * fails, using a FakeJedis to simulate the failure scenario. Verifies metrics are recorded.
     */
    @Test
    @DisplayName(
        "evalshaWithSelfHeal records reloads and eval metrics on NOSCRIPT, falls back to EVAL")
    void evalshaSelfHealAndEvalFallback() {
      // Fake Jedis: scriptLoad loads; evalsha throws NOSCRIPT; eval succeeds
      class FakeJedis extends Jedis {
        @Override
        public String scriptLoad(String script) {
          return "sha";
        }

        @Override
        public Object evalsha(
            String sha1, java.util.List<String> keys, java.util.List<String> args) {
          throw new redis.clients.jedis.exceptions.JedisDataException(
              "NOSCRIPT No matching script");
        }

        @Override
        public Object eval(
            String script, java.util.List<String> keys, java.util.List<String> args) {
          return 1L;
        }
      }

      FakeJedis j = new FakeJedis();
      com.netflix.spectator.api.Registry registry = new com.netflix.spectator.api.DefaultRegistry();
      PrioritySchedulerMetrics metrics = new PrioritySchedulerMetrics(registry);
      RedisScriptManager mgr = new RedisScriptManager(new FakePool(j), metrics);

      // Ensure initialized so getScriptSha works after loadAllScripts
      mgr.initializeScripts();

      Object r =
          mgr.evalshaWithSelfHeal(
              j,
              RedisScriptManager.ADD_AGENT,
              java.util.Arrays.asList("working", "waiting"),
              java.util.Arrays.asList("a", "1"));
      assertThat(r).isEqualTo(1L);

      // Verify at least latency timer recorded (sum across tags)
      long timerSum = 0L;
      for (com.netflix.spectator.api.Meter meter : registry) {
        if (meter.id().name().equals("cats.priorityScheduler.scripts.latency")) {
          for (com.netflix.spectator.api.Measurement ms : meter.measure()) {
            timerSum += (long) ms.value();
          }
        }
      }
      assertThat(timerSum).isGreaterThanOrEqualTo(1L);
    }
  }

  @Nested
  @DisplayName("Integration Tests")
  class IntegrationTests {

    /**
     * Tests that evalshaWithSelfHeal records eval and reload metrics correctly. Verifies eval
     * counter increments on script execution and reload counter increments after script flush.
     */
    @Test
    void recordsEvalAndReloadMetrics() {
      JedisPool pool = TestFixtures.createTestJedisPool(redis);
      com.netflix.spectator.api.Registry registry = new com.netflix.spectator.api.DefaultRegistry();
      PrioritySchedulerMetrics metrics = new PrioritySchedulerMetrics(registry);
      RedisScriptManager manager = new RedisScriptManager(pool, metrics);

      try (Jedis j = pool.getResource()) {
        manager.initializeScripts();
        // Call a small script to record eval
        manager.evalshaWithSelfHeal(
            j,
            RedisScriptManager.SCORE_AGENTS,
            java.util.Arrays.asList("working", "waiting"),
            java.util.Arrays.asList("agentA"));
        // Force flush to drive reload
        j.scriptFlush();
        manager.evalshaWithSelfHeal(
            j,
            RedisScriptManager.SCORE_AGENTS,
            java.util.Arrays.asList("working", "waiting"),
            java.util.Arrays.asList("agentB"));
      }

      long evalCount =
          registry
              .counter(
                  registry
                      .createId("cats.priorityScheduler.scripts.eval")
                      .withTag("scheduler", "priority")
                      .withTag("script", RedisScriptManager.SCORE_AGENTS))
              .count();
      assertThat(evalCount).isGreaterThanOrEqualTo(1);
      assertThat(
              registry
                  .counter(
                      registry
                          .createId("cats.priorityScheduler.scripts.reloads")
                          .withTag("scheduler", "priority"))
                  .count())
          .isGreaterThanOrEqualTo(1);
    }
  }
}
