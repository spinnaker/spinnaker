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

import static com.netflix.spinnaker.cats.redis.cluster.support.CadenceGuard.isPeriodElapsed;
import static com.netflix.spinnaker.cats.redis.cluster.support.CadenceGuard.nowMs;
import static com.netflix.spinnaker.cats.redis.cluster.support.CadenceGuard.overBudget;
import static com.netflix.spinnaker.cats.redis.cluster.support.ExecutorUtils.newOnDemandSingleThreadExecutor;

import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.agent.AgentSchedulerAware;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider;
import com.netflix.spinnaker.cats.cluster.ShardingFilter;
import com.netflix.spinnaker.cats.module.CatsModuleAware;
import com.netflix.spinnaker.cats.redis.cluster.PrioritySchedulerCircuitBreaker.State;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.JedisPool;

/**
 * Priority-based Redis agent scheduler using sorted sets for distributed coordination.
 *
 * <p>Uses two Redis sorted sets for scheduling agents across multiple instances:
 *
 * <ul>
 *   <li><strong>waiting set:</strong> Agents ready for execution, scored by next run time
 *   <li><strong>working set:</strong> Agents currently executing, scored by completion deadline
 * </ul>
 *
 * <p>Key features: atomic state transitions via Lua scripts, priority scheduling (lower scores =
 * higher priority), deadline-aware timeouts, zombie detection, orphan cleanup.
 *
 * <p>See external documentation for detailed configuration reference.
 */
@Component
@Slf4j
public class PriorityAgentScheduler extends CatsModuleAware
    implements AgentScheduler<AgentLock>, Runnable {

  // Core services
  private final RedisScriptManager scriptManager;
  private final PrioritySchedulerMetrics metrics;
  private final AgentAcquisitionService acquisitionService;
  private final ZombieCleanupService zombieService;
  private final OrphanCleanupService orphanService;
  private final PrioritySchedulerConfiguration config;

  // External dependencies
  private final NodeStatusProvider nodeStatusProvider;
  private final ShardingFilter shardingFilter;

  // Runtime state
  private final AtomicLong runCount = new AtomicLong(0);
  private final AtomicBoolean running = new AtomicBoolean(false);
  private final AtomicLong lastHealthLogEpochMs = new AtomicLong(0);

  // Non-blocking executors and guards
  private final java.util.concurrent.ExecutorService zombieCleanupExecutor;
  private final java.util.concurrent.ExecutorService orphanCleanupExecutor;
  private final java.util.concurrent.ExecutorService reconcileExecutor;
  private final AtomicBoolean zombieCleanupRunning = new AtomicBoolean(false);
  private final AtomicBoolean orphanCleanupRunning = new AtomicBoolean(false);
  private final AtomicBoolean reconcileRunning = new AtomicBoolean(false);

  // Timeout tracking for cleanup tasks - allows detection of hung cleanup without blocking
  // scheduler
  private final AtomicLong zombieCleanupStartMs = new AtomicLong(0);
  private final AtomicLong orphanCleanupStartMs = new AtomicLong(0);
  private volatile java.util.concurrent.Future<?> zombieCleanupFuture;
  private volatile java.util.concurrent.Future<?> orphanCleanupFuture;

  // Simple starvation counter: increments when permits==0 and pool activeCount==0. Resets
  // otherwise.
  private final java.util.concurrent.atomic.AtomicInteger permitStarvationConsecutive =
      new java.util.concurrent.atomic.AtomicInteger(0);
  // Snapshot of starvation state for inclusion in 10-minute health summary
  private final java.util.concurrent.atomic.AtomicBoolean lastStarvationSuspected =
      new java.util.concurrent.atomic.AtomicBoolean(false);
  private final java.util.concurrent.atomic.AtomicInteger lastStarvationTicks =
      new java.util.concurrent.atomic.AtomicInteger(0);
  private final java.util.concurrent.atomic.AtomicLong lastStarvationDegradedMs =
      new java.util.concurrent.atomic.AtomicLong(0L);

  // Track all agents provided via schedule(), regardless of current sharding gating
  private final java.util.concurrent.ConcurrentMap<String, KnownAgent> knownAgents =
      new java.util.concurrent.ConcurrentHashMap<>();

  // Reconciliation cadence control
  private final AtomicLong lastReconcileEpochMs = new AtomicLong(0);
  // Local submission gate for orphan cleanup to avoid per-second submits when not due/leader
  private final AtomicLong lastOrphanSubmitEpochMs = new AtomicLong(0);
  // Local submission gate for zombie cleanup to avoid per-second submits
  private final AtomicLong lastZombieSubmitEpochMs = new AtomicLong(0);

  // Watchdog monitoring: detects operational anomalies via ratio-based heuristics.
  //
  // Four anomaly types are tracked, each with a streak counter and last-trigger timestamp:
  // - Leak: Available permits are low but pool is idle (possible permit leak)
  // - Skew: Active agents don't match held permits (capacity accounting mismatch)
  // - ZeroProgress: Ready agents exist but none acquired (acquisition stall)
  // - RedisStall: Redis circuit breaker is open (connectivity issues)
  //
  // Streak counters increment on consecutive anomaly detections and reset on recovery.
  // When a streak exceeds a threshold (typically 3 consecutive cycles), a warning is
  // recorded and surfaced in the periodic health summary. This avoids log spam from
  // transient conditions while still alerting on sustained issues.
  //
  // These counters are single-threaded (updated only in the scheduler loop) so they
  // do not require synchronization. The timestamps are atomic for safe reads during
  // health summary logging.
  private int watchdogLeakStreak = 0;
  private int watchdogSkewStreak = 0;
  private int watchdogZeroProgressStreak = 0;
  private int watchdogRedisStallStreak = 0;

  private final java.util.concurrent.atomic.AtomicLong watchdogLeakLastEpochMs =
      new java.util.concurrent.atomic.AtomicLong(0L);
  private final java.util.concurrent.atomic.AtomicLong watchdogSkewLastEpochMs =
      new java.util.concurrent.atomic.AtomicLong(0L);
  private final java.util.concurrent.atomic.AtomicLong watchdogZeroProgressLastEpochMs =
      new java.util.concurrent.atomic.AtomicLong(0L);
  private final java.util.concurrent.atomic.AtomicLong watchdogRedisStallLastEpochMs =
      new java.util.concurrent.atomic.AtomicLong(0L);

  // Sustained degraded backlog detection (code-level alerting)
  private long degradedBacklogStartEpochMs = 0L;
  private long degradedBacklogBaselineOldestOverdueSec = 0L;

  /**
   * Creates a PriorityAgentScheduler with required dependencies.
   *
   * @param jedisPool Redis connection pool
   * @param nodeStatusProvider Node enablement state provider
   * @param shardingFilter Shard ownership filter
   * @param agentProperties Agent configuration
   * @param schedulerProperties Scheduler configuration
   * @param metrics Metrics registry
   */
  public PriorityAgentScheduler(
      JedisPool jedisPool,
      NodeStatusProvider nodeStatusProvider,
      AgentIntervalProvider intervalProvider,
      ShardingFilter shardingFilter,
      PriorityAgentProperties agentProperties,
      PrioritySchedulerProperties schedulerProperties,
      PrioritySchedulerMetrics metrics) {

    // Initialize services with defensive null checking (use NOOP if null)
    this.metrics = metrics != null ? metrics : PrioritySchedulerMetrics.NOOP;
    this.scriptManager = new RedisScriptManager(jedisPool, this.metrics);
    this.config = new PrioritySchedulerConfiguration(agentProperties, schedulerProperties);
    this.acquisitionService =
        new AgentAcquisitionService(
            jedisPool,
            scriptManager,
            intervalProvider,
            shardingFilter,
            agentProperties,
            schedulerProperties,
            this.metrics);
    this.zombieService =
        new ZombieCleanupService(jedisPool, scriptManager, schedulerProperties, metrics);
    this.orphanService =
        new OrphanCleanupService(jedisPool, scriptManager, schedulerProperties, metrics);

    // Set up service references for advanced cleanup processing
    this.orphanService.setAcquisitionService(this.acquisitionService);
    // Provide acquisition service for zombie cleanup coordination
    this.zombieService.setAcquisitionService(this.acquisitionService);

    // Store external dependencies
    this.nodeStatusProvider = nodeStatusProvider;
    this.shardingFilter = shardingFilter;

    // Dedicated on-demand single-thread executors so the scheduler loop never blocks.
    // Threads are created only when needed and time out when idle for cleaner metrics.
    // For zombie cleanup, prefer to keep the worker thread parked when budget=0 by using the
    // zombie-cleanup interval as keep-alive. With a positive budget, keep-alive equals the budget
    // so the worker retires shortly after work finishes.
    this.zombieCleanupExecutor =
        newOnDemandSingleThreadExecutor(
            "PriorityAgentCleanup-Zombie-#",
            config.getZombieRunBudgetMs() > 0
                ? config.getZombieRunBudgetMs()
                : config.getZombieIntervalMs());
    // For orphan cleanup, when runBudgetMs=0 we intentionally keep the worker thread around in
    // TIMED_WAITING between passes by using the cleanup interval as the keep-alive. This avoids
    // thread churn and makes APM attribution clearer. When a positive budget is configured, use it
    // as the keep-alive so the worker retires shortly after work finishes.
    this.orphanCleanupExecutor =
        newOnDemandSingleThreadExecutor(
            "PriorityAgentCleanup-Orphan-#",
            config.getOrphanRunBudgetMs() > 0
                ? config.getOrphanRunBudgetMs()
                : config.getOrphanIntervalMs());
    // For reconcile, fall back to the Redis refresh cadence when no budget is set, to keep the
    // worker thread parked between reconciliation passes.
    this.reconcileExecutor =
        newOnDemandSingleThreadExecutor(
            "PriorityAgentReconcile-#",
            config.getReconcileRunBudgetMs() > 0
                ? config.getReconcileRunBudgetMs()
                : Math.max(1_000L, (long) config.getRedisRefreshPeriod() * 1_000L));

    // Register shared gauges once
    try {
      this.metrics.registerGauges(
          jedisPool,
          () -> (double) acquisitionService.getRegisteredAgentCount(),
          () -> (double) acquisitionService.getActiveAgentCount(),
          () -> (double) acquisitionService.getReadyCountSnapshot(),
          () -> (double) acquisitionService.getOldestOverdueSeconds(),
          () -> acquisitionService.isDegraded() ? 1 : 0,
          () -> (double) acquisitionService.getCapacityPerCycleSnapshot(),
          () -> {
            if (config.getAgentWorkPool() instanceof java.util.concurrent.ThreadPoolExecutor) {
              return (double)
                  ((java.util.concurrent.ThreadPoolExecutor) config.getAgentWorkPool())
                      .getQueue()
                      .size();
            }
            return -1d;
          },
          () ->
              config.getMaxConcurrentSemaphore() != null
                  ? config.getMaxConcurrentSemaphore().availablePermits()
                  : -1,
          () -> (double) acquisitionService.getCompletionQueueSize(),
          () -> (double) acquisitionService.getServerClientOffsetMs(),
          () -> {
            double cap = acquisitionService.getCapacityPerCycleSnapshot();
            double ready = acquisitionService.getReadyCountSnapshot();
            return cap > 0 ? (ready / cap) : 0;
          });
    } catch (Exception e) {
      log.debug("Failed to register scheduler gauges", e);
    }

    // Register executor gauges if using ThreadPoolExecutor
    if (config.getAgentWorkPool() instanceof java.util.concurrent.ThreadPoolExecutor) {
      this.metrics.registerExecutorGauges(
          (java.util.concurrent.ThreadPoolExecutor) config.getAgentWorkPool());
    }

    log.info("PriorityAgentScheduler initialized successfully");
  }

  /** Initialize the scheduler and start the periodic execution. */
  @PostConstruct
  public void initialize() {
    try {
      // Initialize Redis scripts
      scriptManager.initializeScripts();
      log.info("Redis scripts initialized: {} scripts loaded", scriptManager.getScriptCount());

      // Start the scheduler
      startScheduler();
      running.set(true);

      log.info("PriorityAgentScheduler started successfully");
    } catch (Exception e) {
      log.error("Failed to initialize PriorityAgentScheduler", e);
      throw new AgentSchedulingException("Scheduler initialization failed", e);
    }
  }

  /**
   * Main scheduler execution loop - called periodically by ScheduledExecutorService. Orchestrates
   * agent scheduling, cleanup operations, and health monitoring.
   */
  @Override
  public void run() {
    if (!nodeStatusProvider.isNodeEnabled()) {
      return;
    }

    try {
      long start = nowMs();
      long currentRun = runCount.incrementAndGet();
      log.debug("Starting scheduler run cycle {}", currentRun);

      // Reconcile agent registrations with current sharding/enablement state (offloaded)
      long refreshPeriodMs = Math.max(1, config.getRedisRefreshPeriod()) * 1000L;
      boolean reconcileDue = isPeriodElapsed(lastReconcileEpochMs.get(), refreshPeriodMs);
      if (reconcileDue && reconcileRunning.compareAndSet(false, true)) {
        if (log.isDebugEnabled()) {
          log.debug("Begin reconcileKnownAgents offload for run {}", currentRun);
        }
        reconcileExecutor.submit(
            () -> {
              try {
                reconcileKnownAgentsIfNeeded(currentRun);
              } catch (Exception e) {
                log.warn("Reconcile known agents failed", e);
                try {
                  metrics.incrementRunFailure(e.getClass().getSimpleName());
                } catch (Exception me) {
                  log.debug("Failed to record reconcile failure metric", me);
                }
              } finally {
                reconcileRunning.set(false);
                if (log.isDebugEnabled()) {
                  log.debug("End reconcileKnownAgents offload for run {}", currentRun);
                }
              }
            });
      } else if (log.isDebugEnabled()) {
        if (!reconcileDue) {
          log.debug("Skipping reconcile submission: refresh period not elapsed");
        } else {
          log.debug("Skipping reconcileKnownAgents: previous run still in progress");
        }
      }

      // Check if Redis repopulation is due. If we repopulate, skip acquisition this cycle
      // to let initial registration jitter settle - prevents all new agents from executing
      // immediately on first scheduler cycle (which would defeat jitter purpose)
      boolean repopulatedThisCycle = acquisitionService.repopulateIfDueNow();

      // Acquire ready agents and submit them for execution first to guarantee forward progress
      // Skip if we just repopulated to allow jitter-based score distribution to take effect
      int agentsAcquired = 0;
      if (!repopulatedThisCycle) {
        agentsAcquired =
            acquisitionService.saturatePool(
                currentRun, config.getMaxConcurrentSemaphore(), config.getAgentWorkPool());
      } else {
        log.debug(
            "Skipping acquisition on repopulation cycle {} to allow jitter distribution",
            currentRun);
      }

      // Watchdog: detect possible permit starvation and related stalls via ratio-based heuristics
      try {
        java.util.concurrent.Semaphore maxConcurrentSemaphore = config.getMaxConcurrentSemaphore();
        int availablePermitsNow =
            maxConcurrentSemaphore != null ? maxConcurrentSemaphore.availablePermits() : -1;
        int poolActive = 0;
        if (config.getAgentWorkPool() instanceof java.util.concurrent.ThreadPoolExecutor) {
          poolActive =
              ((java.util.concurrent.ThreadPoolExecutor) config.getAgentWorkPool())
                  .getActiveCount();
        }
        int maxConcurrent =
            acquisitionService != null
                ? acquisitionService.getAgentProperties().getMaxConcurrentAgents()
                : 0;
        int activeCount = acquisitionService.getActiveAgentCount();
        long ready = acquisitionService.getReadyCountSnapshot();
        int effectiveCapacity = Math.max(0, maxConcurrent - activeCount);
        double permitsFreeRatio =
            maxConcurrent > 0
                ? Math.max(0d, Math.min(1d, (double) availablePermitsNow / (double) maxConcurrent))
                : 0d;
        double activeRatio =
            maxConcurrent > 0
                ? Math.max(0d, Math.min(1d, (double) activeCount / (double) maxConcurrent))
                : 0d;
        double acquiredFillRatio =
            effectiveCapacity > 0
                ? Math.max(0d, Math.min(1d, (double) agentsAcquired / (double) effectiveCapacity))
                : 0d;

        boolean redisStall = false;
        try {
          // Prefer the exact breaker state over formatted status text so CLOSED variants like
          // "CLOSED (failures=0/5)" do not trigger false stall alerts.
          State redisState = acquisitionService.getRedisCircuitBreakerState();
          redisStall = redisState != null && redisState != State.CLOSED;
        } catch (Exception e) {
          log.debug("Watchdog: unable to read redis breaker state; assuming CLOSED", e);
        }

        evaluateWatchdog(
            permitsFreeRatio,
            activeRatio,
            acquiredFillRatio,
            ready,
            poolActive,
            agentsAcquired,
            redisStall,
            maxConcurrent,
            activeCount,
            effectiveCapacity,
            availablePermitsNow);

        // Simple permit-starvation detector: no permits available and pool idle for N consecutive
        // cycles.
        // This often correlates with degraded health when there's a backlog but no effective
        // execution.
        try {
          int consecutive = permitStarvationConsecutive.get();
          if (maxConcurrentSemaphore != null && availablePermitsNow == 0 && poolActive == 0) {
            consecutive = permitStarvationConsecutive.incrementAndGet();
          } else {
            permitStarvationConsecutive.set(0);
            consecutive = 0;
          }
          // Tie into degraded reasoning: if degraded and sustained starvation, surface a clear WARN
          boolean degraded = acquisitionService.isDegraded();
          if (consecutive >= 3 && degraded) {
            long degradedMs =
                degradedBacklogStartEpochMs > 0L ? (nowMs() - degradedBacklogStartEpochMs) : 0L;
            lastStarvationSuspected.set(true);
            lastStarvationTicks.set(consecutive);
            lastStarvationDegradedMs.set(degradedMs);
          } else {
            lastStarvationSuspected.set(false);
            lastStarvationTicks.set(0);
            lastStarvationDegradedMs.set(0L);
          }
        } catch (Exception ignore) {
        }

        // Track degraded backlog window without emitting immediate warnings; folded into summary
        boolean degraded = acquisitionService.isDegraded();
        long oldestOverdueSec = acquisitionService.getOldestOverdueSeconds();
        if (degraded && ready > 0) {
          if (degradedBacklogStartEpochMs == 0L) {
            degradedBacklogStartEpochMs = nowMs();
            degradedBacklogBaselineOldestOverdueSec = oldestOverdueSec;
          } else {
            // If backlog continues worsening, update baseline for future comparisons
            if (oldestOverdueSec > degradedBacklogBaselineOldestOverdueSec) {
              degradedBacklogBaselineOldestOverdueSec = oldestOverdueSec;
            }
          }
        } else {
          // Reset if not degraded or no backlog
          degradedBacklogStartEpochMs = 0L;
          degradedBacklogBaselineOldestOverdueSec = 0L;
        }
      } catch (Exception e) {
        log.debug("Watchdog check failed; continuing", e);
      }

      // Offload zombie cleanup (non-blocking) — pre-gated by enabled check and cadence
      try {
        // Skip zombie cleanup entirely if disabled - avoids snapshot creation overhead
        if (!config.isZombieCleanupEnabled()) {
          if (log.isDebugEnabled()) {
            log.debug("Skipping zombie cleanup: disabled via configuration");
          }
        } else {
          // Check for hung cleanup: if running too long, forcibly cancel to prevent permanent
          // failure
          long zombieStartMs = zombieCleanupStartMs.get();
          if (zombieStartMs > 0 && zombieCleanupRunning.get()) {
            long budgetMs = config.getZombieRunBudgetMs();
            // External timeout = budget + 30s margin (or 60s if no budget configured)
            long externalTimeoutMs = budgetMs > 0 ? budgetMs + 30000L : 60000L;
            long elapsedMs = nowMs() - zombieStartMs;
            if (elapsedMs > externalTimeoutMs) {
              log.error(
                  "Zombie cleanup hung for {}ms (timeout={}ms), forcibly cancelling to recover",
                  elapsedMs,
                  externalTimeoutMs);
              try {
                java.util.concurrent.Future<?> f = zombieCleanupFuture;
                if (f != null) {
                  f.cancel(true);
                }
                metrics.incrementCleanupTimeout("zombie");
              } catch (Exception ce) {
                log.debug("Failed to cancel hung zombie cleanup", ce);
              }
              resetZombieCleanupState();
            }
          }

          long zombieIntervalMs = config.getZombieIntervalMs();
          long lastZombieSubmit = lastZombieSubmitEpochMs.get();
          boolean zombieDueToSubmit = isPeriodElapsed(lastZombieSubmit, zombieIntervalMs);
          if (zombieDueToSubmit && zombieCleanupRunning.compareAndSet(false, true)) {
            lastZombieSubmitEpochMs.set(nowMs());
            zombieCleanupStartMs.set(nowMs());
            zombieCleanupFuture =
                zombieCleanupExecutor.submit(
                    () -> {
                      try {
                        java.util.Map<String, String> activeAgentsSnapshot =
                            new java.util.HashMap<>(acquisitionService.getActiveAgentsMap());
                        java.util.Map<String, java.util.concurrent.Future<?>> futuresSnapshot =
                            new java.util.HashMap<>(acquisitionService.getActiveAgentsFutures());
                        long startTs = nowMs();
                        long budgetMs = config.getZombieRunBudgetMs();
                        zombieService.cleanupZombieAgentsIfNeeded(
                            activeAgentsSnapshot, futuresSnapshot);
                        if (budgetMs > 0 && nowMs() - startTs > budgetMs) {
                          log.info(
                              "Zombie cleanup exceeded budget {}ms (elapsed={}ms); subsequent work will be deferred",
                              budgetMs,
                              nowMs() - startTs);
                          // Cooperative hard stop: interrupt to signal budget breach
                          Thread.currentThread().interrupt();
                        }
                      } catch (Exception e) {
                        if (e instanceof InterruptedException) {
                          Thread.currentThread().interrupt();
                          return;
                        }
                        log.warn("Zombie cleanup failed", e);
                        try {
                          metrics.incrementRunFailure(e.getClass().getSimpleName());
                        } catch (Exception me) {
                          log.debug("Failed to record zombie cleanup failure metric", me);
                        }
                      } finally {
                        resetZombieCleanupState();
                      }
                    });
          } else {
            if (!zombieDueToSubmit) {
              if (log.isDebugEnabled()) {
                log.debug("Skipping zombie cleanup submission: interval not elapsed");
              }
            } else {
              // Cleanup skipped because previous run still in progress - track for monitoring
              if (log.isDebugEnabled()) {
                log.debug("Skipping zombie cleanup: previous run still in progress");
              }
              try {
                metrics.incrementCleanupSkipped("zombie");
              } catch (Exception me) {
                log.debug("Failed to record cleanup skipped metric", me);
              }
            }
          }
        }
      } catch (Exception e) {
        log.warn("Failed to schedule zombie cleanup", e);
      }

      // Offload orphan cleanup (non-blocking) — pre-gated by cadence to avoid per-second submits
      try {
        // Check for hung cleanup: if running too long, forcibly cancel to prevent permanent failure
        long orphanStartMs = orphanCleanupStartMs.get();
        if (orphanStartMs > 0 && orphanCleanupRunning.get()) {
          long budgetMs = config.getOrphanRunBudgetMs();
          // External timeout = budget + 30s margin (or 60s if no budget configured)
          long externalTimeoutMs = budgetMs > 0 ? budgetMs + 30000L : 60000L;
          long elapsedMs = nowMs() - orphanStartMs;
          if (elapsedMs > externalTimeoutMs) {
            log.error(
                "Orphan cleanup hung for {}ms (timeout={}ms), forcibly cancelling to recover",
                elapsedMs,
                externalTimeoutMs);
            try {
              java.util.concurrent.Future<?> f = orphanCleanupFuture;
              if (f != null) {
                f.cancel(true);
              }
              metrics.incrementCleanupTimeout("orphan");
            } catch (Exception ce) {
              log.debug("Failed to cancel hung orphan cleanup", ce);
            }
            resetOrphanCleanupState();
          }
        }

        long intervalMs = config.getOrphanIntervalMs();
        long lastSubmit = lastOrphanSubmitEpochMs.get();
        boolean dueToSubmit = isPeriodElapsed(lastSubmit, intervalMs);
        if (dueToSubmit && orphanCleanupRunning.compareAndSet(false, true)) {
          lastOrphanSubmitEpochMs.set(nowMs());
          orphanCleanupStartMs.set(nowMs());
          orphanCleanupFuture =
              orphanCleanupExecutor.submit(
                  () -> {
                    try {
                      long startTs = nowMs();
                      long budgetMs = config.getOrphanRunBudgetMs();
                      orphanService.cleanupOrphanedAgentsIfNeeded();
                      if (budgetMs > 0 && nowMs() - startTs > budgetMs) {
                        log.info(
                            "Orphan cleanup exceeded budget {}ms (elapsed={}ms); subsequent work will be deferred",
                            budgetMs,
                            nowMs() - startTs);
                        Thread.currentThread().interrupt();
                        return;
                      }
                    } catch (Exception e) {
                      if (e instanceof InterruptedException) {
                        Thread.currentThread().interrupt();
                        return;
                      }
                      log.warn("Orphan cleanup failed", e);
                      try {
                        metrics.incrementRunFailure(e.getClass().getSimpleName());
                      } catch (Exception me) {
                        log.debug("Failed to record orphan cleanup failure metric", me);
                      }
                    } finally {
                      resetOrphanCleanupState();
                    }
                  });
        } else {
          if (!dueToSubmit) {
            if (log.isDebugEnabled()) {
              log.debug("Skipping orphan cleanup submission: interval not elapsed");
            }
          } else {
            // Cleanup skipped because previous run still in progress - track for monitoring
            if (log.isDebugEnabled()) {
              log.debug("Skipping orphan cleanup: previous run still in progress");
            }
            try {
              metrics.incrementCleanupSkipped("orphan");
            } catch (Exception me) {
              log.debug("Failed to record cleanup skipped metric", me);
            }
          }
        }
      } catch (Exception e) {
        log.warn("Failed to schedule orphan cleanup", e);
      }

      if (log.isDebugEnabled() && agentsAcquired > 0) {
        log.debug(
            "Scheduler run cycle {} completed: {} agents acquired", currentRun, agentsAcquired);
      }

      // Log health summary every 10 minutes (time-based, not cycle-based)
      maybeLogHealthSummary();

      metrics.recordRunCycle(true, nowMs() - start);

      // Design note: This is the only broad catch(Throwable) in the scheduler by intent.
      // - Purpose: ensure the periodic scheduler loop never dies due to unexpected Errors or
      //   unchecked Throwables (e.g., linkage errors, OOMEs bubbling up, rare VM errors).
      // - Policy: all inner blocks use catch(Exception) and explicitly handle InterruptedException
      //   (restoring interrupt) to allow cooperative cancellation. Only this outer guard remains
      //   a safety net to preserve liveness of the scheduling thread.
    } catch (Throwable t) {
      log.error("Critical error in scheduler run cycle {}", runCount.get(), t);
      metrics.incrementRunFailure(t.getClass().getSimpleName());
      metrics.recordRunCycle(false, 0);
      // Continue scheduler operation despite errors - resilient to failures
    }
  }

  /**
   * Emit a periodic health summary at most once per configured period (default: 10 minutes).
   *
   * <p>The log is human-scannable groupings that match operational concerns:
   *
   * <ul>
   *   <li>Leading segment: overall scheduler health (`health=HEALTHY|DEGRADED`) with optional
   *       reasons such as `permit_mismatch` or degraded backlog cause tokens.
   *   <li>`[agents ...]`: registered agents plus active/futures counts, collapsing to
   *       `active=futures=<n>` when they match.
   *   <li>`[backlog ...]`: queued work snapshot (ready count, oldest overdue seconds, and
   *       `capacityPerCycle`).
   *   <li>`[permits ...]`: semaphore availability rendered as `available/max (percent)` when the
   *       semaphore is enabled, or `n/a` when disabled.
   *   <li>`[cleanup ...]`: zombie/orphan cleanup totals to track maintenance work.
   *   <li>Tail segments contain queue depth, active watchdog triggers, and optional starvation
   *       annotations.
   * </ul>
   */
  private void maybeLogHealthSummary() {
    long now = nowMs();
    long periodMs = config.getHealthSummaryPeriodMs();
    if (periodMs <= 0L) {
      return; // disabled
    }
    long last = lastHealthLogEpochMs.get();
    if (!com.netflix.spinnaker.cats.redis.cluster.support.CadenceGuard.isPeriodElapsed(
        last, periodMs)) {
      return;
    }
    if (!lastHealthLogEpochMs.compareAndSet(last, now)) {
      return; // another thread logged
    }

    SchedulerStats stats = getStats();
    int queueDepth = -1;
    if (config.getAgentWorkPool() instanceof java.util.concurrent.ThreadPoolExecutor) {
      queueDepth =
          ((java.util.concurrent.ThreadPoolExecutor) config.getAgentWorkPool()).getQueue().size();
    }
    java.util.concurrent.Semaphore maxConcurrentSemaphore = config.getMaxConcurrentSemaphore();
    int maxConcurrent = acquisitionService.getAgentProperties().getMaxConcurrentAgents();

    // IMPORTANT: Read order matters for accurate permit leak detection.
    // Read runStates FIRST, then semaphore. This ensures:
    // - If worker completes between reads: runStates stale (high), semaphore accurate (low)
    //   → held < withPermit (safe, not a false positive)
    // - Real leak: semaphore holds permit without RunState → held > withPermit (true positive)
    // Previous order (semaphore first) caused false positives when workers completed between reads.
    int runStatesCount = acquisitionService.getRunStatesCount();
    int runStatesWithPermitHeld = acquisitionService.getRunStatesWithPermitHeldCount();
    int availablePermits =
        maxConcurrentSemaphore != null ? maxConcurrentSemaphore.availablePermits() : -1;

    int activeCount = acquisitionService.getActiveAgentCount();
    long readySnapshot = acquisitionService.getReadyCountSnapshot();
    long oldestOverdueSecondsNow = acquisitionService.getOldestOverdueSeconds();
    double capacityPerCycle = acquisitionService.getCapacityPerCycleSnapshot();

    // Permit accounting: track difference between held permits and active agents.
    // Small mismatches are EXPECTED during in-flight acquisition because permits are
    // acquired before runStates/activeAgents are populated. This is NOT a leak - it's a
    // transient timing window. Only heldPermits > runStatesWithPermitHeld indicates a true leak.
    int permitMismatchValue = 0;
    int heldPermits = 0;
    if (maxConcurrentSemaphore != null && maxConcurrent > 0) {
      heldPermits = Math.max(0, maxConcurrent - availablePermits);
      permitMismatchValue = heldPermits - activeCount;
      // Log diagnostic details at DEBUG level for investigation
      if (permitMismatchValue > 0 && log.isDebugEnabled()) {
        log.debug(
            "Permit accounting: held={} active={} mismatch={} (expected during in-flight acquisition). "
                + "Leak suspected only if held > runStatesWithPermit: held={} vs withPermit={}",
            heldPermits,
            activeCount,
            permitMismatchValue,
            heldPermits,
            runStatesWithPermitHeld);
      }
      // Record metric for observability (not used for health status)
      metrics.recordPermitMismatch(permitMismatchValue);
    }

    // Set consistency check: verify no agent exists in both waiting and working sets
    AgentAcquisitionService.ConsistencyCheckResult consistencyResult =
        acquisitionService.checkSetConsistency(50);
    boolean setOverlap = consistencyResult.hasViolations();

    // Consolidate watchdog triggers in the last 10 minutes
    java.util.List<String> watchdogs = new java.util.ArrayList<>();
    long ttlMs = periodMs;
    if (watchdogLeakLastEpochMs.get() > 0 && (now - watchdogLeakLastEpochMs.get()) < ttlMs) {
      watchdogs.add("permit_leak_suspect");
    }
    if (watchdogSkewLastEpochMs.get() > 0 && (now - watchdogSkewLastEpochMs.get()) < ttlMs) {
      watchdogs.add("capacity_skew");
    }
    if (watchdogZeroProgressLastEpochMs.get() > 0
        && (now - watchdogZeroProgressLastEpochMs.get()) < ttlMs) {
      watchdogs.add("zero_progress");
    }
    if (watchdogRedisStallLastEpochMs.get() > 0
        && (now - watchdogRedisStallLastEpochMs.get()) < ttlMs) {
      watchdogs.add("redis_stall");
    }

    // Note: permitMismatch is NOT included in warnLevel because small mismatches are expected
    // during in-flight acquisition (permits acquired before runStates created). The diagnostic
    // data is still included in the log for investigation if needed.
    boolean warnLevel = (stats.isDegraded() || setOverlap);
    String healthLabel = warnLevel ? "DEGRADED" : "HEALTHY";

    java.util.List<String> reasonTokens = new java.util.ArrayList<>();
    if (setOverlap) {
      reasonTokens.add("set_overlap");
    }
    if (stats.isDegraded()) {
      String degradedReason = stats.getDegradedReason();
      if (degradedReason != null && !degradedReason.isEmpty()) {
        reasonTokens.add(degradedReason);
      }
    }

    StringBuilder msg = new StringBuilder("Scheduler health | health=").append(healthLabel);
    if (!reasonTokens.isEmpty()) {
      msg.append(" reason=").append(String.join("; ", reasonTokens));
    }

    int registeredAgents = stats.getRegisteredAgents();
    int activeAgents = stats.getActiveAgents();
    int futuresCount = acquisitionService.getFuturesMapSize();
    int scriptsCount = scriptManager.getScriptCount();

    if (activeAgents == futuresCount) {
      msg.append(
          String.format(
              " | [agents registered=%d active=futures=%d scripts=%d]",
              registeredAgents, activeAgents, scriptsCount));
    } else {
      msg.append(
          String.format(
              " | [agents registered=%d active=%d futures=%d scripts=%d]",
              registeredAgents, activeAgents, futuresCount, scriptsCount));
    }

    msg.append(
        String.format(
            " [backlog ready=%s oldest_overdue=%ss capacity_per_cycle=%s]",
            formatLong(readySnapshot),
            formatLong(oldestOverdueSecondsNow),
            formatDouble(capacityPerCycle)));

    if (maxConcurrentSemaphore == null || maxConcurrent <= 0) {
      msg.append(" [permits n/a]");
    } else {
      double freeRatio =
          Math.max(0d, Math.min(1d, (double) availablePermits / (double) maxConcurrent));
      msg.append(
          String.format(
              " [permits %s/%s (%.1f%%)]",
              formatLong(availablePermits), formatLong(maxConcurrent), freeRatio * 100d));
    }

    // Add runStates diagnostic to help identify permit_mismatch root cause
    // Key invariant: heldPermits should equal runStatesWithPermit (no leak)
    // If heldPermits > runStatesWithPermit: actual permit leak
    // If heldPermits == runStatesWithPermit but > activeCount: timing difference only
    msg.append(
        String.format(
            " [runStates=%d withPermit=%d held=%d]",
            runStatesCount, runStatesWithPermitHeld, heldPermits));

    msg.append(
        String.format(
            " [cleanup zombies_cleaned=%s orphans_cleaned=%s]",
            formatLong(stats.getZombiesCleanedUp()), formatLong(stats.getOrphansCleanedUp())));

    // Append consistency check results
    if (consistencyResult.getSampled() > 0) {
      msg.append(
          String.format(
              " [consistency violations=%d sampled=%d]",
              consistencyResult.getViolations(), consistencyResult.getSampled()));
    }

    msg.append(" queue_depth=" + formatLong(queueDepth));

    if (!watchdogs.isEmpty()) {
      msg.append(" watchdogs=").append(String.join(",", watchdogs));
    }

    if (lastStarvationSuspected.get()) {
      msg.append(
          String.format(
              " [starvation suspected: degraded_for_ms=%d ticks=%d]",
              lastStarvationDegradedMs.get(), lastStarvationTicks.get()));
    }

    String message = msg.toString();
    if (warnLevel) {
      log.warn(message);
    } else {
      log.info(message);
    }
  }

  /** Format a long value for log output without locale-specific separators. */
  private static String formatLong(long value) {
    return Long.toString(value);
  }

  /** Format a double with either zero or two fractional digits, depending on precision. */
  private static String formatDouble(double value) {
    if (Double.isFinite(value)) {
      if (value == Math.rint(value)) {
        return String.format("%.0f", value);
      }
      return String.format("%.2f", value);
    }
    return "NaN";
  }

  /**
   * Register an agent for scheduling.
   *
   * @param agent The agent to register
   * @param agentExecution Agent execution callback
   * @param executionInstrumentation Metrics instrumentation
   */
  @Override
  public void schedule(
      Agent agent,
      AgentExecution agentExecution,
      ExecutionInstrumentation executionInstrumentation) {
    // Always track the agent so that we can re-balance on shard changes
    knownAgents.put(
        agent.getAgentType(), new KnownAgent(agent, agentExecution, executionInstrumentation));

    if (!isAgentEnabled(agent)) {
      log.debug("Agent {} not enabled, skipping registration", agent.getAgentType());
      return;
    }

    // Set up agent scheduler awareness
    if (agent instanceof AgentSchedulerAware) {
      ((AgentSchedulerAware) agent).setAgentScheduler(this);
    }

    // Register with acquisition service (it will log registration details)
    acquisitionService.registerAgent(agent, agentExecution, executionInstrumentation);

    log.debug("Registered agent {} for scheduling", agent.getAgentType());
  }

  /**
   * Unregister an agent from scheduling.
   *
   * @param agent The agent to unregister
   */
  @Override
  public void unschedule(Agent agent) {
    // Scheduler-scoped safeguard: avoid unscheduling shared regional instance-type agent
    String agentType = agent != null ? agent.getAgentType() : null;
    if (agentType != null && isSharedRegionalClassRegionPattern(agent, agentType)) {
      if (log.isDebugEnabled()) {
        log.debug("Ignoring unschedule for shared regional agent {}", agentType);
      }
      return;
    }
    acquisitionService.unregisterAgent(agent);
    log.debug("Unregistered agent {} from scheduling", agentType);
    knownAgents.remove(agentType);
  }

  /**
   * Detects the AWS instance-type agent that is intentionally shared per-region across accounts and
   * uses the pattern "AmazonInstanceTypeCachingAgent/<region>" (no account prefix).
   */
  private boolean isSharedRegionalClassRegionPattern(Agent agent, String agentType) {
    try {
      // Only gate for AWS providers; other providers include account in agentType
      String provider = agent != null ? agent.getProviderName() : null;
      if (provider == null || !provider.toLowerCase().contains("aws")) {
        return false;
      }
      int first = agentType.indexOf('/');
      int last = agentType.lastIndexOf('/');
      if (!(first > 0 && first == last && first < agentType.length() - 1)) {
        return false;
      }
      // parts[0] should look like a ClassName ending with CachingAgent
      String classPart = agentType.substring(0, first);
      if (!classPart.matches("[A-Z][A-Za-z0-9]*CachingAgent")) {
        return false;
      }
      // parts[1] should look like an AWS region
      String regionPart = agentType.substring(first + 1);
      return regionPart.matches("[a-z]{2}-[a-z]+-\\d");
    } catch (Exception ignore) {
      return false;
    }
  }

  /**
   * Try to manually lock an agent for execution.
   *
   * @param agent The agent to lock
   * @return AgentLock if successful, null if agent is already locked or unavailable
   */
  public AgentLock tryLock(Agent agent) {
    try {
      // Use acquisition service to try to acquire the agent
      return acquisitionService.tryLockAgent(agent);
    } catch (Exception e) {
      log.warn("Failed to lock agent {}", agent.getAgentType(), e);
      return null;
    }
  }

  /**
   * Try to release a manually acquired agent lock.
   *
   * @param lock The lock to release
   * @return true if successfully released, false otherwise
   */
  public boolean tryRelease(AgentLock lock) {
    try {
      return acquisitionService.tryReleaseAgent(lock);
    } catch (Exception e) {
      log.warn("Failed to release agent lock {}", lock.getAgent().getAgentType(), e);
      return false;
    }
  }

  /**
   * Check if an agent lock is still valid.
   *
   * @param lock The lock to validate
   * @return true if lock is still valid, false otherwise
   */
  public boolean lockValid(AgentLock lock) {
    try {
      return acquisitionService.isLockValid(lock);
    } catch (Exception e) {
      log.warn("Failed to validate agent lock {}", lock.getAgent().getAgentType(), e);
      return false;
    }
  }

  /**
   * Check if the scheduler is atomic (always returns true for Redis-based scheduler).
   *
   * @return true
   */
  public boolean isAtomic() {
    return true;
  }

  /** Shutdown the scheduler and clean up resources with graceful agent re-queuing. */
  @PreDestroy
  public void shutdown() {
    log.info("Shutting down PriorityAgentScheduler");

    // Signal shutdown to prevent new work
    running.set(false);

    // Signal shutdown to all services for proper coordination
    acquisitionService.setShuttingDown(true);

    try {
      // Step 1: Gracefully release active agents back to waiting queue
      gracefullyReleaseActiveAgents();

      // Step 2: Stop the scheduler executor
      // Best-effort ThreadLocal cleanup on the scheduler thread before shutdown
      try {
        config
            .getSchedulerExecutorService()
            .submit(
                () -> {
                  try {
                    if (acquisitionService != null) {
                      acquisitionService.removeThreadLocals();
                    }
                  } catch (Exception ignore) {
                    log.debug("Failed to run ThreadLocal cleanup on scheduler executor", ignore);
                  }
                });
      } catch (Exception ignore) {
        log.debug("Failed to schedule ThreadLocal cleanup on scheduler executor", ignore);
      }
      config.getSchedulerExecutorService().shutdown();
      // Intentional: reuse orphan cleanup timeouts so executor shutdown behavior stays consistent
      // across scheduler/orphan flows.
      long schedAwait = config.getOrphanExecutorShutdownAwaitMs();
      long schedForceAwait = config.getOrphanExecutorShutdownForceAwaitMs();
      if (!config
          .getSchedulerExecutorService()
          .awaitTermination(schedAwait, TimeUnit.MILLISECONDS)) {
        log.warn("Scheduler executor did not terminate gracefully, forcing shutdown");
        config.getSchedulerExecutorService().shutdownNow();
        // Wait for forced termination
        if (!config
            .getSchedulerExecutorService()
            .awaitTermination(schedForceAwait, TimeUnit.MILLISECONDS)) {
          log.error("Scheduler executor failed to terminate even after forced shutdown");
        }
      }

      // Stop zombie cleanup executor
      // Best-effort ThreadLocal cleanup on owning thread before shutdown
      try {
        zombieCleanupExecutor.submit(
            () -> {
              try {
                if (zombieService != null) {
                  zombieService.removeThreadLocals();
                }
                if (acquisitionService != null) {
                  acquisitionService.removeThreadLocals();
                }
              } catch (Exception ignore) {
              }
            });
      } catch (Exception ignore) {
        log.debug("Failed to schedule ThreadLocal cleanup on zombie executor", ignore);
      }
      zombieCleanupExecutor.shutdown();
      long zombieAwait = config.getZombieExecutorShutdownAwaitMs();
      long zombieForceAwait = config.getZombieExecutorShutdownForceAwaitMs();
      if (!zombieCleanupExecutor.awaitTermination(zombieAwait, TimeUnit.MILLISECONDS)) {
        log.warn("Zombie cleanup executor did not terminate gracefully, forcing shutdown");
        zombieCleanupExecutor.shutdownNow();
        if (!zombieCleanupExecutor.awaitTermination(zombieForceAwait, TimeUnit.MILLISECONDS)) {
          log.error("Zombie cleanup executor failed to terminate after forced shutdown");
        }
      }

      // Stop orphan cleanup executor
      // Best-effort ThreadLocal cleanup on owning thread before shutdown
      try {
        orphanCleanupExecutor.submit(
            () -> {
              try {
                if (orphanService != null) {
                  orphanService.removeThreadLocals();
                }
                if (acquisitionService != null) {
                  acquisitionService.removeThreadLocals();
                }
              } catch (Exception ignore) {
              }
            });
      } catch (Exception ignore) {
        log.debug("Failed to schedule ThreadLocal cleanup on orphan executor", ignore);
      }
      orphanCleanupExecutor.shutdown();
      long orphanAwait = config.getOrphanExecutorShutdownAwaitMs();
      long orphanForceAwait = config.getOrphanExecutorShutdownForceAwaitMs();
      if (!orphanCleanupExecutor.awaitTermination(orphanAwait, TimeUnit.MILLISECONDS)) {
        log.warn("Orphan cleanup executor did not terminate gracefully, forcing shutdown");
        orphanCleanupExecutor.shutdownNow();
        if (!orphanCleanupExecutor.awaitTermination(orphanForceAwait, TimeUnit.MILLISECONDS)) {
          log.error("Orphan cleanup executor failed to terminate after forced shutdown");
        }
      }

      // Stop reconcile executor (best-effort)
      // Best-effort ThreadLocal cleanup on owning thread before shutdown
      try {
        reconcileExecutor.submit(
            () -> {
              try {
                if (acquisitionService != null) {
                  acquisitionService.removeThreadLocals();
                }
              } catch (Exception ignore) {
              }
            });
      } catch (Exception ignore) {
        log.debug("Failed to schedule ThreadLocal cleanup on reconcile executor", ignore);
      }
      reconcileExecutor.shutdown();
      long reconcileAwait = config.getReconcileExecutorShutdownAwaitMs();
      long reconcileForceAwait = config.getReconcileExecutorShutdownForceAwaitMs();
      if (!reconcileExecutor.awaitTermination(reconcileAwait, TimeUnit.MILLISECONDS)) {
        reconcileExecutor.shutdownNow();
        reconcileExecutor.awaitTermination(reconcileForceAwait, TimeUnit.MILLISECONDS);
      }

      // Step 3: Shutdown all services
      config.shutdown();

      log.info("PriorityAgentScheduler shutdown completed successfully");
    } catch (Exception e) {
      log.error("Error during scheduler shutdown", e);
    }
  }

  /**
   * Gracefully re-queues agents owned by this instance during shutdown.
   *
   * <p>Process: 1) Interrupt running futures and release permits 2) Conditionally move owned
   * working entries back to waiting if still owned (score match) 3) Perform a best-effort wait and
   * log outcomes
   */
  private void gracefullyReleaseActiveAgents() {
    // Get count of agents this instance is actively working on
    int activeCount = acquisitionService.getActiveAgentCount();
    if (activeCount == 0) {
      log.info("No active agents to release during shutdown");
      return;
    }

    log.info("Gracefully releasing {} active agents during shutdown", activeCount);

    try {
      // Set graceful shutdown flag (coordination marker for shutdown sequence)
      acquisitionService.setGracefulShutdown(true);

      // Phase 1: Interrupt any running futures
      Map<String, Future<?>> activeAgentsFutures =
          acquisitionService.getActiveAgentsFuturesSnapshot();
      int interrupted = 0;

      for (Map.Entry<String, Future<?>> entry : activeAgentsFutures.entrySet()) {
        String agentType = entry.getKey();
        Future<?> future = entry.getValue();

        try {
          if (future != null && !future.isDone()) {
            boolean cancelled = future.cancel(true);
            if (cancelled) {
              interrupted++;
              log.debug("Interrupted agent {} during shutdown", agentType);
            }

            // Permit is released when thread exits in worker finally block
          }
        } catch (Exception e) {
          log.debug("Failed to interrupt agent {}", agentType, e);
        }
      }

      // Brief wait for interrupted agents to complete their cleanup
      if (interrupted > 0) {
        Thread.sleep(100);
      }

      // Phase 2: Re-queue only agents this instance was actively working on
      // This prevents duplicate re-queuing in non-sharded multi-instance environments
      Map<String, String> activeAgentsSnapshot = acquisitionService.getActiveAgentsMap();
      int released = 0;

      for (Map.Entry<String, String> entry : activeAgentsSnapshot.entrySet()) {
        String agentType = entry.getKey();
        String expectedScore = entry.getValue(); // Score when we acquired the agent

        try {
          Agent agent = acquisitionService.getAgentByType(agentType);
          if (agent != null) {
            // Conditionally re-queue - only if agent still in working with expected score
            acquisitionService.forceRequeueAgentForShutdown(agent, expectedScore);
            released++;
            log.debug(
                "Attempted re-queue of active agent {} for post-restart execution", agentType);
          }
        } catch (Exception e) {
          log.warn("Failed to gracefully release active agent {} during shutdown", agentType, e);
        }
      }

      log.info(
          "Graceful shutdown: {} agents interrupted, {} active agents re-queued for restart",
          interrupted,
          released);

    } catch (Exception e) {
      log.error("Error during graceful agent release", e);
    }
  }

  /**
   * Periodically reconciles known agents with current sharding/enablement, registering newly-owned
   * agents and unregistering no-longer-owned ones without a restart.
   *
   * @param currentRun current scheduler cycle number
   */
  private void reconcileKnownAgentsIfNeeded(long currentRun) {
    try {
      long refreshPeriodSeconds = config.getRedisRefreshPeriod();
      long refreshPeriodMs = Math.max(1, refreshPeriodSeconds) * 1000L;
      long budgetMs = config.getReconcileRunBudgetMs();
      long start = nowMs();
      long last = lastReconcileEpochMs.get();
      if (!isPeriodElapsed(last, refreshPeriodMs)) {
        return;
      }
      lastReconcileEpochMs.set(start);

      for (KnownAgent ka : knownAgents.values()) {
        if (Thread.currentThread().isInterrupted()) {
          if (log.isDebugEnabled()) {
            log.debug("Reconcile pass stopping early due to interrupt");
          }
          break;
        }
        if (overBudget(start, budgetMs)) {
          log.info(
              "Reconcile pass stopping early due to budget deadline (elapsed={}ms, budget={}ms)",
              nowMs() - start,
              budgetMs);
          break;
        }
        Agent agent = ka.agent;
        boolean enabledNow = isAgentEnabled(agent);
        Agent registered = acquisitionService.getRegisteredAgent(agent.getAgentType());

        if (enabledNow && registered == null) {
          log.debug("Reconcile: registering newly-owned agent {}", agent.getAgentType());
          acquisitionService.registerAgent(agent, ka.execution, ka.instrumentation);
        } else if (!enabledNow && registered != null) {
          log.debug("Reconcile: unregistering no-longer-owned agent {}", agent.getAgentType());
          acquisitionService.unregisterAgent(agent);
        }
      }

      // Lightweight local state validation: ensure activeAgents keys belong to registered agents
      try {
        java.util.Map<String, String> active = acquisitionService.getActiveAgentsMap();
        for (java.util.Map.Entry<String, String> entry : active.entrySet()) {
          if (Thread.currentThread().isInterrupted()) {
            if (log.isDebugEnabled()) {
              log.debug("Reconcile validation stopping early due to interrupt");
            }
            break;
          }
          if (overBudget(start, budgetMs)) {
            log.info(
                "Reconcile validation stopping early due to budget deadline (elapsed={}ms, budget={}ms)",
                nowMs() - start,
                budgetMs);
            break;
          }
          String agentType = entry.getKey();
          String scoreString = entry.getValue();
          boolean numeric = scoreString != null && scoreString.matches("^\\d+$");
          if (acquisitionService.getRegisteredAgent(agentType) == null || !numeric) {
            // Check if agent is still executing (has RunState with permit held).
            // If so, skip cleanup - the worker's finally block will handle it.
            // This prevents race where watchdog removes RunState while worker is running,
            // causing "no RunState" warnings in worker's finally block.
            if (acquisitionService.isAgentCurrentlyExecuting(agentType)) {
              log.debug(
                  "Skipping reconcile cleanup for {} - agent still executing with permit held",
                  agentType);
              continue;
            }
            // Inconsistent local tracking; clean it up with permit release to avoid leaks.
            // Use removeActiveAgentWithPermitRelease to ensure the permit is also released,
            // preventing permit_mismatch where heldPermits > activeAgents.size().
            acquisitionService.removeActiveAgentWithPermitRelease(agentType);
            metrics.incrementStateInconsistentActive();
          }
        }
      } catch (Exception ignore) {
      }

      // Note: Numeric-only waiting member detection and any repair is handled by
      // OrphanCleanupService on cadence. No reconcile-time sampling is performed here.
    } catch (Exception e) {
      log.warn("Failed to reconcile known agents with current shard/config", e);
    }
  }

  /** Lightweight holder for a scheduled agent and its execution/instrumentation handles. */
  private static final class KnownAgent {
    final Agent agent;
    final AgentExecution execution;
    final ExecutionInstrumentation instrumentation;

    KnownAgent(Agent agent, AgentExecution execution, ExecutionInstrumentation instrumentation) {
      this.agent = agent;
      this.execution = execution;
      this.instrumentation = instrumentation;
    }
  }

  /** Test hook that forces an immediate reconciliation without waiting for cadence. */
  void reconcileKnownAgentsNow() {
    lastReconcileEpochMs.set(0);
    reconcileKnownAgentsIfNeeded(runCount.get());
  }

  /**
   * Test hook that forces orphan cleanup without waiting for interval checks.
   *
   * <p>Package-private for test access. Allows tests to trigger cleanup directly without
   * reflection.
   */
  void forceOrphanCleanup() {
    orphanService.forceCleanupOrphanedAgents();
  }

  /**
   * Get scheduler statistics.
   *
   * @return SchedulerStats with current metrics
   */
  public SchedulerStats getStats() {
    return new SchedulerStats(
        runCount.get(),
        acquisitionService.getRegisteredAgentCount(),
        acquisitionService.getActiveAgentCount(),
        zombieService.getZombiesCleanedUp(),
        orphanService.getOrphansCleanedUp(),
        running.get(),
        acquisitionService.isDegraded(),
        acquisitionService.getDegradedReason(),
        acquisitionService.getOldestOverdueSeconds());
  }

  /** Starts the periodic scheduler execution at the configured interval. */
  private void startScheduler() {
    long intervalMs = config.getSchedulerIntervalMs();

    config
        .getSchedulerExecutorService()
        .scheduleAtFixedRate(
            this,
            intervalMs, // Initial delay
            intervalMs, // Period
            TimeUnit.MILLISECONDS);

    log.info("Scheduler started with interval {}ms", intervalMs);
  }

  /**
   * Determines whether an agent is eligible for scheduling on this node.
   *
   * <p>Checks shard ownership, enabled pattern, and disabled pattern.
   */
  private boolean isAgentEnabled(Agent agent) {
    String agentType = agent.getAgentType();

    // Check sharding filter
    if (!shardingFilter.filter(agent)) {
      return false;
    }

    // Check enabled pattern
    if (!config.getEnabledAgentPattern().matcher(agentType).matches()) {
      return false;
    }

    // Check if agent is disabled (pattern or list)
    if (isAgentDisabled(agentType)) {
      return false;
    }

    return true;
  }

  /**
   * Check if an agent is disabled using regex pattern matching.
   *
   * @param agentType the agent type to check
   * @return true if the agent matches the disabled pattern
   */
  private boolean isAgentDisabled(String agentType) {
    return config.getDisabledAgentPattern() != null
        && config.getDisabledAgentPattern().matcher(agentType).matches();
  }

  /** Evaluate watchdog heuristics and emit warnings for consecutive trigger conditions. */
  public void evaluateWatchdog(
      double permitsFreeRatio,
      double activeRatio,
      double acquiredFillRatio,
      long ready,
      int poolActive,
      int agentsAcquired,
      boolean redisStall,
      int maxConcurrent,
      int activeCount,
      int effectiveCapacity,
      int permitsAvailable) {

    // Consecutive-tick streaks to avoid flapping
    // Leak suspect: almost no free permits AND pool idle AND ready backlog
    if (maxConcurrent > 0 && permitsFreeRatio < 0.01 && poolActive == 0 && ready > 0) {
      watchdogLeakStreak++;
    } else {
      watchdogLeakStreak = 0;
    }
    if (watchdogLeakStreak >= 3) {
      watchdogLeakLastEpochMs.set(System.currentTimeMillis());
      // Reset streak after recording trigger
      watchdogLeakStreak = 0;
    }

    // Capacity skew: lots of free permits but we barely acquired anything
    if (ready > 0 && permitsFreeRatio > 0.90 && acquiredFillRatio < 0.10) {
      watchdogSkewStreak++;
    } else {
      watchdogSkewStreak = 0;
    }
    if (watchdogSkewStreak >= 3) {
      watchdogSkewLastEpochMs.set(System.currentTimeMillis());
      watchdogSkewStreak = 0;
    }

    // Zero progress: ready > 0 but acquired = 0, not a Redis stall
    if (ready > 0 && agentsAcquired == 0 && !redisStall) {
      watchdogZeroProgressStreak++;
    } else {
      watchdogZeroProgressStreak = 0;
    }
    if (watchdogZeroProgressStreak >= 3) {
      watchdogZeroProgressLastEpochMs.set(System.currentTimeMillis());
      watchdogZeroProgressStreak = 0;
    }

    // Redis stall: Redis breaker not CLOSED for several ticks
    if (redisStall) {
      watchdogRedisStallStreak++;
    } else {
      watchdogRedisStallStreak = 0;
    }
    if (watchdogRedisStallStreak >= 3) {
      watchdogRedisStallLastEpochMs.set(System.currentTimeMillis());
      watchdogRedisStallStreak = 0;
    }
  }

  /**
   * Statistics holder for scheduler metrics.
   *
   * <p>Includes a config-free health state derived from waiting queue lag relative to the minimum
   * enabled-agent interval on this pod. Queue lag is emitted in seconds even when HEALTHY to aid
   * sizing and performance diagnostics.
   */
  @lombok.Getter
  public static class SchedulerStats {
    private final long runCount;
    private final int registeredAgents;
    private final int activeAgents;
    private final long zombiesCleanedUp;
    private final long orphansCleanedUp;
    private final boolean running;
    private final boolean degraded;
    private final String degradedReason;
    private final long oldestOverdueSeconds;

    public SchedulerStats(
        long runCount,
        int registeredAgents,
        int activeAgents,
        long zombiesCleanedUp,
        long orphansCleanedUp,
        boolean running,
        boolean degraded,
        String degradedReason,
        long oldestOverdueSeconds) {
      this.runCount = runCount;
      this.registeredAgents = registeredAgents;
      this.activeAgents = activeAgents;
      this.zombiesCleanedUp = zombiesCleanedUp;
      this.orphansCleanedUp = orphansCleanedUp;
      this.running = running;
      this.degraded = degraded;
      this.degradedReason = degradedReason == null ? "" : degradedReason;
      this.oldestOverdueSeconds = oldestOverdueSeconds;
    }

    @Override
    public String toString() {
      return String.format(
          "SchedulerStats{runCount=%d, registered=%d, active=%d, zombies=%d, orphans=%d, running=%s, health=%s, oldest_overdue=%ds%s}",
          runCount,
          registeredAgents,
          activeAgents,
          zombiesCleanedUp,
          orphansCleanedUp,
          running,
          degraded ? "DEGRADED" : "HEALTHY",
          oldestOverdueSeconds,
          degraded && degradedReason != null && !degradedReason.isEmpty()
              ? ", reason=" + degradedReason
              : "");
    }
  }

  /** Resets zombie cleanup tracking state after completion or forced cancellation. */
  private void resetZombieCleanupState() {
    zombieCleanupRunning.set(false);
    zombieCleanupStartMs.set(0);
    zombieCleanupFuture = null;
  }

  /** Resets orphan cleanup tracking state after completion or forced cancellation. */
  private void resetOrphanCleanupState() {
    orphanCleanupRunning.set(false);
    orphanCleanupStartMs.set(0);
    orphanCleanupFuture = null;
  }
}
