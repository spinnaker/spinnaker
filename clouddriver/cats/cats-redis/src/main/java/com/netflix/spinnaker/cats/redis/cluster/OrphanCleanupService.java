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

import static com.netflix.spinnaker.cats.redis.cluster.support.CadenceGuard.*;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.redis.cluster.support.ScriptResults;
import java.net.InetAddress;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.params.SetParams;
import redis.clients.jedis.resps.Tuple;

/**
 * Service that detects and cleans up orphaned agents from crashed instances.
 *
 * <p>Orphans are agents left in Redis after their owning pod crashes. Unlike zombies (locally stuck
 * agents), orphans have no running instance. The service uses leader election to coordinate cleanup
 * across pods, preventing duplicate work.
 *
 * <p>Key rules: preserve valid waiting agents; skip locally active agents (zombie cleaner handles
 * those); move valid working orphans back to waiting.
 *
 * <p><b>Scope:</b> Agents from other pods only (no local RunState). Manipulates Redis and
 * activeAgents map; <b>never</b> touches permits. See {@link ZombieCleanupService} for locally
 * stuck agents.
 */
@Component
@Slf4j
public class OrphanCleanupService {

  // Redis key names derived from configuration
  private final String WORKING_SET;
  private final String WAITING_SET;
  private final String CLEANUP_LEADER_KEY;

  private final JedisPool jedisPool;
  private final RedisScriptManager scriptManager;
  private final PrioritySchedulerProperties schedulerProperties;
  private final PrioritySchedulerMetrics metrics;
  private final LongAdder orphansCleanedUp = new LongAdder();

  // Reference to access agent state for orphan identification
  private AgentAcquisitionService acquisitionService;

  // Leadership tracking
  private volatile String currentLeadershipId = null;
  private volatile long lastOrphanCleanup = 0;

  // Design decision: ThreadLocal proliferation (intentional performance optimization)
  // Reusable containers reduce GC pressure in cleanup hot paths. Safe because cleanup runs in a
  // single-threaded executor with proper finally-block clearing. See AgentAcquisitionService for
  // detailed rationale.
  private static final ThreadLocal<java.util.List<String>> REUSABLE_INVALID_ARGS =
      ThreadLocal.withInitial(java.util.ArrayList::new);
  private static final ThreadLocal<java.util.List<String>> REUSABLE_ATTEMPTED_INVALID =
      ThreadLocal.withInitial(java.util.ArrayList::new);
  private static final ThreadLocal<java.util.List<Tuple>> REUSABLE_ORPHAN_LIST =
      ThreadLocal.withInitial(java.util.ArrayList::new);
  private static final ThreadLocal<java.util.Set<String>> REUSABLE_STRING_SET =
      ThreadLocal.withInitial(java.util.HashSet::new);

  /**
   * Constructs a new OrphanCleanupService.
   *
   * @param jedisPool Redis connection pool for cleanup operations
   * @param scriptManager Lua script manager for atomic Redis operations
   * @param schedulerProperties Configuration for cleanup behavior
   * @param metrics Metrics collector for tracking cleanup operations
   */
  public OrphanCleanupService(
      JedisPool jedisPool,
      RedisScriptManager scriptManager,
      PrioritySchedulerProperties schedulerProperties,
      PrioritySchedulerMetrics metrics) {
    this.jedisPool = jedisPool;
    this.scriptManager = scriptManager;
    this.schedulerProperties = schedulerProperties;
    this.metrics = metrics != null ? metrics : PrioritySchedulerMetrics.NOOP;

    PrioritySchedulerProperties.Keys keysCfg = schedulerProperties.getKeys();
    String hash = keysCfg.getHashTag();
    String brace = (hash != null && !hash.isEmpty()) ? ("{" + hash + "}") : "";
    String prefix = keysCfg.getPrefix() != null ? keysCfg.getPrefix() : "";
    this.WAITING_SET = prefix + keysCfg.getWaitingSet() + brace;
    this.WORKING_SET = prefix + keysCfg.getWorkingSet() + brace;
    this.CLEANUP_LEADER_KEY = prefix + keysCfg.getCleanupLeaderKey() + brace;
  }

  /**
   * Set reference to AgentAcquisitionService for advanced orphan processing. This provides access
   * to agent registry and active agent cleanup.
   *
   * @param acquisitionService the acquisition service for agent state coordination
   */
  public void setAcquisitionService(AgentAcquisitionService acquisitionService) {
    this.acquisitionService = acquisitionService;
  }

  /**
   * Remove ThreadLocal buffers held by the current thread to release per-thread memory. Intended to
   * be invoked on the owning executor thread during shutdown.
   */
  @VisibleForTesting
  void removeThreadLocals() {
    try {
      REUSABLE_INVALID_ARGS.remove();
    } catch (Exception ignore) {
      // Best-effort – buffers may already be cleared/GC'd
    }
    try {
      REUSABLE_ATTEMPTED_INVALID.remove();
    } catch (Exception ignore) {
      // Best-effort – buffers may already be cleared/GC'd
    }
    try {
      REUSABLE_ORPHAN_LIST.remove();
    } catch (Exception ignore) {
      // Best-effort – buffers may already be cleared/GC'd
    }
    try {
      REUSABLE_STRING_SET.remove();
    } catch (Exception ignore) {
      // Best-effort – buffers may already be cleared/GC'd
    }
  }

  /** Cleanup orphaned agents if needed, with configurable intervals and leadership coordination. */
  public void cleanupOrphanedAgentsIfNeeded() {
    long start = nowMs();
    if (!schedulerProperties.getOrphanCleanup().isEnabled()) {
      return;
    }

    // Check if enough time has passed since last cleanup
    long intervalMs = schedulerProperties.getOrphanCleanup().getIntervalMs();
    if (!isPeriodElapsed(lastOrphanCleanup, intervalMs)) {
      long remaining = intervalMs - (nowMs() - lastOrphanCleanup);
      if (log.isDebugEnabled()) {
        log.debug(
            "Skipping orphan cleanup - interval not elapsed ({}ms remaining, interval={}ms)",
            remaining,
            intervalMs);
      }
      return;
    }

    // Leadership: forceAllPods only skips election; it does NOT bypass shard gating anywhere.
    // Use leadership election to prevent multiple instances from running cleanup simultaneously
    boolean forceCleanup = schedulerProperties.getOrphanCleanup().isForceAllPods();
    if (!forceCleanup && !tryAcquireCleanupLeadership()) {
      if (log.isDebugEnabled()) {
        log.debug("Skipping orphan cleanup - leadership held by another instance");
      }
      return;
    }

    if (log.isDebugEnabled()) {
      log.debug(
          "Starting orphan cleanup cycle (forced={}, budget={}ms)",
          forceCleanup,
          schedulerProperties.getOrphanCleanup().getRunBudgetMs());
    }

    Jedis jedis;
    try {
      jedis = jedisPool.getResource();
    } catch (Exception e) {
      log.error("Failed to acquire Redis resource during orphan cleanup", e);
      // Release leadership when Redis acquisition fails before cleanup enters try/finally.
      if (!forceCleanup) {
        releaseCleanupLeadership();
      }
      return;
    }

    try (Jedis closeableJedis = jedis) {
      // Advance cadence only after Redis resource acquisition succeeds.
      lastOrphanCleanup = nowMs();
      final long budgetMs = schedulerProperties.getOrphanCleanup().getRunBudgetMs();

      int workingCleaned =
          cleanupOrphanedAgentsFromSet(closeableJedis, WORKING_SET, start, budgetMs);
      long workingElapsedMs = nowMs() - start;
      if (overBudget(start, budgetMs)) {
        log.info(
            "Orphan cleanup exceeded budget {}ms (elapsed={}ms); skipping waiting set (cleaned={})",
            budgetMs,
            workingElapsedMs,
            workingCleaned);
        return;
      }
      int waitingCleaned =
          cleanupOrphanedAgentsFromSet(closeableJedis, WAITING_SET, start, budgetMs);
      int totalCleaned = workingCleaned + waitingCleaned;
      long totalElapsedMs = nowMs() - start;

      if (totalCleaned > 0) {
        orphansCleanedUp.add(totalCleaned);
        log.info(
            "Orphan cleanup cycle completed: {} agents cleaned ({} from working, {} from waiting, elapsed={}ms, budget={}ms)",
            totalCleaned,
            workingCleaned,
            waitingCleaned,
            totalElapsedMs,
            budgetMs);
      } else if (log.isDebugEnabled()) {
        log.debug(
            "Orphan cleanup cycle completed: 0 agents cleaned (elapsed={}ms, budget={}ms)",
            totalElapsedMs,
            budgetMs);
      }
      metrics.recordCleanupTime("orphan", nowMs() - start);
      metrics.incrementCleanupCleaned("orphan", totalCleaned);
    } catch (Exception e) {
      log.error("Error during orphan cleanup", e);
    } finally {
      // Release leadership if we acquired it (but not if forced cleanup)
      if (!forceCleanup) {
        releaseCleanupLeadership();
      }
    }
  }

  /**
   * Force cleanup of orphaned agents immediately, bypassing interval checks. This method is
   * primarily intended for testing purposes.
   *
   * @return Total number of orphaned agents cleaned up
   */
  public int forceCleanupOrphanedAgents() {
    if (!schedulerProperties.getOrphanCleanup().isEnabled()) {
      return 0;
    }

    try (Jedis jedis = jedisPool.getResource()) {
      long start = nowMs();
      final long budgetMs = schedulerProperties.getOrphanCleanup().getRunBudgetMs();
      int workingCleaned = cleanupOrphanedAgentsFromSet(jedis, WORKING_SET, start, budgetMs);
      int waitingCleaned = cleanupOrphanedAgentsFromSet(jedis, WAITING_SET, start, budgetMs);
      int totalCleaned = workingCleaned + waitingCleaned;

      if (totalCleaned > 0) {
        orphansCleanedUp.add(totalCleaned);
        log.info(
            "Forced orphan cleanup completed: {} agents cleaned ({} from working, {} from waiting)",
            totalCleaned,
            workingCleaned,
            waitingCleaned);
      } else if (log.isDebugEnabled()) {
        log.debug("Forced orphan cleanup completed: 0 agents cleaned");
      }
      // Update the last cleanup timestamp
      lastOrphanCleanup = nowMs();
      // Record metrics (consistent with cleanupOrphanedAgentsIfNeeded)
      metrics.recordCleanupTime("orphan", nowMs() - start);
      metrics.incrementCleanupCleaned("orphan", totalCleaned);
      return totalCleaned;
    } catch (Exception e) {
      log.error("Error during forced orphan cleanup", e);
      return 0;
    }
  }

  /**
   * Get the total number of orphaned agents cleaned up since startup.
   *
   * @return total orphans cleaned up
   */
  @VisibleForTesting
  long getOrphansCleanedUp() {
    return orphansCleanedUp.sum();
  }

  /**
   * Get the timestamp of the last orphan cleanup operation.
   *
   * @return last cleanup timestamp in milliseconds
   */
  @VisibleForTesting
  long getLastOrphanCleanup() {
    return lastOrphanCleanup;
  }

  /**
   * Clean up orphaned agents from the specified Redis set with built-in batch processing and
   * fallback mechanism.
   *
   * @param jedis The Jedis connection to the Redis server
   * @param setName The name of the Redis set to clean up
   * @param startEpochMs Epoch time when cleanup operation started (for budget checking)
   * @param budgetMs Maximum runtime budget in milliseconds (0 = disabled)
   * @return The number of orphaned agents cleaned up
   */
  private int cleanupOrphanedAgentsFromSet(
      Jedis jedis, String setName, long startEpochMs, long budgetMs) {
    long cutoffScore;
    long cutoffNowMs = nowMsForRedisCutoff();

    if (WAITING_SET.equals(setName)) {
      // Waiting set: score = next execution time.
      // Orphan candidates are entries whose ready-time is older than (now - threshold).
      long orphanThreshold = schedulerProperties.getOrphanCleanup().getThresholdMs();
      cutoffScore = (cutoffNowMs - orphanThreshold) / 1000;
    } else {
      // Working set: score = completion deadline (acquire_time + timeout).
      // Orphan candidates are entries whose deadline is older than (now - threshold).
      long orphanThreshold = schedulerProperties.getOrphanCleanup().getThresholdMs();
      cutoffScore = (cutoffNowMs - orphanThreshold) / 1000;
    }

    try {
      int totalCleaned = 0;
      int totalScanned = 0;

      final int configuredBatch = schedulerProperties.getBatchOperations().getBatchSize();

      if (configuredBatch <= 0) {
        // Unbounded mode: retain legacy behavior for small environments.
        // We fetch the full eligible range and process it. This maximizes simplicity and
        // avoids pagination overhead when the candidate set is small.
        int passNumber = 0;
        while (true) {
          if (Thread.currentThread().isInterrupted()) {
            log.warn(
                "Stopping {} orphan scan due to interrupt (pass={}, cleaned={}, scanned={})",
                setName,
                passNumber,
                totalCleaned,
                totalScanned);
            break;
          }
          long elapsedMs = nowMs() - startEpochMs;
          if (overBudget(startEpochMs, budgetMs)) {
            log.info(
                "Stopping {} orphan scan due to budget deadline (pass={}, cleaned={}, scanned={}, elapsed={}ms, budget={}ms)",
                setName,
                passNumber,
                totalCleaned,
                totalScanned,
                elapsedMs,
                budgetMs);
            break;
          }

          List<Tuple> all = jedis.zrangeByScoreWithScores(setName, 0, cutoffScore);
          if (all == null || all.isEmpty()) {
            if (log.isDebugEnabled()) {
              log.debug(
                  "Orphan scan ({}/{}): {} set analyzed, 0 candidates found (total_scanned={}, total_cleaned={})",
                  setName,
                  passNumber,
                  totalScanned,
                  totalCleaned);
            }
            break;
          }
          totalScanned += all.size();
          passNumber++;

          java.util.List<Tuple> orphanList = REUSABLE_ORPHAN_LIST.get();
          int cleanedThisPass = 0;
          try {
            orphanList.clear();
            orphanList.addAll(all);
            cleanedThisPass =
                processOrphanBatch(jedis, setName, orphanList, startEpochMs, budgetMs);
          } finally {
            orphanList.clear();
            if (orphanList instanceof java.util.ArrayList) {
              ((java.util.ArrayList<?>) orphanList).trimToSize();
            }
          }

          totalCleaned += cleanedThisPass;
          if (cleanedThisPass == 0) {
            if (log.isDebugEnabled()) {
              log.debug(
                  "Orphan scan ({}/{}): {} set made no progress this pass; exiting early before budget is exhausted (total_scanned={}, total_cleaned={})",
                  setName,
                  passNumber,
                  totalScanned,
                  totalCleaned);
            }
            break;
          }
        }

        // Per-set completion log removed - final summary below provides breakdown by set
        return totalCleaned;
      }

      // Paged mode (batch-size > 0): cap per-iteration allocation and work in bounded windows.
      // pageSize comes from batch-operations.batch-size to align with existing batching knobs.
      final int pageSize = configuredBatch; // > 0 by guard above

      if (WORKING_SET.equals(setName)) {
        // Working set: head-advancing paging.
        // Safe to mutate as we go because we always read from the head (LIMIT 0,pageSize).
        // Moving/removing entries advances the head naturally; we won't skip eligible items.
        int pageNumber = 0;
        while (true) {
          if (Thread.currentThread().isInterrupted()) {
            log.warn(
                "Stopping {} orphan scan due to interrupt (page={}, cleaned={}, scanned={})",
                setName,
                pageNumber,
                totalCleaned,
                totalScanned);
            break;
          }
          long elapsedMs = nowMs() - startEpochMs;
          if (overBudget(startEpochMs, budgetMs)) {
            log.info(
                "Stopping {} orphan scan due to budget deadline (page={}, cleaned={}, scanned={}, elapsed={}ms, budget={}ms)",
                setName,
                pageNumber,
                totalCleaned,
                totalScanned,
                elapsedMs,
                budgetMs);
            break;
          }

          List<Tuple> page = jedis.zrangeByScoreWithScores(setName, 0, cutoffScore, 0, pageSize);
          if (page == null || page.isEmpty()) {
            if (log.isDebugEnabled()) {
              log.debug(
                  "Orphan scan ({}/{}): {} page empty (head), stopping (total_scanned={}, total_cleaned={})",
                  setName,
                  pageNumber,
                  setName,
                  totalScanned,
                  totalCleaned);
            }
            break;
          }
          totalScanned += page.size();
          pageNumber++;

          java.util.List<Tuple> orphanList = REUSABLE_ORPHAN_LIST.get();
          int cleanedThisPass = 0;
          try {
            orphanList.clear();
            orphanList.addAll(page);
            cleanedThisPass =
                processOrphanBatch(jedis, setName, orphanList, startEpochMs, budgetMs);
          } finally {
            orphanList.clear();
            if (orphanList instanceof java.util.ArrayList) {
              ((java.util.ArrayList<?>) orphanList).trimToSize();
            }
          }
          totalCleaned += cleanedThisPass;

          // If no progress, do not spin – deeper pages will have >= scores; exit early
          if (cleanedThisPass == 0) {
            if (log.isDebugEnabled()) {
              log.debug(
                  "Orphan scan ({}/{}): {} set made no progress this pass; exiting early before budget is exhausted (total_scanned={}, total_cleaned={})",
                  setName,
                  pageNumber,
                  totalScanned,
                  totalCleaned);
            }
            break;
          }
        }
        // Per-set completion log removed - final summary provides breakdown by set
        return totalCleaned;
      } else {
        // Waiting set: two-phase per page.
        // 1) Enumerate a page with OFFSET/COUNT without mutating the set, collecting invalid
        //    candidates. This keeps OFFSET stable within the window despite concurrent writers.
        // 2) Mutate (batch remove) only after enumeration; then advance OFFSET by
        //    (pageCount - removedInWindow). If we removed everything, keep OFFSET to re-check
        //    the new head (prevents skipping deeper candidates behind a run of valid ones).
        int offset = 0;
        int pageNumber = 0;
        while (true) {
          if (Thread.currentThread().isInterrupted()) {
            log.warn(
                "Stopping {} orphan scan due to interrupt (page={}, offset={}, cleaned={}, scanned={})",
                setName,
                pageNumber,
                offset,
                totalCleaned,
                totalScanned);
            break;
          }
          long elapsedMs = nowMs() - startEpochMs;
          if (overBudget(startEpochMs, budgetMs)) {
            log.info(
                "Stopping {} orphan scan due to budget deadline (page={}, offset={}, cleaned={}, scanned={}, elapsed={}ms, budget={}ms)",
                setName,
                pageNumber,
                offset,
                totalCleaned,
                totalScanned,
                elapsedMs,
                budgetMs);
            break;
          }

          List<Tuple> page =
              jedis.zrangeByScoreWithScores(setName, 0, cutoffScore, offset, pageSize);
          if (page == null || page.isEmpty()) {
            if (log.isDebugEnabled()) {
              log.debug(
                  "Orphan scan ({}/{}): {} page empty at offset {} (total_scanned={}, total_cleaned={})",
                  setName,
                  pageNumber,
                  offset,
                  totalScanned,
                  totalCleaned);
            }
            break;
          }
          totalScanned += page.size();
          pageNumber++;

          java.util.List<Tuple> orphanList = REUSABLE_ORPHAN_LIST.get();
          int cleanedThisWindow = 0;
          try {
            orphanList.clear();
            orphanList.addAll(page);
            cleanedThisWindow =
                processOrphanBatch(jedis, setName, orphanList, startEpochMs, budgetMs);
          } finally {
            orphanList.clear();
            if (orphanList instanceof java.util.ArrayList) {
              ((java.util.ArrayList<?>) orphanList).trimToSize();
            }
          }

          totalCleaned += cleanedThisWindow;

          int pageCount = page.size();
          if (pageCount < pageSize) {
            break; // end of eligible range
          }

          int advance = pageCount - Math.max(0, cleanedThisWindow);
          // If we removed everything in this window, keep offset (0 advance) to re-check the
          // new head; otherwise, skip over the survivors we enumerated in this window.
          if (advance > 0) {
            offset += advance;
          }
        }
        // Per-set completion log removed - final summary provides breakdown by set
        return totalCleaned;
      }

    } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
      log.warn("Redis connection error while scanning {} for orphans", setName, e);
      return 0;
    } catch (Exception e) {
      log.error("Error cleaning orphaned agents from {} set", setName, e);
      return 0;
    }
  }

  /**
   * Process orphaned agents with batch operations and built-in fallback to individual cleanup.
   *
   * @param jedis The Jedis connection to the Redis server
   * @param setName The name of the Redis set to clean up
   * @param orphans List of orphaned agents to process
   * @param startEpochMs Epoch time when cleanup operation started (for budget checking)
   * @param budgetMs Maximum runtime budget in milliseconds (0 = disabled)
   * @return The number of orphaned agents cleaned up
   */
  private int processOrphanBatch(
      Jedis jedis, String setName, List<Tuple> orphans, long startEpochMs, long budgetMs) {
    if (orphans.isEmpty()) {
      return 0;
    }

    int batchSize = schedulerProperties.getBatchOperations().getBatchSize();
    if (batchSize <= 0) {
      // Simple, non-magic fallback: process up to the current number of candidates.
      batchSize = Math.max(1, orphans.size());
    }
    boolean batchOperationsEnabled = schedulerProperties.getBatchOperations().isEnabled();
    int totalCleaned = 0;

    if (WAITING_SET.equals(setName)) {
      // Critical: Never purge valid waiting by age. Batch-remove only invalid entries.
      if (batchOperationsEnabled) {
        java.util.List<String> invalidArgs = REUSABLE_INVALID_ARGS.get();
        java.util.List<String> attemptedInvalid = REUSABLE_ATTEMPTED_INVALID.get();
        try {
          invalidArgs.clear();
          attemptedInvalid.clear();
          int scannedCount = 0;
          for (Tuple orphan : orphans) {
            scannedCount++;
            if (Thread.currentThread().isInterrupted()) {
              log.warn(
                  "Stopping waiting-batch build due to interrupt (invalid={}, scanned={})",
                  attemptedInvalid.size(),
                  scannedCount);
              break;
            }
            long elapsedMs = nowMs() - startEpochMs;
            if (overBudget(startEpochMs, budgetMs)) {
              log.info(
                  "Stopping waiting-batch build due to budget deadline (invalid={}, scanned={}, elapsed={}ms, budget={}ms)",
                  attemptedInvalid.size(),
                  scannedCount,
                  elapsedMs,
                  budgetMs);
              break;
            }
            String agentName = orphan.getElement();
            boolean removeNumericOnly =
                schedulerProperties.getOrphanCleanup().isRemoveNumericOnlyAgents();
            boolean numericOnly =
                removeNumericOnly && agentName != null && agentName.matches("^\\d{9,11}$");
            // Treat epoch-length numeric-only members as corruption (member mistaken for score)
            // when the admin flag is enabled. Otherwise only remove invalid entries.
            if (numericOnly || !isAgentStillValid(agentName)) {
              // Shard-aware gating: Only remove invalid entries owned by this shard
              boolean belongsToThisShard;
              if (acquisitionService == null) {
                belongsToThisShard = true;
              } else {
                try {
                  belongsToThisShard = acquisitionService.belongsToThisShard(agentName);
                } catch (Exception e) {
                  belongsToThisShard = false; // fail-safe preserve
                }
              }

              if (belongsToThisShard) {
                invalidArgs.add(agentName);
                invalidArgs.add(String.valueOf((long) orphan.getScore()));
                attemptedInvalid.add(agentName);
              }
            }
          }
          if (!invalidArgs.isEmpty()) {
            try {
              Object result =
                  scriptManager.evalshaWithSelfHeal(
                      jedis,
                      RedisScriptManager.REMOVE_AGENTS_CONDITIONAL,
                      java.util.Collections.singletonList(WAITING_SET),
                      invalidArgs);
              ScriptResults.BatchRemovalResult parsed =
                  ScriptResults.parseRemoveAgentsConditional(result);
              totalCleaned += parsed.getRemovedCount();
              // Per-item fallback for any attempted invalid entries not removed by batch (partial
              // success)
              if (parsed.getRemovedCount() < attemptedInvalid.size()) {
                java.util.Set<String> removedSet = REUSABLE_STRING_SET.get();
                try {
                  removedSet.clear();
                  removedSet.addAll(parsed.getMembers());
                  for (String agentName : attemptedInvalid) {
                    if (Thread.currentThread().isInterrupted()) {
                      log.warn(
                          "Stopping per-item fallback due to interrupt (processed={}, remaining={})",
                          attemptedInvalid.size() - attemptedInvalid.indexOf(agentName),
                          attemptedInvalid.indexOf(agentName));
                      break;
                    }
                    long elapsedMs = nowMs() - startEpochMs;
                    if (overBudget(startEpochMs, budgetMs)) {
                      log.info(
                          "Stopping per-item fallback due to budget deadline (processed={}, remaining={}, elapsed={}ms, budget={}ms)",
                          attemptedInvalid.size() - attemptedInvalid.indexOf(agentName),
                          attemptedInvalid.indexOf(agentName),
                          elapsedMs,
                          budgetMs);
                      break;
                    }
                    if (!removedSet.contains(agentName)) {
                      String scoreString;
                      try {
                        Double score = jedis.zscore(WAITING_SET, agentName);
                        scoreString = score != null ? String.valueOf(score.longValue()) : null;
                      } catch (Exception ignore) {
                        scoreString = null;
                      }
                      try {
                        if (scoreString != null) {
                          Object one =
                              scriptManager.evalshaWithSelfHeal(
                                  jedis,
                                  RedisScriptManager.REMOVE_AGENTS_CONDITIONAL,
                                  java.util.Collections.singletonList(WAITING_SET),
                                  java.util.Arrays.asList(agentName, scoreString));
                          ScriptResults.BatchRemovalResult oneParsed =
                              ScriptResults.parseRemoveAgentsConditional(one);
                          totalCleaned += oneParsed.getRemovedCount();
                          if (oneParsed.getRemovedCount() == 0) {
                            Object fallback =
                                scriptManager.evalshaWithSelfHeal(
                                    jedis,
                                    RedisScriptManager.REMOVE_AGENT,
                                    java.util.Arrays.asList(WORKING_SET, WAITING_SET),
                                    java.util.Collections.singletonList(agentName));
                            if (fallback != null && ((Long) fallback).intValue() == 1) {
                              totalCleaned += 1;
                            }
                          }
                        }
                      } catch (Exception ex) {
                        log.debug("Per-item fallback removal failed for {}: {}", agentName, ex);
                      }
                    }
                  }
                } finally {
                  removedSet.clear();
                }
              }
            } catch (Exception e) {
              log.warn(
                  "Batch removal of invalid waiting agents failed, using guarded individual path",
                  e);
              // Guarded per-item fallback: only act on invalid candidates we positively identify
              for (Tuple orphan : orphans) {
                if (Thread.currentThread().isInterrupted()) {
                  log.warn(
                      "Stopping individual conditional removal due to interrupt (processed={}, remaining={})",
                      orphans.indexOf(orphan),
                      orphans.size() - orphans.indexOf(orphan));
                  break;
                }
                long elapsedMs = nowMs() - startEpochMs;
                if (overBudget(startEpochMs, budgetMs)) {
                  log.info(
                      "Stopping individual conditional removal due to budget deadline (processed={}, remaining={}, elapsed={}ms, budget={}ms)",
                      orphans.indexOf(orphan),
                      orphans.size() - orphans.indexOf(orphan),
                      elapsedMs,
                      budgetMs);
                  break;
                }

                String agentName = orphan.getElement();
                boolean removeNumericOnly =
                    schedulerProperties.getOrphanCleanup().isRemoveNumericOnlyAgents();
                boolean numericOnly =
                    removeNumericOnly && agentName != null && agentName.matches("^\\d{9,11}$");
                boolean invalid = numericOnly || !isAgentStillValid(agentName);

                // Shard-aware gating: only this shard may act on invalid entries
                boolean belongsToThisShard;
                if (acquisitionService == null) {
                  belongsToThisShard = true; // tests or unwired contexts
                } else {
                  try {
                    belongsToThisShard = acquisitionService.belongsToThisShard(agentName);
                  } catch (Exception ex) {
                    belongsToThisShard = false; // fail-safe preserve
                  }
                }

                if (!invalid || !belongsToThisShard) {
                  continue; // preserve valid waiting entries and non-owned shard entries
                }

                String scoreString = String.valueOf((long) orphan.getScore());
                try {
                  Object one =
                      scriptManager.evalshaWithSelfHeal(
                          jedis,
                          RedisScriptManager.REMOVE_AGENTS_CONDITIONAL,
                          java.util.Collections.singletonList(WAITING_SET),
                          java.util.Arrays.asList(agentName, scoreString));
                  ScriptResults.BatchRemovalResult oneParsed =
                      ScriptResults.parseRemoveAgentsConditional(one);
                  totalCleaned += oneParsed.getRemovedCount();
                  if (oneParsed.getRemovedCount() == 0) {
                    // As a final fallback for invalid entries only, attempt unconditional remove
                    Object fallback =
                        scriptManager.evalshaWithSelfHeal(
                            jedis,
                            RedisScriptManager.REMOVE_AGENT,
                            java.util.Arrays.asList(WORKING_SET, WAITING_SET),
                            java.util.Collections.singletonList(agentName));
                    if (fallback != null && ((Long) fallback).intValue() == 1) {
                      totalCleaned += 1;
                    }
                  }
                } catch (Exception ex) {
                  log.debug("Individual conditional removal failed for {}: {}", agentName, ex);
                }
              }
            }
          }
        } finally {
          attemptedInvalid.clear();
          invalidArgs.clear();
          if (attemptedInvalid instanceof java.util.ArrayList) {
            ((java.util.ArrayList<?>) attemptedInvalid).trimToSize();
          }
          if (invalidArgs instanceof java.util.ArrayList) {
            ((java.util.ArrayList<?>) invalidArgs).trimToSize();
          }
        }
      } else {
        totalCleaned += cleanupIndividualOrphans(jedis, setName, orphans, startEpochMs, budgetMs);
      }
    } else {
      // Critical: Prefer individual path to allow validity checks and conditional moves, and to
      // skip locally active work.
      for (int i = 0; i < orphans.size(); i += batchSize) {
        if (Thread.currentThread().isInterrupted()) {
          log.warn(
              "Stopping working-batch processing due to interrupt (processed={}, remaining={})",
              i,
              orphans.size() - i);
          break;
        }
        long elapsedMs = nowMs() - startEpochMs;
        if (overBudget(startEpochMs, budgetMs)) {
          log.info(
              "Stopping working-batch processing due to budget deadline (processed={}, remaining={}, elapsed={}ms, budget={}ms)",
              i,
              orphans.size() - i,
              elapsedMs,
              budgetMs);
          break;
        }
        int endIndex = Math.min(i + batchSize, orphans.size());
        List<Tuple> batch = orphans.subList(i, endIndex);
        totalCleaned += cleanupIndividualOrphans(jedis, setName, batch, startEpochMs, budgetMs);
      }
    }

    return totalCleaned;
  }

  /**
   * Attempts to acquire leadership for orphan cleanup using Redis distributed lock.
   *
   * @return true if leadership was acquired, false otherwise
   */
  private boolean tryAcquireCleanupLeadership() {
    long leadershipTtlMs = schedulerProperties.getOrphanCleanup().getLeadershipTtlMs();
    int leadershipTtlSeconds = (int) (leadershipTtlMs / 1000);

    try (Jedis jedis = jedisPool.getResource()) {
      // Create a unique instance ID to identify this instance as the leader
      String instanceId = InetAddress.getLocalHost().getHostName() + "::" + UUID.randomUUID();

      // Use Redis SET with NX and EX options for atomic lock acquisition with built-in expiry
      String result =
          jedis.set(
              CLEANUP_LEADER_KEY,
              instanceId,
              SetParams.setParams().nx().ex((long) leadershipTtlSeconds));

      boolean acquired = "OK".equals(result);
      if (acquired) {
        currentLeadershipId = instanceId;
        if (log.isDebugEnabled()) {
          log.debug("Acquired orphan cleanup leadership: {}", instanceId);
        }
      } else {
        log.debug("Failed to acquire orphan cleanup leadership");
      }

      return acquired;

    } catch (Exception e) {
      log.warn("Failed to acquire cleanup leadership", e);
      return false;
    }
  }

  /** Releases leadership for orphaned agent cleanup if this instance is the current leader. */
  private void releaseCleanupLeadership() {
    if (currentLeadershipId == null) {
      return;
    }

    try (Jedis jedis = jedisPool.getResource()) {
      // Only delete the key if we own it (atomic check-and-delete)
      Object result =
          scriptManager.evalshaWithSelfHeal(
              jedis,
              RedisScriptManager.RELEASE_LEADERSHIP,
              java.util.Collections.singletonList(CLEANUP_LEADER_KEY),
              java.util.Collections.singletonList(currentLeadershipId));

      if ("1".equals(result.toString())) {
        if (log.isDebugEnabled()) {
          log.debug("Released orphan cleanup leadership: {}", currentLeadershipId);
        }
      } else {
        if (log.isDebugEnabled()) {
          log.debug("Leadership was already released or expired: {}", currentLeadershipId);
        }
      }

    } catch (Exception e) {
      log.warn("Failed to release cleanup leadership", e);
    } finally {
      currentLeadershipId = null;
    }
  }

  /**
   * Fallback method to clean up orphaned agents individually when batch operations fail or are
   * disabled. Implements dual-processing logic: - Valid agents (still configured) -> Move to
   * waiting for rescheduling - Invalid agents (removed accounts) -> Remove completely from Redis
   *
   * @param jedis Redis connection
   * @param setName Redis set name (working or waiting)
   * @param orphans List of orphaned agents to clean up
   * @param startEpochMs Epoch time when cleanup operation started (for budget checking)
   * @param budgetMs Maximum runtime budget in milliseconds (0 = disabled)
   * @return Number of agents successfully cleaned up
   */
  private int cleanupIndividualOrphans(
      Jedis jedis, String setName, List<Tuple> orphans, long startEpochMs, long budgetMs) {
    int cleaned = 0;

    for (Tuple orphan : orphans) {
      if (Thread.currentThread().isInterrupted()) {
        log.warn(
            "Stopping individual orphan cleanup early due to interrupt (cleaned={}, processed={}, remaining={})",
            cleaned,
            orphans.indexOf(orphan),
            orphans.size() - orphans.indexOf(orphan));
        break;
      }
      long elapsedMs = nowMs() - startEpochMs;
      if (overBudget(startEpochMs, budgetMs)) {
        log.info(
            "Stopping individual orphan cleanup early due to budget deadline (cleaned={}, processed={}, remaining={}, elapsed={}ms, budget={}ms)",
            cleaned,
            orphans.indexOf(orphan),
            orphans.size() - orphans.indexOf(orphan),
            elapsedMs,
            budgetMs);
        break;
      }
      try {
        String agentName = orphan.getElement();
        double score = orphan.getScore();
        String scoreInSet =
            String.valueOf((long) score); // Convert to long to match score() method format

        // Determine if this is a valid agent or an agent for a removed account
        boolean isStillValid = isAgentStillValid(agentName);

        // Shard-aware protection: For waiting entries, only this shard should consider removal.
        // If ownership cannot be determined or belongs to other shard, preserve.
        // Fail-safe shard gating: false on unexpected errors to avoid cross-shard deletions;
        // when acquisitionService is not wired (tests), default to true for consistent behavior.
        boolean belongsToThisShard = safeBelongsToShard(agentName);

        if (WORKING_SET.equals(setName)) {
          // Skip locally active agents; zombie cleanup manages overruns
          // Defensive: avoid double map access that could race to null; read once and check.
          java.util.Map<String, String> activeMap =
              acquisitionService != null ? acquisitionService.getActiveAgentsMap() : null;
          boolean locallyActive = activeMap != null && activeMap.containsKey(agentName);
          if (locallyActive) {
            log.debug("Skipping locally active working agent {} during orphan cleanup", agentName);
            continue;
          }

          if (isStillValid) {
            // Shard-aware gating: Only this shard should rescue its own orphaned valid agents.
            // Other shards' active agents in WORKZ should be handled by their respective pods;
            // moving them here would cause thrashing as the owning pod re-acquires immediately.
            if (!belongsToThisShard) {
              log.debug("Skipping valid working agent {} - belongs to different shard", agentName);
              continue;
            }

            // For valid agents in working (truly orphaned due to crashes), move them to waiting
            // and preserve their original ready time to maintain queue fairness.
            // workingScore = acquire_time + timeout; originalReady = acquire_time
            String preservedScore = null;
            try {
              if (acquisitionService != null) {
                preservedScore =
                    acquisitionService.computeOriginalReadySecondsFromWorkingScore(
                        agentName, scoreInSet);
              }
            } catch (Exception e) {
              preservedScore = null; // Fail-safe below
            }

            // Fallback to immediate eligibility (now) if preservation is not possible
            String newScore = preservedScore != null ? preservedScore : score(jedis, 0L);
            Object result =
                scriptManager.evalshaWithSelfHeal(
                    jedis,
                    RedisScriptManager.MOVE_AGENTS_CONDITIONAL,
                    java.util.Arrays.asList(WORKING_SET, WAITING_SET),
                    java.util.Arrays.asList(agentName, scoreInSet, newScore));

            if (result != null && "swapped".equals(result)) {
              cleaned++;
              log.info(
                  "Successfully moved orphaned agent {} (original score: {}, new score: {}) from working to waiting set (preserve_ready={}).",
                  agentName,
                  (long) score,
                  Long.valueOf(newScore),
                  preservedScore != null);

              // Clean up local active tracking; acquisition service preserves waiting if present.
              removeActiveAgent(agentName);
            } else {
              log.debug(
                  "Failed to move orphaned agent {} (original score: {}) from working to waiting. It might have been removed or modified by another process.",
                  agentName,
                  (long) score);
            }
          } else {
            // For invalid agents, removal is shard-aware
            if (belongsToThisShard) {
              // Remove invalid agent using individual script
              Object result =
                  scriptManager.evalshaWithSelfHeal(
                      jedis,
                      RedisScriptManager.REMOVE_AGENT,
                      java.util.Arrays.asList(WORKING_SET, WAITING_SET),
                      java.util.Collections.singletonList(agentName));

              // removeAgent returns 1 for success
              boolean removed = result != null && ((Long) result).intValue() == 1;
              if (removed) {
                cleaned++;
                if (isStillValid) {
                  log.info(
                      "Successfully removed orphaned agent {} (original score: {}) from {} set.",
                      agentName,
                      (long) score,
                      setName);
                } else {
                  log.info(
                      "Successfully removed invalid orphaned agent {} (original score: {}) from {} set.",
                      agentName,
                      (long) score,
                      setName);
                }

                // Clean up local active tracking; waiting is preserved if present.
                removeActiveAgent(agentName);
              } else {
                log.debug(
                    "Failed to remove orphaned agent {} (score: {}) from {} set. It might have been removed by another process.",
                    agentName,
                    (long) score,
                    setName);
              }
            } else {
              log.debug(
                  "Preserving invalid working agent {} due to shard gating (belongs_to_this_shard=false)",
                  agentName);
            }
          }
        } else if (WAITING_SET.equals(setName)) {
          // waiting: Only remove invalid entries; always respect shard gating.
          // Numeric-only epoch-length names are considered corruption when enabled.
          boolean removeNumericOnly =
              schedulerProperties.getOrphanCleanup().isRemoveNumericOnlyAgents();
          boolean numericOnly =
              removeNumericOnly && agentName != null && agentName.matches("^\\d{9,11}$");
          boolean removeCandidate = (numericOnly || !isStillValid) && belongsToThisShard;
          if (removeCandidate) {
            Object result =
                scriptManager.evalshaWithSelfHeal(
                    jedis,
                    RedisScriptManager.REMOVE_AGENT,
                    java.util.Arrays.asList(WORKING_SET, WAITING_SET),
                    java.util.Collections.singletonList(agentName));
            boolean removed = result != null && ((Long) result).intValue() == 1;
            if (removed) {
              cleaned++;
              log.info(
                  "Removed invalid waiting agent {} (original score: {})", agentName, (long) score);
            }
          } else {
            log.debug("Preserving valid waiting agent {} (age-based purge disabled)", agentName);
          }
        }

      } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
        log.warn(
            "Redis connection error during orphan cleanup for {} (score {})",
            orphan.getElement(),
            (long) orphan.getScore(),
            e);
      } catch (Exception e) {
        log.error(
            "Error during orphaned agent cleanup attempt for {} (original score: {})",
            orphan.getElement(),
            (long) orphan.getScore(),
            e);
      }
    }

    if (cleaned > 0) {
      log.info("Individually cleaned {} orphaned agents from {}", cleaned, setName);
    }

    return cleaned;
  }

  /**
   * Determine shard ownership for the given agent in a fail-safe way.
   *
   * <p>This method implements a fail-safe design to prevent cross-shard deletions during orphan
   * cleanup. When shard ownership cannot be determined, the method preserves entries by returning
   * {@code false}, preventing cleanup actions on entries that may belong to other shards.
   *
   * <p><b>Fail-Safe Rationale:</b>
   *
   * <ul>
   *   <li><b>Prevents data corruption:</b> Returning {@code false} on errors prevents this pod from
   *       deleting entries that belong to other shards, which could cause agent loss across the
   *       cluster.
   *   <li><b>Safe during scale events:</b> When pods are added/removed, shard configuration changes
   *       via {@code CachingPodsObserver.refreshHeartbeat()}. Transient exceptions during
   *       reconfiguration (e.g., {@code NullPointerException} if shard state is being updated) are
   *       handled safely by preserving entries.
   *   <li><b>Accumulation trade-off:</b> The fail-safe design may cause orphaned entries to
   *       accumulate over time if exceptions occur frequently, but this is preferable to the risk
   *       of cross-shard deletions causing agent loss.
   * </ul>
   *
   * <p><b>Behavior:</b>
   *
   * <ul>
   *   <li>Uses {@code acquisitionService.belongsToThisShard(agentName)} when available.
   *   <li>Returns {@code false} on any unexpected error to preserve entries (avoid cross-shard
   *       delete). This is the fail-safe behavior.
   *   <li>Returns {@code true} when {@code acquisitionService} is not wired (e.g., in tests) to
   *       maintain consistent behavior without blocking cleanup flows.
   * </ul>
   *
   * <p><b>Observability:</b> Logs exceptions for observability during scale events when shard
   * configuration changes. Transient exceptions are logged at debug level; other exceptions are
   * logged at warn level to help diagnose accumulation issues.
   *
   * @param agentName agent identifier used for shard ownership check
   * @return true if this shard should act on the agent, false otherwise (fail-safe preserve on
   *     errors)
   */
  private boolean safeBelongsToShard(String agentName) {
    if (acquisitionService == null) {
      // In tests or when not wired, preserve entries by default in waiting; for working we gate
      // elsewhere.
      return true;
    }
    try {
      return acquisitionService.belongsToThisShard(agentName);
    } catch (NullPointerException | IllegalStateException e) {
      // Transient exceptions - log for observability (may be due to shard reconfiguration during
      // scale events)
      if (log.isDebugEnabled()) {
        log.debug("Shard check failed for {}: {}", agentName, e.getMessage(), e);
      }
      return false; // Still fail-safe preserve
    } catch (Exception e) {
      // Other exceptions - log but preserve
      log.warn("Shard check exception for {}: {}", agentName, e.getMessage(), e);
      return false;
    }
  }

  /**
   * Check if an agent is still valid (still registered and enabled). This method determines whether
   * an orphaned agent should be moved back to waiting for rescheduling or completely removed from
   * Redis.
   *
   * @param agentType The agent type to validate
   * @return true if agent is still valid, false if it should be purged
   */
  private boolean isAgentStillValid(String agentType) {
    if (acquisitionService == null) {
      // In absence of local registry, treat entries as invalid (test environments). Production pods
      // always wire acquisitionService; tests should set it explicitly when needed.
      if (log.isDebugEnabled()) {
        log.debug(
            "AgentAcquisitionService not available, treating agent {} as invalid (will be removed)",
            agentType);
      }
      return false;
    }

    // Check if agent is still registered in the local registry
    // If it's registered, it's considered valid (enabled agents are registered)
    Agent registeredAgent = acquisitionService.getRegisteredAgent(agentType);
    if (registeredAgent == null) {
      if (log.isDebugEnabled()) {
        log.debug("Agent {} is not registered locally, treating as invalid", agentType);
      }
      return false;
    }

    if (log.isDebugEnabled()) {
      log.debug("Agent {} is valid (registered locally)", agentType);
    }
    return true;
  }

  /**
   * Generate a Redis score (timestamp) for agent scheduling. Scores are in seconds since epoch to
   * match Redis sorted set format.
   *
   * @param jedis Redis connection
   * @param delayMs Delay in milliseconds before agent should run (0 = immediate)
   * @return Score as string
   */
  private String score(Jedis jedis, long delayMs) {
    java.util.function.LongSupplier supplier =
        acquisitionService != null ? () -> acquisitionService.nowMsWithOffset() : null;
    return com.netflix.spinnaker.cats.redis.cluster.support.RedisTimeUtils.scoreFromMsDelay(
        jedis, delayMs, supplier);
  }

  /**
   * Returns Redis-aligned "now" for orphan cutoff calculations.
   *
   * <p>When acquisitionService is available we align to the Redis/server offset cache to keep
   * cutoff timing consistent with acquisition/reschedule score generation.
   */
  private long nowMsForRedisCutoff() {
    try {
      if (acquisitionService != null) {
        long redisAlignedNowMs = acquisitionService.nowMsWithOffset();
        if (redisAlignedNowMs > 0L) {
          return redisAlignedNowMs;
        }
      }
    } catch (Exception e) {
      log.debug("Falling back to local clock for orphan cutoff timing", e);
    }
    return nowMs();
  }

  /**
   * Remove an active agent from local tracking and clean up its execution state. This includes
   * canceling futures and releasing semaphore permits.
   *
   * @param agentType The agent to remove
   */
  private void removeActiveAgent(String agentType) {
    if (acquisitionService != null) {
      acquisitionService.removeActiveAgent(agentType);
      log.debug("Removed active agent {} from local tracking", agentType);
    } else {
      log.debug("AgentAcquisitionService not available, cannot remove active agent {}", agentType);
    }
  }
}
