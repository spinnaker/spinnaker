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
import com.netflix.spinnaker.cats.redis.cluster.support.ScriptResults;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

/**
 * Service that detects and cleans up zombie agents (stuck or timed-out agents).
 *
 * <p>Zombies are agents that exceed their completion deadline plus a configurable buffer. The
 * service scans locally active agents, cancels stuck executions, and removes them from Redis to
 * allow rescheduling.
 *
 * <p><b>Scope:</b> Locally active agents only (with RunState on this pod). Cancels the Future (via
 * interrupt), removes from local tracking. Permit is released when thread exits in worker finally
 * block. See {@link OrphanCleanupService} for agents from crashed pods (Redis-only, no permits).
 */
@Component
@Slf4j
public class ZombieCleanupService {

  private final String WORKING_SET;

  private final JedisPool jedisPool;
  private final RedisScriptManager scriptManager;
  private final PrioritySchedulerProperties schedulerProperties;
  private final PrioritySchedulerMetrics metrics;

  // Tracking for zombie cleanup
  private final LongAdder zombiesCleanedUp = new LongAdder();
  private volatile long lastZombieCleanupEpochMs = 0;

  // Compiled regex pattern for exceptional agents
  private volatile Pattern exceptionalAgentsPattern;

  // Design decision: ThreadLocal proliferation (intentional performance optimization)
  // Reusable containers reduce GC pressure in cleanup hot paths. Safe because cleanup runs in a
  // single-threaded executor with proper finally-block clearing. See AgentAcquisitionService for
  // detailed rationale.
  private static final ThreadLocal<java.util.List<String>> REUSABLE_ZOMBIE_TYPES =
      ThreadLocal.withInitial(java.util.ArrayList::new);
  private static final ThreadLocal<java.util.List<String>> REUSABLE_ZOMBIE_BATCH =
      ThreadLocal.withInitial(java.util.ArrayList::new);
  private static final ThreadLocal<java.util.List<String>> REUSABLE_BATCH_ARGS =
      ThreadLocal.withInitial(java.util.ArrayList::new);
  private static final ThreadLocal<java.util.List<String>> REUSABLE_ATTEMPTED =
      ThreadLocal.withInitial(java.util.ArrayList::new);
  private static final ThreadLocal<java.util.List<String>> REUSABLE_INPUT_CANDIDATES =
      ThreadLocal.withInitial(java.util.ArrayList::new);
  private static final ThreadLocal<java.util.Set<String>> REUSABLE_STRING_SET =
      ThreadLocal.withInitial(java.util.HashSet::new);
  private static final ThreadLocal<java.util.List<String>> REUSABLE_REMAINING =
      ThreadLocal.withInitial(java.util.ArrayList::new);

  /**
   * Constructs a new ZombieCleanupService instance with the provided properties.
   *
   * @param jedisPool Jedis connection pool for Redis operations
   * @param scriptManager Redis script manager for batch operations
   * @param schedulerProperties Configuration properties for scheduler behavior
   */
  public ZombieCleanupService(
      JedisPool jedisPool,
      RedisScriptManager scriptManager,
      PrioritySchedulerProperties schedulerProperties,
      PrioritySchedulerMetrics metrics) {
    this.jedisPool = jedisPool;
    this.scriptManager = scriptManager;
    this.schedulerProperties = schedulerProperties;
    this.metrics = metrics != null ? metrics : PrioritySchedulerMetrics.NOOP;
    compileExceptionalAgentsPattern();

    PrioritySchedulerProperties.Keys keysCfg = schedulerProperties.getKeys();
    String hash = keysCfg.getHashTag();
    String brace = (hash != null && !hash.isEmpty()) ? ("{" + hash + "}") : "";
    String prefix = keysCfg.getPrefix() != null ? keysCfg.getPrefix() : "";
    this.WORKING_SET = prefix + keysCfg.getWorkingSet() + brace;
  }

  // Reference to acquisition service for coordinating with active agent tracking.
  private AgentAcquisitionService acquisitionService;

  /**
   * Sets the acquisition service for coordinating with active agent tracking.
   *
   * @param acquisitionService the acquisition service instance to use
   */
  @VisibleForTesting
  void setAcquisitionService(AgentAcquisitionService acquisitionService) {
    this.acquisitionService = acquisitionService;
  }

  /**
   * Remove ThreadLocal buffers held by the current thread to release per-thread memory. Intended to
   * be invoked on the owning executor thread during shutdown.
   */
  @VisibleForTesting
  void removeThreadLocals() {
    try {
      REUSABLE_ZOMBIE_TYPES.remove();
    } catch (Exception ignore) {
      // Best-effort – buffers may already be cleared/GC'd
    }
    try {
      REUSABLE_ZOMBIE_BATCH.remove();
    } catch (Exception ignore) {
      // Best-effort – buffers may already be cleared/GC'd
    }
    try {
      REUSABLE_BATCH_ARGS.remove();
    } catch (Exception ignore) {
      // Best-effort – buffers may already be cleared/GC'd
    }
    try {
      REUSABLE_ATTEMPTED.remove();
    } catch (Exception ignore) {
      // Best-effort – buffers may already be cleared/GC'd
    }
    try {
      REUSABLE_INPUT_CANDIDATES.remove();
    } catch (Exception ignore) {
      // Best-effort – buffers may already be cleared/GC'd
    }
    try {
      REUSABLE_STRING_SET.remove();
    } catch (Exception ignore) {
      // Best-effort – buffers may already be cleared/GC'd
    }
    try {
      REUSABLE_REMAINING.remove();
    } catch (Exception ignore) {
      // Best-effort – buffers may already be cleared/GC'd
    }
  }

  /**
   * Compiles the exceptional agents pattern for efficient matching. This method is called during
   * initialization and can be called again if configuration changes.
   */
  private void compileExceptionalAgentsPattern() {
    try {
      String patternString = schedulerProperties.getExceptionalAgentsPattern();
      this.exceptionalAgentsPattern = schedulerProperties.getExceptionalAgentsPatternCompiled();
      if (this.exceptionalAgentsPattern != null) {
        log.info(
            "Compiled exceptional agents pattern: {}", this.exceptionalAgentsPattern.pattern());
      } else if (patternString != null && !patternString.trim().isEmpty()) {
        // Pattern string is set but compilation failed (getExceptionalAgentsPatternCompiled catches
        // exceptions)
        log.error(
            "Failed to compile exceptional agents pattern '{}' - invalid regex, using default threshold for all agents",
            patternString);
      } else {
        log.debug("No exceptional agents pattern configured");
      }
    } catch (Exception e) {
      this.exceptionalAgentsPattern = null;
      log.error("Failed to obtain exceptional agents pattern", e);
    }
  }

  /**
   * Determines the appropriate zombie threshold for the given agent type.
   *
   * @param agentType The agent type to check
   * @return The zombie threshold in milliseconds (default or exceptional)
   */
  private long getZombieThresholdForAgent(String agentType) {
    // Check if agent matches exceptional pattern
    if (exceptionalAgentsPattern != null && exceptionalAgentsPattern.matcher(agentType).matches()) {
      long exceptionalThreshold =
          schedulerProperties.getZombieCleanup().getExceptionalAgents().getThresholdMs();
      log.debug(
          "Agent '{}' matches exceptional pattern, using threshold: {}ms",
          agentType,
          exceptionalThreshold);
      return exceptionalThreshold;
    }

    // Use default threshold
    return schedulerProperties.getZombieCleanup().getThresholdMs();
  }

  /**
   * Clean up zombie agents if the cleanup interval has elapsed. This method is called periodically
   * by the main scheduler.
   *
   * @param activeAgents Map of active agents (agentType -> completionDeadline)
   * @param activeAgentsFutures Map of agent futures for cancellation
   */
  public void cleanupZombieAgentsIfNeeded(
      Map<String, String> activeAgents, Map<String, Future<?>> activeAgentsFutures) {
    // Defense-in-depth: skip if disabled (scheduler also checks before calling)
    if (!schedulerProperties.getZombieCleanup().isEnabled()) {
      if (log.isDebugEnabled()) {
        log.debug("Skipping zombie cleanup: disabled via configuration");
      }
      return;
    }

    long now = nowMs();
    long zombieCleanupInterval = schedulerProperties.getZombieCleanup().getIntervalMs();

    if (isPeriodElapsed(lastZombieCleanupEpochMs, zombieCleanupInterval)) {
      if (log.isDebugEnabled()) {
        log.debug(
            "Starting zombie cleanup cycle (interval={}ms, active_agents={})",
            zombieCleanupInterval,
            activeAgents.size());
      }
      cleanupZombieAgents(activeAgents, activeAgentsFutures);
      lastZombieCleanupEpochMs = now;
    } else {
      long remaining = zombieCleanupInterval - (now - lastZombieCleanupEpochMs);
      if (log.isDebugEnabled()) {
        log.debug("Skipping zombie cleanup - interval not elapsed ({}ms remaining)", remaining);
      }
    }
  }

  /**
   * Cleans up zombie agents on the local instance based on execution duration.
   *
   * <p>Zombie agents are those that have been running for too long on this Clouddriver instance.
   * This can happen due to rate limiting, execution timeouts, or other interruptions in agent
   * processing. Unlike orphaned agents (which have no running instance), zombies are still
   * executing but have exceeded their expected runtime.
   *
   * <p>This cleanup prevents thread pool exhaustion: stuck agents hold executor threads
   * indefinitely, eventually saturating the pool and blocking all new work. The cleanup cancels
   * stuck futures and frees threads for other agents. Checks the local activeAgents map for agents
   * that have exceeded their completion deadline (current_time + agent_timeout), then performs
   * cleanup in Redis to remove the agents from both working and waiting sets.
   *
   * @param activeAgents Map of active agents (agentType -> completionDeadline)
   * @param activeAgentsFutures Map of agent futures for cancellation
   * @return Number of zombie agents cleaned up
   */
  public int cleanupZombieAgents(
      Map<String, String> activeAgents, Map<String, Future<?>> activeAgentsFutures) {
    long start = nowMs();
    final long budgetMs = schedulerProperties.getZombieCleanup().getRunBudgetMs();
    long currentTime = nowMs();
    java.util.List<String> zombieAgentTypes = REUSABLE_ZOMBIE_TYPES.get();
    try {
      zombieAgentTypes.clear();

      int validAgentsScanned = 0;

      for (Map.Entry<String, String> entry : activeAgents.entrySet()) {
        if (Thread.currentThread().isInterrupted()) {
          log.warn(
              "Stopping zombie scan early due to interrupt (scanned={}, zombies={})",
              validAgentsScanned,
              zombieAgentTypes.size());
          break;
        }
        currentTime = nowMs();
        long elapsedMs = currentTime - start;
        if (overBudget(start, budgetMs)) {
          log.info(
              "Zombie scan stopping due to budget deadline (scanned={}, zombies={}, elapsed={}ms, budget={}ms)",
              validAgentsScanned,
              zombieAgentTypes.size(),
              elapsedMs,
              budgetMs);
          break;
        }
        String agentType = entry.getKey();
        String deadlineScore = entry.getValue();

        try {
          // Score represents completion deadline in epoch seconds
          // Formula: acquire_time + agent_timeout = completion deadline
          long completionDeadlineMs = Long.parseLong(deadlineScore) * 1000;
          validAgentsScanned++;

          // Different agents may have different thresholds (e.g., BigQuery agents need longer)
          long zombieThreshold = getZombieThresholdForAgent(agentType);

          // Zombie detection: current_time > (completion_deadline + buffer_threshold)
          // Buffer prevents false positives from temporary delays
          if (currentTime > completionDeadlineMs + zombieThreshold) {
            zombieAgentTypes.add(agentType);
            long overdueMs = currentTime - completionDeadlineMs;
            boolean isExceptional =
                exceptionalAgentsPattern != null
                    && exceptionalAgentsPattern.matcher(agentType).matches();
            log.warn(
                "Zombie agent detected: {} ({}ms overdue past completion deadline, {}ms {} threshold exceeded)",
                agentType,
                overdueMs,
                zombieThreshold,
                isExceptional ? "exceptional" : "default");
          }
        } catch (NumberFormatException e) {
          log.warn("Invalid acquire score for agent {}: {}", agentType, deadlineScore, e);

          // Force cleanup invalid scores to prevent permanent stuck state
          // Defensive against external modifications
          zombieAgentTypes.add(agentType);
          log.error(
              "Force cleaning zombie agent {} with corrupted acquire score '{}' - likely external modification during acquisition",
              agentType,
              deadlineScore);
        }
      }

      // Log scanning summary
      long scanElapsedMs = nowMs() - start;
      if (zombieAgentTypes.isEmpty()) {
        if (log.isDebugEnabled()) {
          log.debug(
              "Zombie scan completed: {} agents analyzed, 0 zombies found (elapsed={}ms)",
              validAgentsScanned,
              scanElapsedMs);
        }
        return 0;
      }

      log.info(
          "Zombie scan completed: {} agents analyzed, {} zombies found - cleaning up: {} (elapsed={}ms)",
          validAgentsScanned,
          zombieAgentTypes.size(),
          zombieAgentTypes.stream().limit(5).collect(java.util.stream.Collectors.toList()),
          scanElapsedMs);

      // Check if batch operations are enabled (disabled by default for safety)
      boolean batchOperationsEnabled = schedulerProperties.getBatchOperations().isEnabled();
      int totalCleaned = 0;

      try (Jedis jedis = jedisPool.getResource()) {
        if (!batchOperationsEnabled) {
          if (log.isDebugEnabled()) {
            log.debug(
                "Batch zombie cleanup disabled, using individual operations for {} agents",
                zombieAgentTypes.size());
          }

          for (String agentType : zombieAgentTypes) {
            if (Thread.currentThread().isInterrupted()) {
              log.warn(
                  "Stopping zombie individual cleanup due to interrupt (cleaned={}, remaining={})",
                  totalCleaned,
                  zombieAgentTypes.size() - totalCleaned);
              break;
            }
            long elapsedMs = nowMs() - start;
            if (overBudget(start, budgetMs)) {
              log.info(
                  "Zombie individual cleanup stopping due to budget deadline (cleaned={}, remaining={}, elapsed={}ms, budget={}ms)",
                  totalCleaned,
                  zombieAgentTypes.size() - totalCleaned,
                  elapsedMs,
                  budgetMs);
              break;
            }
            try {
              if (cleanupIndividualZombieAgent(
                  jedis, agentType, activeAgents, activeAgentsFutures)) {
                totalCleaned++;
              }
            } catch (Exception e) {
              log.error("Failed to cleanup zombie agent {}", agentType, e);
            }
          }
        } else {
          // Use batch cleanup with fallback to individual operations
          java.util.List<String> zombieBatch = REUSABLE_ZOMBIE_BATCH.get();
          try {
            zombieBatch.clear();
            int configured = schedulerProperties.getBatchOperations().getBatchSize();
            if (configured <= 0) {
              // Simple, non-magic fallback: process up to the current number of candidates.
              configured = Math.max(1, zombieAgentTypes.size());
            }
            int batchSize = Math.min(configured, zombieAgentTypes.size());
            if (log.isDebugEnabled()) {
              log.debug(
                  "Processing {} zombie agents in batches of {} with fallback",
                  zombieAgentTypes.size(),
                  batchSize);
            }

            for (String agentType : zombieAgentTypes) {
              if (Thread.currentThread().isInterrupted()) {
                log.warn(
                    "Stopping zombie batch preparation due to interrupt (prepared={}, cleaned={})",
                    zombieBatch.size(),
                    totalCleaned);
                break;
              }
              long elapsedMs = nowMs() - start;
              if (overBudget(start, budgetMs)) {
                log.info(
                    "Zombie batch preparation stopping due to budget deadline (prepared={}, cleaned={}, elapsed={}ms, budget={}ms)",
                    zombieBatch.size(),
                    totalCleaned,
                    elapsedMs,
                    budgetMs);
                break;
              }
              zombieBatch.add(agentType);

              if (zombieBatch.size() >= batchSize) {
                if (Thread.currentThread().isInterrupted()) {
                  log.warn(
                      "Skipping zombie batch execution due to interrupt (batch_size={}, cleaned={})",
                      zombieBatch.size(),
                      totalCleaned);
                  break;
                }
                long batchElapsedMs = nowMs() - start;
                if (overBudget(start, budgetMs)) {
                  log.info(
                      "Skipping zombie batch execution due to budget deadline (batch_size={}, cleaned={}, elapsed={}ms, budget={}ms)",
                      zombieBatch.size(),
                      totalCleaned,
                      batchElapsedMs,
                      budgetMs);
                  break;
                }
                totalCleaned +=
                    cleanupZombieBatch(
                        jedis, zombieBatch, activeAgents, activeAgentsFutures, start, budgetMs);
                zombieBatch.clear();
              }
            }

            // Process remaining zombies
            if (!zombieBatch.isEmpty()) {
              if (!Thread.currentThread().isInterrupted() && !overBudget(start, budgetMs)) {
                totalCleaned +=
                    cleanupZombieBatch(
                        jedis, zombieBatch, activeAgents, activeAgentsFutures, start, budgetMs);
              }
            }
          } finally {
            zombieBatch.clear();
          }
        }

        zombiesCleanedUp.add(totalCleaned);
        long totalElapsedMs = nowMs() - start;
        metrics.recordCleanupTime("zombie", totalElapsedMs);
        metrics.incrementCleanupCleaned("zombie", totalCleaned);
        if (totalCleaned > 0) {
          log.info(
              "Zombie cleanup cycle completed: {} agents cleaned (scanned={}, zombies={}, elapsed={}ms, budget={}ms)",
              totalCleaned,
              validAgentsScanned,
              zombieAgentTypes.size(),
              totalElapsedMs,
              budgetMs);
        } else if (log.isDebugEnabled()) {
          log.debug(
              "Zombie cleanup cycle completed: 0 agents cleaned (scanned={}, elapsed={}ms, budget={}ms)",
              validAgentsScanned,
              totalElapsedMs,
              budgetMs);
        }
        return totalCleaned;

      } catch (Exception e) {
        log.error("Error during zombie agent cleanup", e);
        metrics.recordCleanupTime("zombie", nowMs() - start);
        return 0;
      }
    } finally {
      zombieAgentTypes.clear();
      if (zombieAgentTypes instanceof java.util.ArrayList) {
        ((java.util.ArrayList<?>) zombieAgentTypes).trimToSize();
      }
    }
  }

  /**
   * Get the total number of zombie agents cleaned up since startup.
   *
   * @return total zombies cleaned up
   */
  @VisibleForTesting
  long getZombiesCleanedUp() {
    return zombiesCleanedUp.sum();
  }

  /**
   * Get the timestamp of the last zombie cleanup operation.
   *
   * @return last cleanup timestamp in milliseconds
   */
  @VisibleForTesting
  long getLastZombieCleanup() {
    return lastZombieCleanupEpochMs;
  }

  /**
   * Refreshes the exceptional agents pattern configuration. This can be called when configuration
   * is updated at runtime.
   */
  public void refreshExceptionalAgentsPattern() {
    compileExceptionalAgentsPattern();
  }

  /**
   * Clean up zombie batch with optional batch operation and fallback mechanism. If batch operations
   * are enabled in configuration, attempts batch cleanup first. If batch operations are disabled or
   * if the batch operation fails, falls back to individual cleanup for each zombie agent.
   *
   * @param jedis Jedis connection for Redis operations
   * @param zombieAgentTypes List of zombie agent types
   * @param activeAgents Map of active agents (agentType -> completionDeadline)
   * @param activeAgentsFutures Map of agent futures for cancellation
   * @return Number of agents cleaned up
   */
  private int cleanupZombieBatch(
      Jedis jedis,
      List<String> zombieAgentTypes,
      Map<String, String> activeAgents,
      Map<String, Future<?>> activeAgentsFutures,
      long startEpochMs,
      long budgetMs) {
    java.util.List<String> batchArgs = REUSABLE_BATCH_ARGS.get();
    java.util.List<String> attemptedCandidates = REUSABLE_ATTEMPTED.get();
    java.util.List<String> inputCandidates = REUSABLE_INPUT_CANDIDATES.get();
    java.util.Set<String> removedSet = REUSABLE_STRING_SET.get();
    java.util.List<String> remainingForFallback = REUSABLE_REMAINING.get();
    try {
      batchArgs.clear();
      attemptedCandidates.clear();
      inputCandidates.clear();
      removedSet.clear();
      remainingForFallback.clear();

      if (zombieAgentTypes.isEmpty()) {
        return 0;
      }

      // Try batch operation first (even for a single agent)
      if (!zombieAgentTypes.isEmpty()) {
        try {
          // Build arguments for batch cleanup: [agent1, score1, agent2, score2, ...]
          // Track the specific agents we actually attempted in the batch (deadlineScore present)
          inputCandidates.addAll(zombieAgentTypes);

          for (String agentType : zombieAgentTypes) {
            if (Thread.currentThread().isInterrupted()) {
              log.warn("Stopping zombie batch build due to interrupt");
              break;
            }
            if (overBudget(startEpochMs, budgetMs)) {
              log.warn("Stopping zombie batch build due to budget deadline");
              break;
            }
            String deadlineScore = activeAgents.get(agentType);
            if (deadlineScore != null) {
              batchArgs.add(agentType);
              batchArgs.add(deadlineScore);
              attemptedCandidates.add(agentType);
            }
          }

          if (!batchArgs.isEmpty()) {
            // Execute Lua script to batch cleanup zombie agents from Redis working set
            Object result =
                scriptManager.evalshaWithSelfHeal(
                    jedis,
                    RedisScriptManager.REMOVE_AGENTS_CONDITIONAL,
                    java.util.Collections.singletonList(WORKING_SET), // Redis key (working)
                    batchArgs); // [agent1, score1, agent2, score2, ...]

            // Parse Lua script return value and synchronize local state
            ScriptResults.BatchRemovalResult parsed =
                ScriptResults.parseRemoveAgentsConditional(result);
            int cleanedByBatch = parsed.getRemovedCount();
            if (cleanedByBatch > 0) {
              log.info(
                  "Zombie cleanup batch processed: {} agents cleaned from {} candidates",
                  cleanedByBatch,
                  zombieAgentTypes.size());
              for (String agentType : parsed.getMembers()) {
                Future<?> future = activeAgentsFutures.remove(agentType);
                if (future != null && !future.isDone()) {
                  boolean cancelled = future.cancel(true);
                  if (log.isDebugEnabled()) {
                    log.debug("Cancelled zombie agent {} future: {}", agentType, cancelled);
                  }
                }
                // Permit is released when thread exits in worker finally block
                if (acquisitionService != null) {
                  try {
                    acquisitionService.removeActiveAgent(agentType);
                  } catch (Exception e) {
                    log.debug(
                        "Failed to remove active agent via service; falling back to map removal",
                        e);
                    activeAgents.remove(agentType);
                  }
                } else {
                  activeAgents.remove(agentType);
                }
                if (log.isDebugEnabled()) {
                  log.debug("Cleaned up zombie agent: {}", agentType);
                }
              }
              // Do not return early; fall through to per-item cleanup for any remaining original
              // candidates that were not removed by the batch operation.
              removedSet.addAll(parsed.getMembers());
              for (String candidate : inputCandidates) {
                if (!removedSet.contains(candidate)) {
                  remainingForFallback.add(candidate);
                }
              }
              // Perform per-item fallback over remaining candidates and add to cleanedByBatch
              if (log.isDebugEnabled()) {
                log.debug(
                    "Using individual cleanup for {} remaining zombie agents after batch",
                    remainingForFallback.size());
              }
              int fallbackCleaned = 0;
              for (String agentType : remainingForFallback) {
                if (Thread.currentThread().isInterrupted()) {
                  log.warn(
                      "Stopping zombie individual fallback due to interrupt (fallback_cleaned={}, remaining={})",
                      fallbackCleaned,
                      remainingForFallback.size() - fallbackCleaned);
                  break;
                }
                long elapsedMs = nowMs() - startEpochMs;
                if (overBudget(startEpochMs, budgetMs)) {
                  log.info(
                      "Zombie individual fallback stopping due to budget deadline (fallback_cleaned={}, remaining={}, elapsed={}ms, budget={}ms)",
                      fallbackCleaned,
                      remainingForFallback.size() - fallbackCleaned,
                      elapsedMs,
                      budgetMs);
                  break;
                }
                try {
                  if (cleanupIndividualZombieAgent(
                      jedis, agentType, activeAgents, activeAgentsFutures)) {
                    fallbackCleaned++;
                  }
                } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
                  log.warn("Redis connection error while cleaning zombie {}", agentType, e);
                } catch (Exception e) {
                  log.warn("Failed to cleanup individual zombie {}", agentType, e);
                }
              }
              return cleanedByBatch + fallbackCleaned;
            }
          }
        } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
          log.warn(
              "Redis connection error during batch zombie cleanup for {} agents",
              zombieAgentTypes.size(),
              e);
        } catch (Exception e) {
          log.warn(
              "Batch zombie cleanup failed for {} agents, falling back to individual cleanup",
              zombieAgentTypes.size(),
              e);
        }
      }

      // Batch operation failed, disabled, or single agent - fall back to individual cleanup
      if (log.isDebugEnabled()) {
        log.debug("Using individual cleanup for {} zombie agents", zombieAgentTypes.size());
      }
      int totalCleaned = 0;
      for (String agentType : zombieAgentTypes) {
        if (Thread.currentThread().isInterrupted()) {
          log.warn(
              "Stopping zombie individual fallback due to interrupt (cleaned={}, remaining={})",
              totalCleaned,
              zombieAgentTypes.size() - totalCleaned);
          break;
        }
        long elapsedMs = nowMs() - startEpochMs;
        if (overBudget(startEpochMs, budgetMs)) {
          log.info(
              "Zombie individual fallback stopping due to budget deadline (cleaned={}, remaining={}, elapsed={}ms, budget={}ms)",
              totalCleaned,
              zombieAgentTypes.size() - totalCleaned,
              elapsedMs,
              budgetMs);
          break;
        }
        try {
          if (cleanupIndividualZombieAgent(jedis, agentType, activeAgents, activeAgentsFutures)) {
            totalCleaned++;
          }
        } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
          log.warn("Redis connection error while cleaning zombie {}", agentType, e);
        } catch (Exception e) {
          log.warn("Failed to cleanup individual zombie {}", agentType, e);
        }
      }
      return totalCleaned;
    } finally {
      remainingForFallback.clear();
      removedSet.clear();
      inputCandidates.clear();
      attemptedCandidates.clear();
      batchArgs.clear();
      if (remainingForFallback instanceof java.util.ArrayList) {
        ((java.util.ArrayList<?>) remainingForFallback).trimToSize();
      }
      if (inputCandidates instanceof java.util.ArrayList) {
        ((java.util.ArrayList<?>) inputCandidates).trimToSize();
      }
      if (attemptedCandidates instanceof java.util.ArrayList) {
        ((java.util.ArrayList<?>) attemptedCandidates).trimToSize();
      }
      if (batchArgs instanceof java.util.ArrayList) {
        ((java.util.ArrayList<?>) batchArgs).trimToSize();
      }
    }
  }

  /**
   * Clean up a single zombie agent individually.
   *
   * @param jedis Jedis connection for Redis operations
   * @param agentType Type of the zombie agent
   * @param activeAgents Map of active agents (agentType -> completionDeadline)
   * @param activeAgentsFutures Map of agent futures for cancellation
   * @return true if the agent was successfully cleaned up
   */
  private boolean cleanupIndividualZombieAgent(
      Jedis jedis,
      String agentType,
      Map<String, String> activeAgents,
      Map<String, Future<?>> activeAgentsFutures) {

    try {
      // Snapshot current future and then delegate active tracking removal to acquisition service
      Future<?> future = activeAgentsFutures.remove(agentType);
      String deadlineScore = activeAgents.get(agentType);

      if (deadlineScore == null) {
        log.debug("Agent {} not found in active tracking, skipping cleanup", agentType);
        return false;
      }

      // Cancel the future if it exists
      if (future != null && !future.isDone()) {
        future.cancel(true);
        log.info("Cancelled zombie agent execution: {}", agentType);
      }

      // Remove from WORKING only by default to preserve any legitimate waiting entry that
      // should allow immediate reacquisition. This matches batch behavior and avoids delaying
      // the next run. If a duplicate exists in WAITING (corruption), it will be handled by
      // orphan cleanup's optional repair or a targeted check below.
      Object result =
          scriptManager.evalshaWithSelfHeal(
              jedis,
              RedisScriptManager.REMOVE_AGENTS_CONDITIONAL,
              java.util.Collections.singletonList(WORKING_SET),
              java.util.Arrays.asList(agentType, deadlineScore));

      boolean removed = false;
      try {
        if (result instanceof java.util.List) {
          java.util.List<?> list = (java.util.List<?>) result;
          if (!list.isEmpty() && list.get(0) instanceof Number) {
            removed = ((Number) list.get(0)).intValue() > 0;
          }
        }
      } catch (Exception parseEx) {
        log.debug(
            "Failed to parse removeAgentsConditional result for {}: {}",
            agentType,
            result,
            parseEx);
        removed = false;
      }

      // Do not perform an unconditional fallback ZREM here. If the conditional removal failed,
      // ownership likely changed or the agent was already removed. Proceed with local cleanup
      // regardless to avoid race conditions that could orphan a legitimately re-acquired agent.
      // Permit is released when thread exits in worker finally block.

      // Always clean local state regardless of Redis outcome to prevent leaks
      if (acquisitionService != null) {
        acquisitionService.removeActiveAgent(agentType);
      } else {
        activeAgents.remove(agentType);
      }

      if (removed) {
        log.debug("Removed zombie agent {} from Redis working set", agentType);
        // Note: Zombie cleanup only removes from WORKING set. If a duplicate exists in WAITING,
        // that's corruption which should be handled by orphan cleanup (not zombie cleanup).
        // A legitimate WAITING entry would have been added AFTER zombie cleanup by completion
        // processing, so zombie cleanup should never touch WAITING.
      } else {
        log.debug(
            "Zombie agent {} not found in Redis working set during cleanup (result={})",
            agentType,
            result);
      }

      // Count as cleaned once we've definitively stopped local execution and freed capacity.
      return true;

    } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
      log.warn("Redis connection error removing zombie {} from Redis", agentType, e);
      return false;
    } catch (Exception e) {
      log.warn("Failed to remove zombie agent {} from Redis", agentType, e);
      return false;
    }
  }
}
