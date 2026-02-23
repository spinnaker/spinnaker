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

import com.google.common.annotations.VisibleForTesting;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Manages Redis Lua scripts for atomic scheduler operations.
 *
 * <p>Loads and caches Lua scripts for efficient execution via EVALSHA. Scripts provide atomic
 * operations for agent state transitions between waiting and working sets.
 *
 * <p>Key operations:
 *
 * <ul>
 *   <li>Agent movement between sets (waiting <-> working)
 *   <li>Batch operations for performance
 *   <li>Conditional operations with ownership validation
 *   <li>Leadership management for distributed coordination
 * </ul>
 *
 * <p>Self-heals on NOSCRIPT errors by transparently reloading scripts.
 */
@Component
@Slf4j
public class RedisScriptManager {
  private static final String EVALSHA_RETRY_FALLBACK_LOG =
      "EVALSHA retry failed for '{}' after reload; using EVAL fallback";

  // Script name constants for Redis Lua operations

  // === Basic operations ===
  public static final String ADD_AGENT = "addAgent"; // Single agent addition
  public static final String ADD_AGENTS = "addAgents"; // Batch agent addition
  public static final String REMOVE_AGENT = "removeAgent"; // Single agent removal
  public static final String RESCHEDULE_AGENT =
      "rescheduleAgent"; // Atomic completion: working→waiting transition by worker thread

  // === State transitions ===
  public static final String MOVE_AGENTS =
      "moveAgents"; // Unconditional waiting->working for agent acquisition
  public static final String MOVE_AGENTS_CONDITIONAL =
      "moveAgentsConditional"; // Conditional working->waiting with ownership verification

  // === Queries ===
  public static final String SCORE_AGENTS = "scoreAgents"; // Batch score lookup for multiple agents
  public static final String ZMSCORE_AGENTS =
      "zmscoreAgents"; // Atomic ZMSCORE for both sets; returns [1/0 per arg] ORed across sets

  // === Advanced operations ===
  public static final String ACQUIRE_AGENTS =
      "acquireAgents"; // Batch atomic waiting->working acquisition
  public static final String REMOVE_AGENTS_CONDITIONAL =
      "removeAgentsConditional"; // Conditional removal with score validation (orphan + zombie
  // cleanup)

  // === System ===
  public static final String RELEASE_LEADERSHIP =
      "releaseLeadership"; // Distributed leadership release

  private final JedisPool jedisPool;
  private final PrioritySchedulerMetrics metrics;
  private volatile Map<String, String> scriptShas = Collections.emptyMap();
  private final AtomicBoolean initialized = new AtomicBoolean(false);

  // Single source of truth for Lua bodies used by both scriptLoad and EVAL fallback
  private volatile Map<String, String> scriptBodies = Collections.emptyMap();
  private final Object scriptReloadLock = new Object();
  private final AtomicLong scriptGeneration = new AtomicLong(0L);

  /**
   * Constructs a new RedisScriptManager.
   *
   * @param jedisPool Redis connection pool for script operations
   * @param metrics Metrics collector for tracking script execution
   */
  public RedisScriptManager(JedisPool jedisPool, PrioritySchedulerMetrics metrics) {
    this.jedisPool = jedisPool;
    this.metrics = metrics != null ? metrics : PrioritySchedulerMetrics.NOOP;
  }

  /**
   * Initialize all Lua scripts by loading them into Redis and caching their SHA hashes. This method
   * is thread-safe and will only load scripts once.
   */
  public void initializeScripts() {
    if (initialized.get()) {
      return;
    }

    synchronized (this) {
      if (initialized.get()) {
        return;
      }

      try (Jedis jedis = jedisPool.getResource()) {
        loadAllScripts(jedis);
        initialized.set(true);
        log.info("Loaded {} Redis Lua scripts for PriorityAgentScheduler", scriptShas.size());
      } catch (Exception e) {
        log.error("Failed to initialize Redis scripts", e);
        throw new AgentSchedulingException("Failed to initialize Redis scripts", e);
      }
    }
  }

  /**
   * Get the SHA hash for a script name.
   *
   * @param scriptName The script name constant
   * @return The SHA hash for EVALSHA execution
   * @throws IllegalStateException if scripts are not initialized or script not found
   */
  @VisibleForTesting
  String getScriptSha(String scriptName) {
    if (scriptName == null) {
      throw new IllegalArgumentException("Script name cannot be null");
    }

    if (!initialized.get()) {
      throw new IllegalStateException("Scripts not initialized. Call initializeScripts() first.");
    }

    String sha = scriptShas.get(scriptName);
    if (sha == null) {
      throw new IllegalArgumentException("Unknown script: " + scriptName);
    }
    return sha;
  }

  /**
   * Get the number of loaded Redis Lua scripts.
   *
   * @return Number of scripts currently loaded and cached
   */
  @VisibleForTesting
  int getScriptCount() {
    return scriptShas.size();
  }

  /**
   * Execute a script via EVALSHA with automatic self-heal on NOSCRIPT.
   *
   * <p>Scripts provide atomic operations for agent state transitions. If Redis has evicted scripts
   * (failover, SCRIPT FLUSH), transparently reloads and retries to avoid disruption.
   */
  public Object evalshaWithSelfHeal(
      Jedis jedis, String scriptName, java.util.List<String> keys, java.util.List<String> args) {
    long start = System.nanoTime();
    try {
      // Fast path: execute cached script SHA
      Object result = jedis.evalsha(getScriptSha(scriptName), keys, args);
      metrics.recordScriptEval(
          scriptName, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start));
      return result;
    } catch (redis.clients.jedis.exceptions.JedisDataException e) {
      String msg = e.getMessage();
      if (msg != null && msg.contains("NOSCRIPT")) {
        long observedGeneration = scriptGeneration.get();
        try {
          reloadAllScriptsIfStale(jedis, observedGeneration, scriptName, "NOSCRIPT");
          // Retry with reloaded SHA
          long retryStart = System.nanoTime();
          Object result = jedis.evalsha(getScriptSha(scriptName), keys, args);
          metrics.recordScriptEval(
              scriptName, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - retryStart));
          return result;
        } catch (Exception retry) {
          // Fallback: execute script body directly via EVAL
          log.warn(EVALSHA_RETRY_FALLBACK_LOG, scriptName, retry);
          String body = getScriptBody(scriptName);
          if (body != null) {
            long evalStart = System.nanoTime();
            Object result = jedis.eval(body, keys, args);
            metrics.recordScriptEval(
                scriptName, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - evalStart));
            return result;
          }
        }
      }
      metrics.incrementScriptError(scriptName, e.getClass().getSimpleName());
      throw e;
    } catch (ClassCastException cce) {
      // Defensive: result type mismatch (e.g., Redis/Jedis returns a different shape)
      // Attempt one-time reload of scripts and retry this call
      log.warn(
          "Script result type mismatch for '{}' - reloading scripts (possible Redis/Jedis version issue)",
          scriptName,
          cce);
      try {
        metrics.incrementScriptResultTypeError(scriptName);
      } catch (Exception ignoreMetric) {
      }

      long observedGeneration = scriptGeneration.get();
      try {
        reloadAllScriptsIfStale(jedis, observedGeneration, scriptName, "ClassCastException");
        long retryStart = System.nanoTime();
        Object result = jedis.evalsha(getScriptSha(scriptName), keys, args);
        metrics.recordScriptEval(
            scriptName, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - retryStart));
        return result;
      } catch (Exception retry) {
        // Fall back to EVAL body
        log.warn(EVALSHA_RETRY_FALLBACK_LOG, scriptName, retry);
        String body = getScriptBody(scriptName);
        if (body != null) {
          long evalStart = System.nanoTime();
          Object result = jedis.eval(body, keys, args);
          metrics.recordScriptEval(
              scriptName, TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - evalStart));
          return result;
        }
        throw cce; // rethrow original CCE if we have no body
      }
    }
  }

  /** Returns Lua body for a script name. */
  private String getScriptBody(String scriptName) {
    return scriptBodies.get(scriptName);
  }

  /**
   * Check if scripts are initialized.
   *
   * @return true if all scripts are loaded and ready for use
   */
  public boolean isInitialized() {
    return initialized.get();
  }

  /**
   * Reloads scripts only when no other thread has already done so.
   *
   * <p>The generation check avoids duplicate reload work under concurrent NOSCRIPT/error paths.
   */
  private void reloadAllScriptsIfStale(
      Jedis jedis, long observedGeneration, String scriptName, String trigger) {
    synchronized (scriptReloadLock) {
      if (scriptGeneration.get() != observedGeneration) {
        log.debug("Skipping reload for '{}' after {} (already reloaded)", scriptName, trigger);
        return;
      }

      log.warn(
          "Reloading Redis scripts for '{}' after {} (possible Redis failover, restart, or SCRIPT FLUSH)",
          scriptName,
          trigger);
      loadAllScripts(jedis);
      metrics.incrementScriptsReload();
    }
  }

  private void loadAllScripts(Jedis jedis) {
    // Build bodies once to avoid duplication and drift. These scripts codify the agent lifecycle
    // invariants used by the Priority scheduler. Inline comments explain expected semantics.
    Map<String, String> bodies = new LinkedHashMap<>();

    // --- Individual operations ---

    // addAgent: Add single agent to the waiting set with pipeline compatibility.
    // Invariants:
    // - An agent may be in at most one of waiting or working at any time.
    // - If present in either, re-adding is a no-op (idempotent enqueue).
    // Args: KEYS[1]=working, KEYS[2]=waiting, ARGV[1]=agentName, ARGV[2]=score
    // Returns: 1 if agent added successfully, 0 if agent already exists in either set
    // Usage: Pipeline-friendly for bulk operations, individual scheduling
    bodies.put(
        ADD_AGENT,
        "-- Check if agent exists in either working or waiting set\n"
            + "local exists = redis.call('zscore', KEYS[1], ARGV[1]) or redis.call('zscore', KEYS[2], ARGV[1])\n"
            + "if not exists then\n"
            + "  -- Agent is new, add to the waiting set with provided score\n"
            + "  redis.call('zadd', KEYS[2], ARGV[2], ARGV[1])\n"
            + "  return 1  -- Success: agent added\n"
            + "else\n"
            + "  return 0  -- Already exists: no action taken\n"
            + "end\n");

    // removeAgent: Unconditionally remove agent from both working and waiting sets.
    // Invariants:
    // - Removal is idempotent; used by completion, zombie cleanup, and defensive cleanup.
    // Args: KEYS[1]=working, KEYS[2]=waiting, ARGV[1]=agentName
    // Returns: 1 (always successful - removes from both sets regardless of presence)
    // Usage: Agent completion cleanup, zombie cleanup, pipeline-friendly removal
    bodies.put(
        REMOVE_AGENT,
        "-- Remove agent from working set (may not exist)\n"
            + "redis.call('zrem', KEYS[1], ARGV[1])\n"
            + "-- Remove agent from waiting set (may not exist)\n"
            + "redis.call('zrem', KEYS[2], ARGV[1])\n"
            + "return 1  -- Always successful: Redis ZREM is idempotent\n");

    // rescheduleAgent: Atomically reschedule agent upon completion.
    // This script is called by the worker thread immediately after agent execution completes,
    // performing the working→waiting transition atomically. This eliminates the race condition
    // that existed when completion processing was deferred to the scheduler thread.
    //
    // Invariants:
    // - Atomically moves agent from working→waiting in a single Redis operation.
    // - If agent is in working set: remove from working, add to waiting with new score.
    // - If agent is in neither set: add to waiting (defensive, handles concurrent cleanup).
    // - If agent is already in waiting: no-op (already rescheduled by another path).
    // Args: KEYS[1]=working, KEYS[2]=waiting, ARGV[1]=agentName, ARGV[2]=newScore
    // Returns: 'moved' if moved from working, 'added' if added (was in neither), 'exists' if
    // already in waiting
    // Usage: Worker thread calls this in conditionalReleaseAgent() via atomicRescheduleInRedis()
    bodies.put(
        RESCHEDULE_AGENT,
        "-- Check if already in waiting set (already rescheduled, no-op)\n"
            + "local waitingScore = redis.call('zscore', KEYS[2], ARGV[1])\n"
            + "if waitingScore then\n"
            + "  return 'exists'\n"
            + "end\n"
            + "-- Check if in working set (race: completion queued before removeActiveAgent)\n"
            + "local workingScore = redis.call('zscore', KEYS[1], ARGV[1])\n"
            + "if workingScore then\n"
            + "  -- Move from working to waiting\n"
            + "  redis.call('zrem', KEYS[1], ARGV[1])\n"
            + "  redis.call('zadd', KEYS[2], ARGV[2], ARGV[1])\n"
            + "  return 'moved'\n"
            + "end\n"
            + "-- Not in either set: add to waiting\n"
            + "redis.call('zadd', KEYS[2], ARGV[2], ARGV[1])\n"
            + "return 'added'\n");

    // --- Batch operations ---

    // addAgents: Add single or multiple agents to the waiting set (consolidated from addAgent +
    // batch add).
    // Invariants:
    // - Same as addAgent but batched; returns {count, [added...]} for observability.
    // Handles both single [agent, score] and batch [agent1, score1, agent2, score2, ...] operations
    // Track added agents and the count of successful additions.
    bodies.put(
        ADD_AGENTS,
        "local added = {}\n"
            + "local count = 0\n"
            + "-- Agent scores are provided as pairs: [agent1, score1, agent2, score2, ...]\n"
            + "for i=1,#ARGV,2 do\n"
            + "  local agent = ARGV[i]\n"
            + "  local score = ARGV[i+1]\n"
            + "  -- Guards: score must be numeric and agent must not be numeric\n"
            + "  if tonumber(score) ~= nil and tonumber(agent) == nil then\n"
            + "    local exists = redis.call('zscore', KEYS[1], agent) or redis.call('zscore', KEYS[2], agent)\n"
            + "    if not exists then\n"
            + "      redis.call('zadd', KEYS[2], score, agent)\n"
            + "      table.insert(added, agent)\n"
            + "      count = count + 1\n"
            + "    end\n"
            + "  end\n"
            + "end\n"
            + "return {count, added}\n");

    // --- Agent state transition scripts ---

    // moveAgents: Unconditionally move agent waiting -> working for acquisition (single agent).
    // Invariants:
    // - Returns newScore if moved; nil if not waiting. Used by acquisition flows.
    // Args: KEYS[1]=working, KEYS[2]=waiting, ARGV[1]=agentName, ARGV[2]=newScore
    // Returns: newScore if successful, nil if agent not in the waiting set
    // Usage: Agent acquisition (waiting -> working transition)
    bodies.put(
        MOVE_AGENTS,
        "-- Attempt to remove agent from the waiting set\n"
            + "local removed = redis.call('zrem', KEYS[2], ARGV[1])\n"
            + "if removed == 1 then\n"
            + "  -- Agent was in waiting, move to working with new score\n"
            + "  redis.call('zadd', KEYS[1], ARGV[2], ARGV[1])\n"
            + "  return ARGV[2]\n"
            + "else\n"
            + "  return nil\n"
            + "end\n");

    // moveAgentsConditional: Conditionally move working -> waiting with ownership verification.
    // Invariants:
    // - Score encodes lock ownership. Only the owning scorer may requeue.
    // - Used by graceful shutdown and local zombie/orphan cleanup.
    // Args: KEYS[1]=working, KEYS[2]=waiting, ARGV[1]=agentName, ARGV[2]=expectedScore,
    // ARGV[3]=newScore
    // Returns: 'swapped' if agent moved successfully, nil if ownership verification failed
    // Usage: Graceful shutdown re-queuing, ensures only owning pod moves its agents
    bodies.put(
        MOVE_AGENTS_CONDITIONAL,
        "local score = redis.call('zscore', KEYS[1], ARGV[1])\n"
            + "if score and tonumber(score) == tonumber(ARGV[2]) then\n"
            + "  redis.call('zrem', KEYS[1], ARGV[1])\n"
            + "  redis.call('zadd', KEYS[2], ARGV[3], ARGV[1])\n"
            + "  return 'swapped'\n"
            + "else return nil end\n");

    // --- Advanced cleanup scripts ---

    // removeAgentsConditional: Remove single or multiple agents with score validation.
    // Invariants:
    // - Used for orphan cleanup (cross-instance) and zombie cleanup (local instance).
    // - Only removes entries whose scores match expectations, preventing races.
    // - Handles batch [agent1, score1, agent2, score2, ...] operations.
    // Track removed agents and the count of successful removals.
    bodies.put(
        REMOVE_AGENTS_CONDITIONAL,
        "local removed = {}\n"
            + "local count = 0\n"
            + "-- Agent scores are provided as pairs: [agent1, score1, agent2, score2, ...]\n"
            + "for i=1,#ARGV,2 do\n"
            + "  local agent = ARGV[i]\n"
            + "  local expectedScore = ARGV[i+1]\n"
            + "  local actualScore = redis.call('zscore', KEYS[1], agent)\n"
            + "  if actualScore and tonumber(actualScore) == tonumber(expectedScore) then\n"
            + "    redis.call('zrem', KEYS[1], agent)\n"
            + "    table.insert(removed, agent)\n"
            + "    count = count + 1\n"
            + "  end\n"
            + "end\n"
            + "return {count, removed}\n");

    // acquireAgents: Batch atomic acquisition waiting -> working.
    // Invariants:
    // - For each candidate, if it is still in waiting, atomically move and track acquired list.
    // - Combined with ready-scan limit, keeps acquisition O(N) per cycle.
    bodies.put(
        ACQUIRE_AGENTS,
        "local acquired = {}\n"
            + "local count = 0\n"
            + "-- Agent scores are provided as pairs: [agent1, score1, agent2, score2, ...]\n"
            + "for i=1,#ARGV,2 do\n"
            + "  local agent = ARGV[i]\n"
            + "  local newScore = ARGV[i+1]\n"
            + "  if tonumber(newScore) ~= nil then\n"
            + "    local waitingScore = redis.call('zscore', KEYS[2], agent)\n"
            + "    if waitingScore then\n"
            + "      redis.call('zrem', KEYS[2], agent)\n"
            + "      redis.call('zadd', KEYS[1], newScore, agent)\n"
            + "      table.insert(acquired, agent)\n"
            + "      count = count + 1\n"
            + "    end\n"
            + "  end\n"
            + "end\n"
            + "return {count, acquired}\n");

    // --- Query scripts ---

    // zmscoreAgents: Batch presence check across working and waiting sets.
    // Invariants:
    // - Atomically evaluates both sets within one script execution per batch
    // - Returns [1|0, 1|0, ...] aligned with ARGV order (1 if present in either set)
    // - Read-only; no state is modified
    bodies.put(
        ZMSCORE_AGENTS,
        "-- Input validation: ensure at least one agent name provided\n"
            + "if #ARGV == 0 then\n"
            + "  return {}\n"
            + "end\n"
            + "local workingScores = redis.call('zmscore', KEYS[1], unpack(ARGV))\n"
            + "local waitingScores = redis.call('zmscore', KEYS[2], unpack(ARGV))\n"
            + "local presence = {}\n"
            + "for i=1,#ARGV do\n"
            + "  local workingScore = workingScores[i]\n"
            + "  local waitingScore = waitingScores[i]\n"
            + "  if workingScore or waitingScore then\n"
            + "    table.insert(presence, 1)\n"
            + "  else\n"
            + "    table.insert(presence, 0)\n"
            + "  end\n"
            + "end\n"
            + "return presence\n");

    // scoreAgents: Batch score lookup for multiple agents.
    // Invariants:
    // - Returns [agent, workScore|'null', waitScore|'null', ...] for diagnostics/observability.
    bodies.put(
        SCORE_AGENTS,
        "-- Input validation: ensure at least one agent name provided\n"
            + "if #ARGV == 0 then\n"
            + "  return {}\n"
            + "end\n"
            + "local results = {}\n"
            + "for i=1,#ARGV do\n"
            + "  local agent = ARGV[i]\n"
            + "  local workingScore = redis.call('zscore', KEYS[1], agent)\n"
            + "  local waitingScore = redis.call('zscore', KEYS[2], agent)\n"
            + "  table.insert(results, agent)\n"
            + "  table.insert(results, workingScore or 'null')\n"
            + "  table.insert(results, waitingScore or 'null')\n"
            + "end\n"
            + "return results\n");

    // --- Leadership management ---

    // releaseLeadership: Release leadership only if we own it (atomic check-and-delete).
    // Invariants:
    // - Prevents another node from releasing a lock it does not own.
    bodies.put(
        RELEASE_LEADERSHIP,
        "if redis.call('get', KEYS[1]) == ARGV[1] then\n"
            + "  return redis.call('del', KEYS[1])\n"
            + "else\n"
            + "  return 0\n"
            + "end\n");

    // Load SHAs first, then atomically publish both maps as immutable snapshots.
    Map<String, String> loadedShas = new LinkedHashMap<>();
    for (Map.Entry<String, String> entry : bodies.entrySet()) {
      loadedShas.put(entry.getKey(), jedis.scriptLoad(entry.getValue()));
    }
    scriptBodies = Collections.unmodifiableMap(new LinkedHashMap<>(bodies));
    scriptShas = Collections.unmodifiableMap(loadedShas);
    scriptGeneration.incrementAndGet();

    log.debug("Loaded Redis Lua scripts: {}", scriptShas.keySet());
  }
}
