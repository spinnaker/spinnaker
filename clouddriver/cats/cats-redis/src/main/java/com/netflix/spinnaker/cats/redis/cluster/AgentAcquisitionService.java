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

import static com.netflix.spinnaker.cats.agent.ExecutionInstrumentation.elapsedTimeMs;
import static com.netflix.spinnaker.cats.redis.cluster.support.CadenceGuard.*;

import com.google.common.annotations.VisibleForTesting;
import com.netflix.spinnaker.cats.agent.Agent;
import com.netflix.spinnaker.cats.agent.AgentExecution;
import com.netflix.spinnaker.cats.agent.ExecutionInstrumentation;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.ShardingFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Pipeline;
import redis.clients.jedis.Response;
import redis.clients.jedis.Tuple;

/**
 * Service that acquires agents from Redis and executes them using atomic operations.
 *
 * <p>Manages agent lifecycle: acquisition from waiting set, execution in working set, completion
 * handling, and periodic repopulation for recovery.
 *
 * <p><b>Cleanup coordination:</b> {@link ZombieCleanupService} handles locally-stuck agents
 * (cancels/interrupts threads). {@link OrphanCleanupService} handles agents from crashed pods
 * (Redis-only, never touches permits). Worker finally is the ultimate authority for permit release.
 * CAS on {@link RunState#permitHeld} ensures exactly-once permit release when multiple threads
 * race.
 *
 * @see RunState for per-agent CAS-protected execution state
 */
@Component
@Slf4j
public class AgentAcquisitionService {

  // Redis key names (injected via properties)
  private final String WAITING_SET;
  private final String WORKING_SET;

  private final JedisPool jedisPool;
  private final RedisScriptManager scriptManager;
  private final AgentIntervalProvider intervalProvider;
  private final ShardingFilter shardingFilter;
  private final PriorityAgentProperties agentProperties;
  private final PrioritySchedulerProperties schedulerProperties;
  private final PrioritySchedulerMetrics metrics;

  // Agent tracking
  private final Map<String, AgentWorker> agents = new ConcurrentHashMap<>();
  private final Map<String, String> activeAgents = new ConcurrentHashMap<>();
  private final Map<String, java.util.concurrent.Future<?>> activeAgentsFutures =
      new ConcurrentHashMap<>();

  // Redis TIME synchronization for multi-instance coordination
  private static final AtomicLong lastTimeCheck = new AtomicLong(0);
  private static final AtomicLong serverClientOffset = new AtomicLong(0);

  // Advanced statistics tracking
  private final AtomicLong agentMapSize = new AtomicLong(0);
  private final AtomicLong activeAgentMapSize = new AtomicLong(0);
  private final LongAdder agentsAcquired = new LongAdder();
  private final LongAdder agentsExecuted = new LongAdder();
  private final LongAdder agentsFailed = new LongAdder();

  // Exactly-once permit release handshake for zombie cancellation fairness
  private final ConcurrentHashMap<String, RunState> runStates = new ConcurrentHashMap<>();
  private volatile Semaphore
      maxConcurrentSemaphoreRef; // Provided by scheduler when calling saturatePool

  // Dead-man timer scheduler for early cancellation at (deadline + threshold)
  private final java.util.concurrent.ScheduledExecutorService deadmanScheduler;

  // Compiled exceptional-agents pattern to mirror zombie cleanup semantics
  private volatile java.util.regex.Pattern zombieExceptionalAgentsPattern;

  /**
   * Per-agent state for exactly-once permit release handshake.
   *
   * <p>This class tracks the lifecycle of a semaphore permit through agent execution:
   *
   * <ul>
   *   <li>{@code permitHeld} - true if the semaphore permit has not yet been released; CAS to false
   *       ensures exactly-once release whether by normal completion or cancellation
   *   <li>{@code started} - true once the agent's execution has actually begun
   *   <li>{@code deadmanHandle} - scheduled future for the dead-man timeout that auto-cancels stuck
   *       agents; cancelled on normal completion to prevent spurious interrupts
   * </ul>
   *
   * <p>Thread safety: All boolean fields use AtomicBoolean for lock-free CAS operations. The
   * deadmanHandle is volatile for visibility. A RunState instance is created when an agent is
   * submitted and removed when execution completes (normal or cancelled).
   *
   * <p><b>Coordination with cleanup:</b> Orphan cleanup never accesses RunState (only handles
   * agents from crashed pods). Shutdown may race with worker finally (safe via CAS).
   */
  private static final class RunState {
    final java.util.concurrent.atomic.AtomicBoolean permitHeld =
        new java.util.concurrent.atomic.AtomicBoolean(true);
    final java.util.concurrent.atomic.AtomicBoolean started =
        new java.util.concurrent.atomic.AtomicBoolean(false);
    volatile java.util.concurrent.ScheduledFuture<?> deadmanHandle;
  }

  /** Determine zombie threshold for the agent, honoring exceptional agents config. */
  private long getZombieThresholdForAgent(String agentType) {
    try {
      if (zombieExceptionalAgentsPattern != null
          && agentType != null
          && zombieExceptionalAgentsPattern.matcher(agentType).matches()) {
        return schedulerProperties.getZombieCleanup().getExceptionalAgents().getThresholdMs();
      }
      return schedulerProperties.getZombieCleanup().getThresholdMs();
    } catch (Exception e) {
      return schedulerProperties.getZombieCleanup().getThresholdMs();
    }
  }

  /** Dead-man timeout action: interrupt the stuck agent's thread. */
  private void onDeadmanTimeout(String agentType) {
    // Skip dead-man processing during shutdown to avoid racing with gracefullyReleaseActiveAgents()
    if (shuttingDown.get()) {
      log.debug("Dead-man timeout skipped for {} during shutdown", agentType);
      return;
    }
    try {
      java.util.concurrent.Future<?> future = activeAgentsFutures.get(agentType);
      if (future != null && !future.isDone()) {
        boolean cancelled = future.cancel(true);
        if (cancelled) {
          // Permit is released when thread exits in worker finally block
          log.warn("Dead-man timeout fired for {}: future cancelled", agentType);
        } else {
          log.debug("Dead-man timeout fired for {}: cancel returned false", agentType);
        }
      }
    } catch (Exception e) {
      log.debug("Dead-man timeout handling failed for {}", agentType, e);
    }
  }

  // Backlog/health snapshots and rate-limiting
  private final AtomicLong lastBacklogWarnEpochMs = new AtomicLong(0);
  private final AtomicLong lastStallWarnEpochMs = new AtomicLong(0);
  private final AtomicLong lastBatchParsingMismatchWarnEpochMs = new AtomicLong(0);
  private final AtomicLong lastDiagEpochMs = new AtomicLong(0);
  private final AtomicLong lastOldestOverdueSeconds = new AtomicLong(0);
  private final AtomicLong lastReadyCount = new AtomicLong(0);
  private final AtomicLong lastCapacityPerCycle = new AtomicLong(0);
  private final AtomicBoolean lastDegraded = new AtomicBoolean(false);
  private final java.util.concurrent.atomic.AtomicReference<String> lastDegradedReason =
      new java.util.concurrent.atomic.AtomicReference<>("");

  // Cached minimal enabled-agent interval in seconds for diagnostics (0 disables checks)
  private final AtomicLong cachedMinEnabledIntervalSec = new AtomicLong(0L);

  /**
   * Health evaluation notes:
   *
   * <ul>
   *   <li>Queue lag is computed based on the scores of agents in the waiting set; working-set
   *       overruns (zombies) are handled by the zombie cleanup.
   *   <li>Degradation is config-free: oldest_overdue_seconds > min enabled-agent interval on this
   *       pod.
   *   <li>WARNs are rate-limited to once per 10 minutes to avoid flooding.
   * </ul>
   */

  // Reusable collections to reduce GC pressure in high-load scenarios.
  // Design decision: ThreadLocal proliferation (intentional performance optimization)
  // ---------------------------------------------------------------------------------
  // These ThreadLocal collections reduce GC pressure in the scheduler hot path by reusing
  // containers across cycles. This is safe because:
  // 1. saturatePool() runs in a single-threaded scheduler executor (no concurrency within thread)
  // 2. All containers are cleared in finally blocks after each use (removeThreadLocals() on
  // shutdown)
  // 3. trimToSize() is called to shed capacity after large spikes
  // 4. JVM thread-local storage is efficient for this access pattern
  // The alternative (new ArrayList/HashSet per cycle) would create ~10 objects x 1Hz = GC pressure
  // at high agent counts. This optimization reduces GC pause times.
  private static final ThreadLocal<Set<AgentWorker>> REUSABLE_WORKERS_SET =
      ThreadLocal.withInitial(HashSet::new);

  private static final ThreadLocal<java.util.List<String>> REUSABLE_CANDIDATE_AGENTS =
      ThreadLocal.withInitial(java.util.ArrayList::new);
  private static final ThreadLocal<java.util.List<AgentWorker>> REUSABLE_CANDIDATE_WORKERS =
      ThreadLocal.withInitial(java.util.ArrayList::new);
  private static final ThreadLocal<java.util.List<String>> REUSABLE_ELIGIBLE_AGENTS =
      ThreadLocal.withInitial(java.util.ArrayList::new);
  private static final ThreadLocal<java.util.List<String>> REUSABLE_AGENT_SCORE_PAIRS =
      ThreadLocal.withInitial(java.util.ArrayList::new);
  private static final ThreadLocal<java.util.List<AgentCompletion>> REUSABLE_COMPLETIONS =
      ThreadLocal.withInitial(java.util.ArrayList::new);

  // Runtime configuration
  private volatile Pattern enabledAgentPattern;
  private volatile Pattern disabledAgentPattern;
  private volatile int redisRefreshPeriod;

  // Shutdown coordination
  private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
  private final AtomicBoolean gracefulShutdown = new AtomicBoolean(false);

  // Circuit breakers for protecting against cascading failures
  private final PrioritySchedulerCircuitBreaker acquisitionCircuitBreaker;
  private final PrioritySchedulerCircuitBreaker redisCircuitBreaker;

  // Queue agent completions for batch processing
  private final ConcurrentLinkedQueue<AgentCompletion> completionQueue =
      new ConcurrentLinkedQueue<>();

  // Recovery queue for agents that failed Redis scheduling after retry exhaustion.
  // Agents in this queue will be retried on the next scheduler cycle.
  private final ConcurrentLinkedQueue<AgentRecovery> scheduleRecoveryQueue =
      new ConcurrentLinkedQueue<>();

  // Time-based repopulation cadence control (epoch millis of last repopulation)
  private final java.util.concurrent.atomic.AtomicLong lastRepopulateEpochMs =
      new java.util.concurrent.atomic.AtomicLong(0L);

  /**
   * Local, per-pod failure streaks used for exponential backoff without extra Redis keys.
   *
   * <p>Streaks reset on success and increment on failure. This map is intentionally ephemeral and
   * will reset on pod restarts or resharding events.
   */
  private final java.util.concurrent.ConcurrentHashMap<String, Integer> failureStreaks =
      new java.util.concurrent.ConcurrentHashMap<>();

  // Failure classification for error-aware scheduling decisions.
  private enum FailureClass {
    PERMANENT_FORBIDDEN,
    THROTTLED,
    TRANSIENT,
    SERVER_ERROR,
    UNKNOWN
  }

  /** Represents an agent completion waiting to be processed in the next scheduler cycle. */
  private static class AgentCompletion {
    final Agent agent;
    final String deadlineScore;
    final boolean success;
    final FailureClass failureClass; // null when success
    final String throwableClassName; // optional; may be null

    AgentCompletion(Agent agent, String deadlineScore, boolean success) {
      this.agent = agent;
      this.deadlineScore = deadlineScore;
      this.success = success;
      this.failureClass = null;
      this.throwableClassName = null;
    }

    AgentCompletion(
        Agent agent,
        String deadlineScore,
        boolean success,
        FailureClass failureClass,
        String throwableClassName) {
      this.agent = agent;
      this.deadlineScore = deadlineScore;
      this.success = success;
      this.failureClass = failureClass;
      this.throwableClassName = throwableClassName;
    }
  }

  /**
   * Represents an agent that failed Redis scheduling and needs recovery on next cycle. Tracks the
   * original offset and retry attempt count to prevent infinite retry loops.
   */
  private static class AgentRecovery {
    final Agent agent;
    final long offsetMs;
    final int attemptCount;

    AgentRecovery(Agent agent, long offsetMs) {
      this.agent = agent;
      this.offsetMs = offsetMs;
      // attemptCount reflects completed recovery attempts; start at 0 so first processing is
      // attempt #1
      this.attemptCount = 0;
    }

    AgentRecovery(Agent agent, long offsetMs, int attemptCount) {
      this.agent = agent;
      this.offsetMs = offsetMs;
      this.attemptCount = attemptCount;
    }
  }

  /** Maximum number of recovery attempts before permanently dropping an agent. */
  private static final int MAX_RECOVERY_ATTEMPTS = 3;

  /**
   * Constructs a new AgentAcquisitionService.
   *
   * @param jedisPool Redis connection pool for scheduling operations
   * @param scriptManager Lua script manager for atomic Redis operations
   * @param intervalProvider Provider for agent execution intervals
   * @param shardingFilter Filter for shard-aware agent scheduling
   * @param agentProperties Configuration for agent behavior
   * @param schedulerProperties Configuration for scheduler behavior
   * @param metrics Metrics collector for tracking acquisition operations
   */
  public AgentAcquisitionService(
      JedisPool jedisPool,
      RedisScriptManager scriptManager,
      AgentIntervalProvider intervalProvider,
      ShardingFilter shardingFilter,
      PriorityAgentProperties agentProperties,
      PrioritySchedulerProperties schedulerProperties,
      PrioritySchedulerMetrics metrics) {
    this.jedisPool = jedisPool;
    this.scriptManager = scriptManager;
    this.intervalProvider = intervalProvider;
    this.shardingFilter = shardingFilter;
    this.agentProperties = agentProperties;
    this.schedulerProperties = schedulerProperties;
    this.metrics = metrics != null ? metrics : PrioritySchedulerMetrics.NOOP;

    // Initialize circuit breakers with configuration settings
    PrioritySchedulerProperties.CircuitBreaker cbConfig = schedulerProperties.getCircuitBreaker();
    if (cbConfig != null && cbConfig.isEnabled()) {
      this.acquisitionCircuitBreaker =
          new PrioritySchedulerCircuitBreaker(
              "acquisition",
              cbConfig.getFailureThreshold(),
              cbConfig.getFailureWindowMs(),
              cbConfig.getCooldownMs(),
              cbConfig.getHalfOpenDurationMs(),
              metrics);

      // Redis circuit breaker is more sensitive (lower threshold, shorter window)
      this.redisCircuitBreaker =
          new PrioritySchedulerCircuitBreaker(
              "redis",
              Math.max(3, cbConfig.getFailureThreshold() - 2), // Slightly lower threshold
              Math.min(5000, cbConfig.getFailureWindowMs()), // Shorter window
              (long) (cbConfig.getCooldownMs() * 0.7), // Shorter cooldown
              (long) (cbConfig.getHalfOpenDurationMs() * 0.6), // Shorter half-open
              metrics);
    } else {
      // Create disabled circuit breakers that always allow requests
      this.acquisitionCircuitBreaker =
          new PrioritySchedulerCircuitBreaker(
              "acquisition",
              Integer.MAX_VALUE, // Never trip
              Long.MAX_VALUE,
              0,
              0,
              metrics);
      this.redisCircuitBreaker =
          new PrioritySchedulerCircuitBreaker(
              "redis",
              Integer.MAX_VALUE, // Never trip
              Long.MAX_VALUE,
              0,
              0,
              metrics);
    }

    // Resolve configured key names at construction time
    PrioritySchedulerProperties.Keys keysCfg = schedulerProperties.getKeys();
    String hash = keysCfg.getHashTag();
    String brace = (hash != null && !hash.isEmpty()) ? ("{" + hash + "}") : "";
    String prefix = keysCfg.getPrefix() != null ? keysCfg.getPrefix() : "";
    this.WAITING_SET = prefix + keysCfg.getWaitingSet() + brace;
    this.WORKING_SET = prefix + keysCfg.getWorkingSet() + brace;

    // Initialize runtime configuration
    this.enabledAgentPattern = Pattern.compile(agentProperties.getEnabledPattern());

    // Compile disabled agent pattern if provided
    if (!agentProperties.getDisabledPattern().isEmpty()) {
      this.disabledAgentPattern = Pattern.compile(agentProperties.getDisabledPattern());
    } else {
      this.disabledAgentPattern = null;
    }

    this.redisRefreshPeriod = schedulerProperties.getRefreshPeriodSeconds();

    // Compile exceptional-agents pattern to share semantics with ZombieCleanupService
    try {
      this.zombieExceptionalAgentsPattern =
          schedulerProperties.getExceptionalAgentsPatternCompiled();
    } catch (Exception e) {
      log.error("Failed to compile exceptional agents pattern for dead-man timing", e);
      this.zombieExceptionalAgentsPattern = null;
    }

    // Initialize dead-man scheduler (single thread, daemon, remove cancelled tasks)
    java.util.concurrent.ScheduledThreadPoolExecutor dmExec =
        new java.util.concurrent.ScheduledThreadPoolExecutor(
            1,
            r -> {
              Thread thread = new Thread(r, "DeadmanTimer-0");
              thread.setDaemon(true);
              return thread;
            });
    dmExec.setRemoveOnCancelPolicy(true);
    this.deadmanScheduler = dmExec;
  }

  /**
   * Shuts down the dead-man timer scheduler during bean destruction. Invoked automatically by
   * Spring's lifecycle management.
   */
  @PreDestroy
  public void shutdownDeadmanScheduler() {
    try {
      if (deadmanScheduler != null) {
        deadmanScheduler.shutdownNow();
      }
    } catch (Exception ignore) {
      log.debug("Failed to shutdown deadman scheduler", ignore);
    }
  }

  /**
   * Core scheduling logic: Find ready agents, acquire them, and submit for execution.
   *
   * <p>Semantics (chunked acquisition): - Compute available slots = max(0, maxConcurrentAgents -
   * currentlyRunning) unless unbounded - Fill up to available slots in chunks of size
   * redis.scheduler.batch-operations.batch-size within the same scheduler tick (multiple scans if
   * needed) - If batch-operations.batch-size <= 0, treat as "no per-chunk cap" and use remaining
   * slots as the chunk size (still uses batch acquisition if enabled) - Never spin: if a chunk
   * acquires 0, stop and submit what was acquired
   *
   * @param runCount Current run cycle number for periodic refresh
   * @param maxConcurrentSemaphore Optional semaphore for instance-wide concurrency control
   * @param agentWorkPool Thread pool for executing agents
   * @return Number of agents successfully acquired and submitted for execution
   */
  public int saturatePool(
      long runCount, Semaphore maxConcurrentSemaphore, ExecutorService agentWorkPool) {
    // Store reference for permit accounting
    this.maxConcurrentSemaphoreRef = maxConcurrentSemaphore;
    log.debug("Starting agent acquisition cycle {}, known agents: {}", runCount, agents.size());
    metrics.incrementAcquireAttempts();

    // Check circuit breaker before attempting acquisition
    if (!acquisitionCircuitBreaker.allowRequest()) {
      log.warn(
          "Acquisition circuit breaker is OPEN - skipping agent acquisition cycle {}", runCount);
      metrics.recordCircuitBreakerBlocked("acquisition");
      return 0;
    }

    long acquireStartMs = System.currentTimeMillis();

    try (Jedis jedis = jedisPool.getResource()) {
      // Fetch Redis TIME once for this cycle and reuse a cached nowMs across score() calls
      Long nowMsCached = null;
      try {
        java.util.List<String> time = jedis.time();
        if (time != null && time.size() >= 2) {
          long sec = Long.parseLong(time.get(0));
          long micros = Long.parseLong(time.get(1));
          nowMsCached = (sec * 1000L) + (micros / 1000L);
        }
      } catch (Exception e) {
        log.debug("Failed to fetch Redis TIME for this cycle; falling back to offset cache", e);
      }
      // Prune completed futures (best-effort) to keep tracking map small
      try {
        activeAgentsFutures.forEach(
            (key, future) -> {
              if (future != null && future.isDone()) {
                activeAgentsFutures.remove(key, future); // remove only if mapping unchanged
              }
            });
      } catch (Exception ignore) {
        // Best-effort only – pruning failures are non-fatal and retried next cycle
      }
      // Check concurrent agent limits before processing
      int maxConcurrentAgents = agentProperties.getMaxConcurrentAgents();
      int currentlyRunning = (int) activeAgentMapSize.get();
      boolean unbounded = maxConcurrentAgents <= 0;

      if (!unbounded && currentlyRunning >= maxConcurrentAgents) {
        log.debug(
            "Skipping agent acquisition - at max concurrent limit ({} running, {} max)",
            currentlyRunning,
            maxConcurrentAgents);
        metrics.recordAcquireTime("auto", System.currentTimeMillis() - acquireStartMs);
        return 0;
      }

      // Phase 1: Process queued agent completions (metrics/observability only)
      processQueuedCompletions();

      // Phase 2: Process recovery queue for agents that failed Redis scheduling
      processRecoveryQueue(jedis);

      // Phase 3: Agent repopulation (Redis recovery, periodic)
      long nowMsForRepop = System.currentTimeMillis();
      long refreshPeriodMs = Math.max(1L, schedulerProperties.getRefreshPeriodSeconds()) * 1000L;
      long last = lastRepopulateEpochMs.get();
      boolean dueByTime = (last != 0L) && ((nowMsForRepop - last) >= refreshPeriodMs);
      boolean dueByCycle = (redisRefreshPeriod > 0) && (runCount % redisRefreshPeriod == 0);
      if (dueByCycle) {
        lastRepopulateEpochMs.set(nowMsForRepop);
        repopulateRedisAgents(jedis);
      } else if (dueByTime) {
        lastRepopulateEpochMs.set(nowMsForRepop);
        repopulateRedisAgents(jedis);
      } else if (last == 0L) {
        // First call ever: perform repopulation to initialize Redis state immediately.
        // This ensures agents are ready on startup rather than waiting for refreshPeriodSeconds
        // (~30s default) to elapse.
        lastRepopulateEpochMs.set(nowMsForRepop);
        repopulateRedisAgents(jedis);
      }
      // Note: cachedMinEnabledIntervalSec is event-driven (updated on register/unregister),
      // not polled - no need to update every cycle

      // Phase 4: Determine current readiness state for diagnostics (gated by cadence/need)
      String currentScore = score(jedis, 0L, nowMsCached);
      long currentScoreSeconds = safeParseScore(currentScore, System.currentTimeMillis() / 1000L);
      // Gate diagnostics: only compute when debug is enabled, when warn cadence is due,
      // or when a periodic diagnostic cadence elapses. Period derives from scheduler interval.
      long schedulerIntervalMs = schedulerProperties.getIntervalMs();
      final long DIAG_PERIOD_MS = Math.max(3L * Math.max(1L, schedulerIntervalMs), 10_000L);
      boolean emitDiag =
          log.isDebugEnabled()
              || isPeriodElapsed(lastBacklogWarnEpochMs.get(), 600_000L)
              || isPeriodElapsed(lastStallWarnEpochMs.get(), 300_000L)
              || isPeriodElapsed(lastDiagEpochMs.get(), DIAG_PERIOD_MS);

      // Cycle-long registry snapshot: consistent view of registered agents for the entire cycle
      final java.util.Map<String, AgentWorker> registrySnapshot = new java.util.HashMap<>(agents);

      long readyCountForDiagnostics = 0L;
      boolean earlyEmptyReady = false;
      Long earliestRunnableLocalScore = null;
      if (emitDiag) {
        try {
          // Examine a small sorted-set window so we can count the oldest agents this pod could
          // actually execute (restricted to locally registered + enabled agents).
          int batchSize = schedulerProperties.getBatchOperations().getBatchSize();
          // If batchSize is 0 or negative, use a default window size for diagnostics
          final int window =
              batchSize > 0
                  ? Math.max(8, Math.min(64, batchSize))
                  : 64; // Default window size when batch size is unbounded
          Set<Tuple> earliest =
              jedis.zrangeByScoreWithScores(WAITING_SET, "-inf", currentScore, 0, window);
          long eligibleReady = 0L;
          if (earliest != null) {
            for (Tuple tuple : earliest) {
              String agentType = tuple.getElement();
              AgentWorker local = registrySnapshot.get(agentType);
              if (local != null && isAgentEnabled(local.getAgent())) {
                eligibleReady++;
                if (earliestRunnableLocalScore == null) {
                  // First matching entry: use its score for stall diagnostics below.
                  earliestRunnableLocalScore = (long) tuple.getScore();
                }
              }
            }
          }
          earlyEmptyReady = (eligibleReady == 0L);
          readyCountForDiagnostics = eligibleReady;
          if (earlyEmptyReady) {
            // Waiting set has entries but none are runnable on this pod right now; check whether
            // the next local candidate is still in the future and, if so, surface a stall warning
            // on the same cadence as before.
            // Use a separate deep scan for stall detection (periodic deep scans are acceptable
            // for diagnostics, while shallow scans are preferred for acquisition performance).
            try {
              long waitingBacklog = jedis.zcard(WAITING_SET);
              Long stallCandidateScore = earliestRunnableLocalScore;
              if (stallCandidateScore == null) {
                stallCandidateScore =
                    findEarliestFutureLocalScore(
                        jedis, registrySnapshot, currentScoreSeconds, window);
              }
              if (waitingBacklog > 0 && stallCandidateScore != null) {
                long nowSec = currentScoreSeconds;
                long minIntervalSec = cachedMinEnabledIntervalSec.get();
                if (minIntervalSec > 0L
                    && (stallCandidateScore - nowSec) > minIntervalSec
                    && shouldWarnNow(lastStallWarnEpochMs, 300_000)) {
                  long nextReadyInSec = Math.max(0L, stallCandidateScore - nowSec);
                  log.warn(
                      "Acquisition stall detected: ready=0, waiting_backlog={}, next_local_ready_in={}s > min_interval={}s, pool_active={}, pool_waiters={}",
                      waitingBacklog,
                      nextReadyInSec,
                      minIntervalSec,
                      jedisPool.getNumActive(),
                      jedisPool.getNumWaiters());
                  metrics.incrementStallDetected();
                }
              }
            } catch (Exception ignore) {
              // Diagnostics only – continue with acquisition attempt.
            }
          }
        } catch (Exception e) {
          // Log diagnostic failures to aid debugging stall scenarios.
          // Acquisition must proceed regardless of diagnostic errors - diagnostics are for
          // observability only, not for controlling acquisition behavior.
          log.debug("Diagnostic readiness check failed, proceeding with acquisition anyway", e);
          readyCountForDiagnostics = 0L;
          earliestRunnableLocalScore = null;
          // Important: earlyEmptyReady is NOT set here - acquisition must always proceed
        }

        // Diagnostics are for observability only. The diagnostic window (max 64 agents) may not
        // reflect actual eligibility due to timing, exceptions, or queue ordering. The acquisition
        // phase scans the full queue with proper filtering and must always run.
        if (earlyEmptyReady && log.isDebugEnabled()) {
          log.debug(
              "Diagnostic window shows no locally eligible agents; acquisition will proceed to scan full queue");
        }
      }

      long readyCount = emitDiag ? Math.max(0L, readyCountForDiagnostics) : 0L;
      long oldestOverdueSec = 0L;
      if (emitDiag) {
        try {
          // To avoid false positives on DEGRADED, consider only agents known and enabled locally.
          // Fetch a small window of the oldest ready entries and pick the first matching local
          // agent.
          int batchSizeForWindow = schedulerProperties.getBatchOperations().getBatchSize();
          // If batchSize is 0 or negative (unbounded), use default window size for diagnostics
          final int window =
              batchSizeForWindow > 0
                  ? Math.max(8, Math.min(64, batchSizeForWindow))
                  : 64; // Default window size when batch size is unbounded
          Set<Tuple> oldestWindow =
              jedis.zrangeByScoreWithScores(WAITING_SET, "-inf", currentScore, 0, window);
          long nowSecForDiagnostics;
          try {
            nowSecForDiagnostics = Long.parseLong(currentScore);
          } catch (NumberFormatException nfe) {
            nowSecForDiagnostics = System.currentTimeMillis() / 1000L;
          }
          for (Tuple tuple : oldestWindow) {
            String agentType = tuple.getElement();
            AgentWorker local = registrySnapshot.get(agentType);
            if (local != null && isAgentEnabled(local.getAgent())) {
              long oldestScore = (long) tuple.getScore();
              oldestOverdueSec = Math.max(0L, nowSecForDiagnostics - oldestScore);
              break;
            }
          }
        } catch (Exception ignore) {
          // Best-effort; keep defaults on failure
        }
      }

      // Phase 5: Agent acquisition setup
      // Reusing thread-local collection to avoid memory allocations
      Set<AgentWorker> workersToSubmit = REUSABLE_WORKERS_SET.get();
      workersToSubmit.clear(); // Clear any previous contents
      int agentsAcquiredThisCycle = 0;

      // Prevent same-tick reacquisition attempts for the same agent
      java.util.Set<String> attemptedThisCycle = new java.util.HashSet<>();

      // Calculate how many new agents this pod can try to acquire
      int availableSlotsForNewAgents =
          unbounded ? Integer.MAX_VALUE : Math.max(0, maxConcurrentAgents - currentlyRunning);

      if (!unbounded && availableSlotsForNewAgents <= 0) {
        if (log.isDebugEnabled()) {
          log.debug(
              "No available slots to acquire new agents this cycle ({} running, {} max). Skipping acquisition phase.",
              currentlyRunning,
              maxConcurrentAgents);
        }
        metrics.recordAcquireTime("auto", System.currentTimeMillis() - acquireStartMs);
        return 0;
      }

      // Do NOT cap by diagnostic-ready count; we intentionally probed only a single element.
      // Acquisition capacity should reflect concurrency slots when bounded, or be effectively
      // unbounded when maxConcurrentAgents <= 0.
      int effectiveMaxToAcquire = unbounded ? Integer.MAX_VALUE : availableSlotsForNewAgents;

      // Evaluate health/degradation and rate-limited WARNing.
      // Avoid false positives by excluding known-orphan/zombie cases: the decision is based purely
      // on queue lag in the waiting set (via agent scores) and local agent cadences. Working-set
      // overruns are handled by the zombie cleanup and are not considered here.
      long minIntervalSec = cachedMinEnabledIntervalSec.get();

      boolean degraded = oldestOverdueSec > minIntervalSec && minIntervalSec > 0L;
      int capacityPerCycle = availableSlotsForNewAgents;

      if (degraded) {
        logDegradedBacklog(
            oldestOverdueSec,
            minIntervalSec,
            readyCount,
            capacityPerCycle,
            currentlyRunning,
            maxConcurrentAgents);
      }

      // Store initial degradation state for later update after slot filling check
      boolean initialDegraded = degraded;
      String initialDegradedReason =
          degraded
              ? String.format(
                  "oldest_overdue=%ss > min_interval=%ss; ready=%d capacity_per_cycle=%d",
                  oldestOverdueSec, minIntervalSec, Math.max(0L, readyCount), capacityPerCycle)
              : "";

      // Persist initial snapshots (will be updated after slot filling check if needed)
      // Always keep snapshots up to date for health logging
      lastOldestOverdueSeconds.set(oldestOverdueSec);
      lastReadyCount.set(Math.max(0L, readyCount));
      lastCapacityPerCycle.set(capacityPerCycle);
      // Degradation status will be finalized after slot filling check
      int queueDepthDebug = -1;
      if (agentWorkPool instanceof java.util.concurrent.ThreadPoolExecutor) {
        queueDepthDebug =
            ((java.util.concurrent.ThreadPoolExecutor) agentWorkPool).getQueue().size();
      }
      log.debug(
          "Attempting to acquire agents ({} running, {} max capacity, {} ready in Redis, up to {} slots this cycle, queue_depth={})",
          currentlyRunning,
          maxConcurrentAgents,
          readyCount,
          availableSlotsForNewAgents,
          queueDepthDebug);

      // Phase 6: Acquire up to available slots in chunks of batch-size
      int remainingToAcquire = effectiveMaxToAcquire;
      int chunkOffset = 0; // Track offset for pagination through ready agents

      // Calculate max chunk attempts based on actual need and filtering expectations.
      // batch-size <= 0 means "no per-chunk cap" so each attempt can try every remaining slot.
      int configuredBatchSize = schedulerProperties.getBatchOperations().getBatchSize();
      if (configuredBatchSize <= 0) {
        configuredBatchSize = effectiveMaxToAcquire; // Default 0 = use all remaining slots
      }

      // Base calculation: how many chunks we need to fill available slots
      int baseAttempts =
          (effectiveMaxToAcquire + configuredBatchSize - 1)
              / configuredBatchSize; // ceiling division

      // Apply multiplier for filtering scenarios
      double multiplier = schedulerProperties.getBatchOperations().getChunkAttemptMultiplier();
      int maxChunkAttempts;

      if (multiplier <= 0) {
        // No multiplier configured - just use base attempts (no extra attempts for filtering)
        maxChunkAttempts = Math.max(1, baseAttempts);
        log.debug("Using base chunk attempts (no multiplier): {}", maxChunkAttempts);
      } else {
        // Apply multiplier to handle filtering
        maxChunkAttempts = Math.max(1, (int) Math.ceil(baseAttempts * multiplier));

        // Cap at a reasonable limit to prevent runaway in edge cases
        maxChunkAttempts = Math.min(maxChunkAttempts, 100);

        log.debug(
            "Calculated chunk attempts: {} (slots: {} / batch: {} = {} base x {} multiplier)",
            maxChunkAttempts,
            effectiveMaxToAcquire,
            configuredBatchSize,
            baseAttempts,
            multiplier);
      }

      int chunkAttempts = 0;

      while (remainingToAcquire > 0 && chunkAttempts < maxChunkAttempts) {
        chunkAttempts++;

        // Use configured batch size when positive; otherwise treat as unlimited for this chunk.
        // With the default (0), we effectively make a single pass that can include every remaining
        // slot.
        int configuredBatch = schedulerProperties.getBatchOperations().getBatchSize();
        int perChunkLimit = configuredBatch > 0 ? configuredBatch : remainingToAcquire;
        int chunkSize = Math.min(remainingToAcquire, perChunkLimit);
        if (chunkSize <= 0) {
          break;
        }

        // Refresh server time for fairness across chunks
        currentScore = score(jedis, 0L, nowMsCached);

        // Use offset to skip already-tried agents when continuing after filtered chunks
        Set<String> readyChunk =
            jedis.zrangeByScore(WAITING_SET, "-inf", currentScore, chunkOffset, chunkSize);

        if (readyChunk == null || readyChunk.isEmpty()) {
          break; // Nothing else ready right now
        }

        int acquiredThisChunk = 0;
        long chunkStartMs = System.currentTimeMillis();
        if (schedulerProperties.getBatchOperations().isEnabled() && !readyChunk.isEmpty()) {
          try {
            acquiredThisChunk =
                saturatePoolBatch(
                    jedis,
                    readyChunk,
                    chunkSize,
                    maxConcurrentSemaphore,
                    workersToSubmit,
                    attemptedThisCycle,
                    registrySnapshot,
                    nowMsCached,
                    chunkStartMs);
            metrics.recordAcquireTime("batch", System.currentTimeMillis() - chunkStartMs);
          } catch (Exception e) {
            // Note: saturatePoolBatch catches Throwable internally and records fallback metrics,
            // so this catch should rarely fire. However, it's kept as a safety net for any
            // unexpected exceptions that might propagate (e.g., if saturatePoolBatch signature
            // changes).
            //
            // Important: saturatePoolBatch catches all Throwables internally and records metrics,
            // then returns normally (doesn't throw). If an exception reaches here, it means either:
            // 1) A bug in saturatePoolBatch (shouldn't happen)
            // 2) An unexpected exception type that bypassed the internal catch (unlikely)
            // In these rare cases, metrics may be double-counted, but this is acceptable as it
            // indicates a problem that needs investigation.
            log.warn("Batch acquisition failed for chunk, falling back to individual", e);
            // Record fallback metrics here as well (in case exception propagated from
            // saturatePoolBatch). Note: This may result in double-counting if saturatePoolBatch
            // already recorded metrics, but this is acceptable for the safety net case.
            metrics.incrementBatchFallback();
            metrics.recordAcquireTime("fallback", System.currentTimeMillis() - chunkStartMs);
            workersToSubmit.clear();
            acquiredThisChunk =
                saturatePoolIndividual(
                    jedis,
                    readyChunk,
                    chunkSize,
                    maxConcurrentSemaphore,
                    workersToSubmit,
                    attemptedThisCycle,
                    registrySnapshot,
                    nowMsCached);
          }
        } else {
          acquiredThisChunk =
              saturatePoolIndividual(
                  jedis,
                  readyChunk,
                  chunkSize,
                  maxConcurrentSemaphore,
                  workersToSubmit,
                  attemptedThisCycle,
                  registrySnapshot,
                  nowMsCached);
          metrics.recordAcquireTime("individual", System.currentTimeMillis() - chunkStartMs);
        }

        if (acquiredThisChunk <= 0) {
          // No agents acquired from this chunk - could be due to:
          // 1. All agents in chunk were acquired by other pods (normal contention)
          // 2. All agents were filtered out (sharding/disabled)
          // 3. Semaphore exhausted
          // Continue to next chunk to maintain throughput - agents deeper in queue may still
          // be eligible, and with high filter rates stopping here reduces effective capacity
          log.debug(
              "No agents acquired from chunk (size: {}) at offset {}, checking for more ready agents",
              readyChunk.size(),
              chunkOffset);

          // Only break if we had nothing to attempt (empty chunk means no more ready)
          if (readyChunk.isEmpty()) {
            break;
          }
          // Move offset forward to skip agents we've already tried
          chunkOffset += readyChunk.size();
          // Continue to next chunk to prevent starvation
          continue;
        }

        agentsAcquiredThisCycle += acquiredThisChunk;
        remainingToAcquire -= acquiredThisChunk;
        // Reset offset to 0 after successful acquisition since acquired agents are removed from
        // waiting set
        // Only move offset forward if no agents were acquired (filtering scenario)
        chunkOffset = 0;
      }

      // Check for slot filling performance issues
      boolean slotFillingIssue = false;
      String slotFillingReason = null;

      if (chunkAttempts >= maxChunkAttempts && remainingToAcquire > 0) {
        // Check if we have significant unfilled slots with evidence of filtering
        double unfilledRatio = (double) remainingToAcquire / effectiveMaxToAcquire;
        int scannedButNotAcquired = chunkOffset - agentsAcquiredThisCycle;

        if (unfilledRatio > 0.2 && scannedButNotAcquired > 0) {
          // Significant performance degradation detected
          slotFillingIssue = true;
          double actualFilterRate = (double) scannedButNotAcquired / chunkOffset;
          slotFillingReason =
              String.format(
                  "slot_filling_degraded: %d%% unfilled after scanning %d agents (filter_rate=%.1f%%, multiplier=%s)",
                  (int) (unfilledRatio * 100),
                  chunkOffset,
                  actualFilterRate * 100,
                  multiplier > 0 ? String.valueOf(multiplier) : "0");
        } else if (log.isDebugEnabled()) {
          // Still log at debug for troubleshooting
          log.debug(
              "Reached max chunk attempts ({}), {} slots unfilled, scanned {}, acquired {}",
              maxChunkAttempts,
              remainingToAcquire,
              chunkOffset,
              agentsAcquiredThisCycle);
        }
      }

      // Update degradation status based on backlog issues only.
      // Note: slotFillingIssue is NOT included in DEGRADED because with sharding enabled,
      // high filter rates are expected (agents belong to other pods). Slot filling issues
      // are tracked as watchdogs for observability but don't affect health status.
      if (emitDiag) {
        boolean finalDegraded = initialDegraded;
        String finalDegradedReason = initialDegraded ? initialDegradedReason : "";

        // Log slot filling issue separately if present (for debugging, not health status)
        if (slotFillingIssue && log.isDebugEnabled()) {
          log.debug("Slot filling watchdog: {}", slotFillingReason);
        }

        lastDegraded.set(finalDegraded);
        lastDegradedReason.set(finalDegradedReason);
        lastDiagEpochMs.set(System.currentTimeMillis());
      }

      // Phase 7: Submit all acquired agents for execution
      // Submit each agent individually to handle rejections properly
      for (AgentWorker worker : workersToSubmit) {
        String agentType = worker.getAgent().getAgentType();

        // Critical: Set semaphore before execution so it can be released when done
        worker.setMaxConcurrentSemaphore(maxConcurrentSemaphore);
        // Initialize run-state for exactly-once permit release
        runStates.put(agentType, new RunState());

        // Submit with proper rejection handling
        java.util.concurrent.Future<?> future =
            submitAgentWithRejectionHandling(worker, agentWorkPool, maxConcurrentSemaphore);

        if (future != null) {
          // Schedule dead-man cancellation exactly at (completion deadline + threshold)
          try {
            if (schedulerProperties.getZombieCleanup().isEnabled()) {
              if (worker.deadlineScore != null && worker.deadlineScore.matches("^\\d+$")) {
                long thresholdMs = getZombieThresholdForAgent(agentType);
                // deadlineScore encodes the completion deadline in epoch seconds
                long completionDeadlineMs = Long.parseLong(worker.deadlineScore) * 1000L;
                // Use nowMsCached if available (from cycle start) for consistency with acquisition
                // timing. This prevents premature cancellation during long scheduler cycles (>1s)
                // where nowMsWithOffset() would be later than nowMsCached, causing the timer to
                // fire earlier than intended.
                long nowMsForTimer = nowMsCached != null ? nowMsCached : nowMsWithOffset();
                long delayMs = Math.max(0L, (completionDeadlineMs + thresholdMs) - nowMsForTimer);
                RunState runState = runStates.get(agentType);
                if (runState != null) {
                  runState.deadmanHandle =
                      deadmanScheduler.schedule(
                          () -> onDeadmanTimeout(agentType),
                          delayMs,
                          java.util.concurrent.TimeUnit.MILLISECONDS);
                }
              }
            }
          } catch (Exception e) {
            log.debug("Dead-man scheduling failed for {}", worker.getAgent().getAgentType(), e);
          }
          log.debug("Submitted agent {} for execution", worker.getAgent().getAgentType());
        }
      }

      if (log.isDebugEnabled()) {
        log.debug(
            "Completed agent acquisition cycle: {} agents acquired and submitted for execution",
            agentsAcquiredThisCycle);
      }

      metrics.incrementAcquired(agentsAcquiredThisCycle);
      metrics.recordAcquireTime("auto", System.currentTimeMillis() - acquireStartMs);

      // Record successful acquisition to circuit breaker
      acquisitionCircuitBreaker.recordSuccess();
      redisCircuitBreaker.recordSuccess();

      return agentsAcquiredThisCycle;

    } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
      log.warn("Redis connection error during agent acquisition", e);

      // Record Redis failure to circuit breakers
      redisCircuitBreaker.recordFailure(e);
      acquisitionCircuitBreaker.recordFailure(e);

      metrics.recordAcquireTime("auto", System.currentTimeMillis() - acquireStartMs);
      return 0;

      // Design note: Catch Throwable as final safety net for acquisition cycle.
      // - Most permit-related failures are handled by inner catch(Throwable) blocks in batch
      //   acquisition and submission, but this ensures we handle Errors in other parts of the
      //   acquisition flow (e.g., during diagnostics, metrics, submission loop setup).
      // - While the scheduler's outer catch(Throwable) would eventually catch these, handling
      //   them here allows proper circuit breaker recording and prevents Error propagation from
      //   disrupting other scheduler services.
    } catch (Throwable e) {
      log.error("Error during agent acquisition cycle", e);

      // Record general failure to acquisition circuit breaker (only for Exceptions)
      if (e instanceof Exception) {
        acquisitionCircuitBreaker.recordFailure((Exception) e);
      }

      metrics.recordAcquireTime("auto", System.currentTimeMillis() - acquireStartMs);
      return 0;
    }
  }

  /**
   * Public helper to perform only the periodic Redis repopulation when due, without attempting any
   * acquisition. Intended for schedulers that want to separate repopulation from acquisition within
   * a single run cycle.
   *
   * @param runCount current scheduler cycle number
   */
  public void repopulateIfDue(long runCount) {
    // Check Redis circuit breaker before attempting repopulation
    if (!redisCircuitBreaker.allowRequest()) {
      log.debug("Redis circuit breaker is OPEN - skipping repopulation for cycle {}", runCount);
      metrics.recordCircuitBreakerBlocked("redis");
      // Note: cachedMinEnabledIntervalSec is event-driven (updated on register/unregister)
      return;
    }

    long start = System.currentTimeMillis();
    // Note: cachedMinEnabledIntervalSec is event-driven (updated on register/unregister)
    try (Jedis jedis = jedisPool.getResource()) {
      long nowMsForRepop = start;
      long refreshPeriodMs = Math.max(1L, schedulerProperties.getRefreshPeriodSeconds()) * 1000L;
      long last = lastRepopulateEpochMs.get();
      if (nowMsForRepop - last >= refreshPeriodMs
          && lastRepopulateEpochMs.compareAndSet(last, nowMsForRepop)) {
        repopulateRedisAgents(jedis);
        metrics.recordRepopulateTime(System.currentTimeMillis() - start);
        redisCircuitBreaker.recordSuccess();
      }
    } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
      log.warn("Redis connection error during repopulation", e);
      redisCircuitBreaker.recordFailure(e);
      metrics.incrementRepopulateError("redis_connection");
      metrics.recordRepopulateTime(System.currentTimeMillis() - start);
    } catch (Exception e) {
      log.warn("Repopulation attempt failed", e);
      metrics.incrementRepopulateError(e.getClass().getSimpleName());
      metrics.recordRepopulateTime(System.currentTimeMillis() - start);
    }
  }

  /**
   * Perform time-based repopulation when due and return true if it was executed. This is intended
   * for schedulers to decide whether to skip acquisition on the same tick.
   */
  public boolean repopulateIfDueNow() {
    long now = nowMs();
    long refreshPeriodMs = Math.max(1L, schedulerProperties.getRefreshPeriodSeconds()) * 1000L;
    long last = lastRepopulateEpochMs.get();
    // Note: cachedMinEnabledIntervalSec is event-driven (updated on register/unregister)
    if (last == 0L) {
      // Do not initialize here; allow caller (scheduler) to trigger repopulation
      // on first run when required by tests/config.
      return false;
    }
    if (!isPeriodElapsed(last, refreshPeriodMs)) {
      return false;
    }
    if (!lastRepopulateEpochMs.compareAndSet(last, now)) {
      return false;
    }

    long start = now;
    try (Jedis jedis = jedisPool.getResource()) {
      repopulateRedisAgents(jedis);
      metrics.recordRepopulateTime(System.currentTimeMillis() - start);
      return true;
    } catch (Exception e) {
      log.warn("Repopulation attempt failed", e);
      metrics.incrementRepopulateError(e.getClass().getSimpleName());
      return false;
    }
  }

  private static boolean shouldWarnNow(AtomicLong lastEpochMs, long minPeriodMs) {
    long now = System.currentTimeMillis();
    long last = lastEpochMs.get();
    if (now - last >= minPeriodMs) {
      lastEpochMs.set(now);
      return true;
    }
    return false;
  }

  /**
   * Safely parse a score string to a long value, with fallback to default value on parse failure.
   *
   * @param scoreString Score string to parse
   * @param defaultValue Default value to return if parsing fails
   * @return Parsed score or default value
   */
  private static long safeParseScore(String scoreString, long defaultValue) {
    try {
      return Long.parseLong(scoreString);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * Find the earliest future local agent score by scanning agents with scores greater than
   * currentScore. This is used for stall detection when no ready agents are available.
   *
   * <p>This performs a deep scan of future agents (score > currentScore) to find the earliest
   * locally registered and enabled agent. This is separate from the shallow acquisition scan for
   * performance reasons - deep scans are acceptable for periodic diagnostics but not for
   * acquisition.
   *
   * @param jedis Redis connection
   * @param registrySnapshot Snapshot of registered agents
   * @param currentScoreSeconds Current time in seconds (score threshold)
   * @param window Maximum number of agents to scan
   * @return The earliest future local agent score, or null if none found
   */
  private Long findEarliestFutureLocalScore(
      Jedis jedis,
      java.util.Map<String, AgentWorker> registrySnapshot,
      long currentScoreSeconds,
      int window) {
    try {
      // Scan future agents (score > currentScore) to find earliest local candidate
      // Use String.valueOf to convert long to String for Redis API
      // Overflow protection: if currentScoreSeconds is MAX_VALUE, no future scores exist
      if (currentScoreSeconds == Long.MAX_VALUE) {
        return null; // No scores can be > MAX_VALUE
      }
      Set<Tuple> futureAgents =
          jedis.zrangeByScoreWithScores(
              WAITING_SET, String.valueOf(currentScoreSeconds + 1), "+inf", 0, window);
      Long earliestFutureLocalScore = null;
      if (futureAgents != null) {
        for (Tuple tuple : futureAgents) {
          String agentType = tuple.getElement();
          AgentWorker local = registrySnapshot.get(agentType);
          if (local != null && isAgentEnabled(local.getAgent())) {
            long score = (long) tuple.getScore();
            if (earliestFutureLocalScore == null || score < earliestFutureLocalScore) {
              earliestFutureLocalScore = score;
            }
          }
        }
      }
      return earliestFutureLocalScore;
    } catch (Exception e) {
      // Best-effort diagnostics - return null on failure
      log.debug("Failed to find earliest future local score", e);
      return null;
    }
  }

  private void logDegradedBacklog(
      long oldestOverdueSec,
      long minIntervalSec,
      long readyCount,
      int capacityPerCycle,
      int currentlyRunning,
      int maxConcurrentAgents) {
    if (shouldWarnNow(lastBacklogWarnEpochMs, 600_000)) {
      log.warn(
          "PriorityScheduler degraded: oldest_overdue={}s > min_interval={}s; ready={} capacity_per_cycle={} running={} max_concurrent={}",
          oldestOverdueSec,
          minIntervalSec,
          readyCount,
          capacityPerCycle,
          currentlyRunning,
          maxConcurrentAgents);
    } else if (log.isDebugEnabled()) {
      log.debug(
          "PriorityScheduler degraded (suppressed WARN): oldest_overdue={}s > min_interval={}s; ready={} capacity_per_cycle={} running={} max_concurrent={}",
          oldestOverdueSec,
          minIntervalSec,
          readyCount,
          capacityPerCycle,
          currentlyRunning,
          maxConcurrentAgents);
    }
  }

  /**
   * Acquires agents in batches to control memory consumption and lock contention in large
   * deployments. Uses {@code agentAcquisitionBatchSize} to limit the number of agents processed in
   * each Redis operation.
   *
   * @param jedis Redis connection
   * @param readyAgents Set of agent types ready for execution
   * @param maxToAcquire Maximum number of agents to acquire (concurrency limit)
   * @param maxConcurrentSemaphore Semaphore for concurrency control
   * @param workersToSubmit Collection to add successfully acquired workers
   * @return Number of agents successfully acquired
   */
  private int saturatePoolBatch(
      Jedis jedis,
      Set<String> readyAgents,
      int maxToAcquire,
      Semaphore maxConcurrentSemaphore,
      Set<AgentWorker> workersToSubmit,
      java.util.Set<String> attemptedThisCycle,
      java.util.Map<String, AgentWorker> registrySnapshot,
      Long nowMsCached,
      long batchStartMs) {

    // Calculate batch size to prevent memory/Redis overload
    int configuredBatchSize = schedulerProperties.getBatchOperations().getBatchSize();
    int effectiveBatchSize =
        configuredBatchSize > 0 ? Math.min(maxToAcquire, configuredBatchSize) : maxToAcquire;

    log.debug(
        "Using batch agent acquisition: {} ready agents, max: {}, batch size: {}",
        readyAgents.size(),
        maxToAcquire,
        effectiveBatchSize);

    int candidateCount = 0;
    java.util.List<String> candidateAgents = REUSABLE_CANDIDATE_AGENTS.get();
    java.util.List<AgentWorker> candidateWorkers = REUSABLE_CANDIDATE_WORKERS.get();
    candidateAgents.clear();
    candidateWorkers.clear();

    // Phase 1: Pre-filter ready agents using local registry + enablement/sharding
    // This avoids acquiring permits for agents we will filter out anyway during candidate building,
    // reducing wasted work and permit churn under heavy filtering.
    java.util.List<String> eligibleAgents = REUSABLE_ELIGIBLE_AGENTS.get();
    eligibleAgents.clear();
    for (String agentType : readyAgents) {
      AgentWorker worker = registrySnapshot.get(agentType);
      if (worker == null) {
        log.debug("Agent {} not in registry during pre-filter, skipping", agentType);
        continue;
      }
      if (!isAgentEnabled(worker.getAgent())) {
        log.debug("Agent {} filtered by enablement/sharding during pre-filter", agentType);
        continue;
      }
      eligibleAgents.add(agentType);
    }

    // Phase 2: Build candidate list and acquire semaphore permits
    // Note: We respect both the concurrency limit (maxToAcquire) and batch size limit
    for (String agentType : eligibleAgents) {
      if (attemptedThisCycle != null && attemptedThisCycle.contains(agentType)) {
        continue;
      }
      if (candidateCount >= effectiveBatchSize) {
        log.debug(
            "Reached batch size limit: {} agents prepared for acquisition", effectiveBatchSize);
        break;
      }

      AgentWorker worker = registrySnapshot.get(agentType);
      if (worker == null) {
        log.warn(
            "Agent {} not found in local registry, skipping (may have been dynamically removed)",
            agentType);
        continue;
      }

      // Double-check sharding/enablement gating before acquiring permit to prevent permit mismatch
      // This ensures we don't acquire permits for agents that will be filtered out by sharding
      if (!isAgentEnabled(worker.getAgent())) {
        log.debug(
            "Skipping candidate agent {} due to sharding/enablement filter before permit acquisition",
            agentType);
        continue;
      }

      if (maxConcurrentSemaphore != null && !maxConcurrentSemaphore.tryAcquire()) {
        log.debug("Semaphore limit reached at {} agents", candidateCount);
        break;
      }

      // At this point, we have the permit and the agent passed all filters

      candidateAgents.add(agentType);
      candidateWorkers.add(worker);
      candidateCount++; // Track candidates prepared
      if (attemptedThisCycle != null) {
        attemptedThisCycle.add(agentType);
      }
    }

    if (candidateAgents.isEmpty()) {
      log.debug("No valid candidate agents for batch acquisition");
      return 0;
    }

    // Phase 3: Batch acquire agents using Redis Lua script
    // Track permits acquired in Phase 2 for accurate catch block cleanup
    // IMPORTANT: Capture this BEFORE Phase 3 modifies candidateAgents (removing invalid pairs)
    final int permitsAcquiredForBatch = candidateAgents.size();
    // Track permits released during processing for catch block permit accounting
    int permitsReleasedInLoop = 0;
    // Track actual workers added to workersToSubmit for accurate return value
    int actualWorkersAdded = 0;

    try {
      // Prepare Redis Lua script arguments: [agent1, score1, agent2, score2, ...]
      // The Lua script expects alternating agent names and scores
      java.util.List<String> agentScorePairs = REUSABLE_AGENT_SCORE_PAIRS.get();
      agentScorePairs.clear();

      // Phase 3: Build agentScorePairs with validation
      // Important: When validation fails, we must remove the agent from
      // candidateAgents/candidateWorkers
      // to maintain 1:1 index correspondence for Phase 4 processing.
      // Iterate backwards to safely remove while iterating.
      for (int i = candidateAgents.size() - 1; i >= 0; i--) {
        String agentType = candidateAgents.get(i);
        AgentWorker worker = candidateWorkers.get(i);

        // Generate completion deadline for this agent (current time + timeout)
        long agentTimeout = intervalProvider.getInterval(worker.getAgent()).getTimeout();
        String deadlineScore = score(jedis, agentTimeout, nowMsCached);

        // Validate pair before adding: agent non-numeric, score numeric
        boolean scoreNumeric = deadlineScore != null && deadlineScore.matches("^\\d+$");
        boolean agentNumeric = agentType != null && agentType.matches("^\\d+$");
        if (!scoreNumeric || agentNumeric) {
          metrics.incrementInvalidPair("acquire_batch");
          // Skip invalid pair; release semaphore since we won't attempt this one
          if (maxConcurrentSemaphore != null) {
            maxConcurrentSemaphore.release();
            permitsReleasedInLoop++;
          }
          // Remove from candidate lists to maintain index alignment with agentScorePairs
          candidateAgents.remove(i);
          candidateWorkers.remove(i);
          continue;
        }

        // Add in reverse per-pair order (score, agent) since we iterate backwards.
        // After Collections.reverse(), this becomes correct order (agent, score).
        // Final list format: [agent0, score0, agent1, score1, ...]
        // where even indices (0, 2, 4...) are agent names, odd indices (1, 3, 5...) are scores
        agentScorePairs.add(deadlineScore);
        agentScorePairs.add(agentType);
      }

      // Reverse to restore original candidateAgents order (we iterated backwards for safe removal)
      // This is O(n) vs O(n²) for repeated add(0, ...) insertions.
      // Note: We added (score, agent) per iteration, so after reverse we get (agent, score).
      java.util.Collections.reverse(agentScorePairs);

      // Invariant: candidateAgents and agentScorePairs must be aligned
      // Each candidate has exactly one (agent, score) pair in agentScorePairs
      if (candidateAgents.size() * 2 != agentScorePairs.size()) {
        log.error(
            "Index alignment violated: candidateAgents.size()={} but agentScorePairs.size()={}",
            candidateAgents.size(),
            agentScorePairs.size());
        metrics.incrementAcquireValidationFailure("index_alignment");
        return 0;
      }

      // Execute batch acquisition Lua script
      Object result =
          scriptManager.evalshaWithSelfHeal(
              jedis,
              RedisScriptManager.ACQUIRE_AGENTS,
              Arrays.asList(WORKING_SET, WAITING_SET),
              agentScorePairs);

      // Phase 4: Process batch acquisition results
      if (result instanceof List) {
        // acquireAgents returns a Lua array: [count, [acquiredAgent1, acquiredAgent2, ...]]
        // Jedis can surface elements as Long, String, or byte[] depending on codec/version.
        // Coerce types defensively to avoid ClassCastException and keep the pod progressing.
        List<?> resultList = (List<?>) result;
        long successCount = 0L;
        try {
          Object c0 = resultList.size() > 0 ? resultList.get(0) : 0L;
          if (c0 instanceof Long) {
            successCount = (Long) c0;
          } else if (c0 instanceof String) {
            successCount = Long.parseLong((String) c0);
          } else if (c0 instanceof byte[]) {
            successCount =
                Long.parseLong(new String((byte[]) c0, java.nio.charset.StandardCharsets.UTF_8));
          }
        } catch (Exception ex) {
          metrics.incrementAcquireValidationFailure("batch_result_count_parse");
        }

        Set<String> acquiredAgentTypes = new HashSet<>();
        if (resultList.size() > 1) {
          Object list1 = resultList.get(1);
          if (list1 instanceof List) {
            for (Object element : (List<?>) list1) {
              if (element instanceof String) {
                acquiredAgentTypes.add((String) element);
              } else if (element instanceof byte[]) {
                acquiredAgentTypes.add(
                    new String((byte[]) element, java.nio.charset.StandardCharsets.UTF_8));
              } else if (element != null) {
                acquiredAgentTypes.add(String.valueOf(element));
              }
            }
          }
        }

        // Validate parsing consistency: ensure parsed agent list matches Redis-reported count
        // This prevents silent permit leaks if parsing fails or Redis returns unexpected format
        if (acquiredAgentTypes.size() != successCount) {
          if (shouldWarnNow(lastBatchParsingMismatchWarnEpochMs, 600_000)) {
            log.warn(
                "Batch acquisition parsing mismatch: Redis returned count={}, parsed agents={}. "
                    + "This may indicate parsing failure or unexpected Redis response format. "
                    + "Parsed agents will be processed normally; unparsed agents will have permits "
                    + "released in the processing loop below.",
                successCount,
                acquiredAgentTypes.size());
          } else if (log.isDebugEnabled()) {
            log.debug(
                "Batch acquisition parsing mismatch (suppressed WARN): Redis returned count={}, parsed agents={}",
                successCount,
                acquiredAgentTypes.size());
          }
          metrics.incrementAcquireValidationFailure("batch_result_count_mismatch");
          // Note: Permits for candidates not in acquiredAgentTypes will be released in another
          // processing loop. If successCount > acquiredAgentTypes.size(),
          // there are unparsed agents that Redis says were acquired, but we couldn't parse them.
          // These will be treated as failed acquisitions and their permits will be released.
          // This prevents permit leaks at the cost of potentially retrying agents that were
          // actually acquired but couldn't be parsed.
        }

        log.debug(
            "Batch acquisition completed: {} successes out of {} attempts",
            successCount,
            candidateAgents.size());

        // Process each candidate agent to see if it was successfully acquired
        for (int candidateIndex = 0; candidateIndex < candidateAgents.size(); candidateIndex++) {
          String agentType = candidateAgents.get(candidateIndex);

          if (acquiredAgentTypes.contains(agentType)) {
            // Success: This pod acquired the agent - set up for execution
            AgentWorker worker = candidateWorkers.get(candidateIndex);

            // Extract the agent's score from agentScorePairs array
            // Array structure: [agent1, score1, agent2, score2, ...]
            // For agent at index candidateIndex: score is at position (candidateIndex * 2 + 1)

            // Critical: Validate index and score format to prevent corruption
            // from concurrent external modifications during batch acquisition
            String deadlineScore = null;
            int scoreIndex = candidateIndex * 2 + 1;

            if (scoreIndex < agentScorePairs.size()) {
              String scoreCandidate = agentScorePairs.get(scoreIndex);
              // Validate that the score is a numeric string (timestamp in seconds)
              if (scoreCandidate != null && scoreCandidate.matches("^\\d+$")) {
                deadlineScore = scoreCandidate;
              } else {
                log.error(
                    "Invalid acquire score detected for agent {} at index {}: '{}' - likely corruption from concurrent external modification",
                    agentType,
                    scoreIndex,
                    scoreCandidate);
              }
            } else {
              log.error(
                  "Score index {} out of bounds for agent {} (agentScorePairs.size={}). Concurrent external modification likely occurred during batch acquisition.",
                  scoreIndex,
                  agentType,
                  agentScorePairs.size());
            }

            // Only proceed if we have a valid score
            if (deadlineScore != null) {
              worker.deadlineScore = deadlineScore;
              workersToSubmit.add(worker);
              actualWorkersAdded++;
              activeAgents.put(agentType, deadlineScore);
              activeAgentMapSize.incrementAndGet();
              agentsAcquired.increment();

              log.debug("Batch acquired agent {} with score {}", agentType, deadlineScore);
            } else {
              // Could not get valid score - release permit and skip this agent
              if (maxConcurrentSemaphore != null) {
                maxConcurrentSemaphore.release();
                permitsReleasedInLoop++;
              }
              log.warn(
                  "Skipping agent {} due to invalid/missing acquire score - will retry on next cycle",
                  agentType);
              metrics.incrementAcquireValidationFailure("batch_score_corruption");
            }
          } else {
            // Failure: Agent lost to another pod in race condition
            // Release the semaphore permit we pre-acquired
            if (maxConcurrentSemaphore != null) {
              maxConcurrentSemaphore.release();
              permitsReleasedInLoop++;
            }
            log.debug("Agent {} was acquired by another pod", agentType);
          }
        }

        log.debug(
            "Batch acquisition completed: {}/{} workers added (Redis reported {} successes)",
            actualWorkersAdded,
            candidateAgents.size(),
            successCount);
        // Return actual workers added to workersToSubmit (not Redis successCount).
        // successCount can exceed actualWorkersAdded when agents are acquired by Redis
        // but skipped due to invalid scores during Java-side validation.
        return actualWorkersAdded;
      }

      log.warn("Unexpected batch acquisition result: {}", result);
      // Release all semaphore permits on batch failure
      if (maxConcurrentSemaphore != null) {
        for (int releaseIndex = 0; releaseIndex < candidateAgents.size(); releaseIndex++) {
          maxConcurrentSemaphore.release();
        }
      }
      return 0;
    } catch (Throwable e) {
      // Design note: Catch Throwable to ensure permit cleanup on all failure modes.
      // - Critical: Phase 2 acquired permits for all candidates. If an Error (e.g.,
      //   OutOfMemoryError) occurs during Phase 3 (Redis batch) or Phase 4 (result processing),
      //   we must release all acquired permits to prevent permit leaks.
      // - The scheduler's outer catch(Throwable) would eventually catch Errors, but by then permits
      //   are already leaked, causing permanent capacity loss until pod restart.
      log.error("Batch agent acquisition failed, falling back to individual mode", e);

      // Record fallback metrics - this ensures metrics are recorded even when exception
      // doesn't propagate to outer catch block
      metrics.incrementBatchFallback();
      metrics.recordAcquireTime("fallback", System.currentTimeMillis() - batchStartMs);

      // Clean up partial state before fallback:
      // 1. Remove activeAgents entries for workers added before exception (fallback will
      // re-acquire)
      // 2. Clear workersToSubmit so fallback starts fresh
      // 3. Release only permits that weren't already released in the loop
      //
      // Permit accounting:
      // - Phase 2 acquired permitsAcquiredForBatch permits (captured BEFORE Phase 3)
      // - Phase 3 validation + Phase 4 loop released permitsReleasedInLoop permits
      // - Remaining permits held = permitsAcquiredForBatch - permitsReleasedInLoop
      //   (includes partial successes in workersToSubmit + unprocessed candidates)
      //
      // IMPORTANT: We use permitsAcquiredForBatch (not candidateAgents.size()) because
      // Phase 3 both removes from candidateAgents AND increments permitsReleasedInLoop.
      // Using candidateAgents.size() would double-count those removals.

      // Clean up activeAgents entries for partial successes
      for (AgentWorker worker : workersToSubmit) {
        String agentType = worker.getAgent().getAgentType();
        if (activeAgents.remove(agentType) != null) {
          activeAgentMapSize.decrementAndGet();
        }
      }

      // Clear workersToSubmit - fallback will rebuild from scratch
      workersToSubmit.clear();

      // Release permits that are still held (not already released in loop)
      int permitsToRelease = permitsAcquiredForBatch - permitsReleasedInLoop;
      if (maxConcurrentSemaphore != null && permitsToRelease > 0) {
        for (int releaseIndex = 0; releaseIndex < permitsToRelease; releaseIndex++) {
          maxConcurrentSemaphore.release();
        }
        log.debug(
            "Released {} permits during batch fallback cleanup (acquired={}, already_released={})",
            permitsToRelease,
            permitsAcquiredForBatch,
            permitsReleasedInLoop);
      }

      // Fallback to individual acquisition
      return saturatePoolIndividual(
          jedis,
          new HashSet<>(candidateAgents),
          maxToAcquire,
          maxConcurrentSemaphore,
          workersToSubmit,
          attemptedThisCycle,
          registrySnapshot,
          nowMsCached);
    } finally {
      REUSABLE_AGENT_SCORE_PAIRS.get().clear();
      REUSABLE_ELIGIBLE_AGENTS.get().clear();
      REUSABLE_CANDIDATE_WORKERS.get().clear();
      REUSABLE_CANDIDATE_AGENTS.get().clear();
    }
  }

  /**
   * This is the original individual acquisition logic, kept as fallback when batch operations are
   * disabled or fail.
   *
   * @param jedis Redis connection
   * @param readyAgents Set of agent types ready for execution
   * @param maxToAcquire Maximum number of agents to acquire
   * @param maxConcurrentSemaphore Semaphore for concurrency control
   * @param workersToSubmit Collection to add successfully acquired workers
   * @return Number of agents successfully acquired
   */
  private int saturatePoolIndividual(
      Jedis jedis,
      Set<String> readyAgents,
      int maxToAcquire,
      Semaphore maxConcurrentSemaphore,
      Set<AgentWorker> workersToSubmit,
      java.util.Set<String> attemptedThisCycle,
      java.util.Map<String, AgentWorker> registrySnapshot,
      Long nowMsCached) {

    log.debug(
        "Using individual agent acquisition for {} ready agents (max: {})",
        readyAgents.size(),
        maxToAcquire);

    int agentsAcquiredThisCycle = 0;

    for (String agentType : readyAgents) {
      if (attemptedThisCycle != null && attemptedThisCycle.contains(agentType)) {
        continue;
      }
      if (agentsAcquiredThisCycle >= maxToAcquire) {
        log.debug(
            "Reached available slot limit for new agents this cycle ({} acquired out of {} target slots).",
            agentsAcquiredThisCycle,
            maxToAcquire);
        break;
      }

      // Check worker existence and sharding/enablement before acquiring permit to prevent permit
      // mismatch
      AgentWorker worker = registrySnapshot.get(agentType);
      if (worker == null) {
        log.warn("Ready agent {} not found in local agents map, skipping.", agentType);
        continue;
      }

      // Double-check sharding/enablement gating before acquiring permit to prevent permit mismatch
      if (!isAgentEnabled(worker.getAgent())) {
        log.debug(
            "Skipping ready agent {} due to sharding/enablement filter before permit acquisition",
            agentType);
        continue;
      }

      if (maxConcurrentSemaphore != null && !maxConcurrentSemaphore.tryAcquire()) {
        log.debug(
            "Instance concurrent agent limit reached (no permits from 'maxConcurrentSemaphore' semaphore). Cannot acquire more agents this cycle.");
        break; // Stop trying if semaphore is full
      }

      // Semaphore permit acquired - agent passed all filters

      // Try to acquire this agent from Redis
      String agentAcquireScore = tryAcquireAgent(jedis, worker.getAgent(), nowMsCached);
      if (agentAcquireScore != null) {
        // Successfully acquired agent, prepare for execution
        worker.deadlineScore = agentAcquireScore;
        workersToSubmit.add(worker);
        agentsAcquiredThisCycle++;

        // Track active agent
        activeAgents.put(agentType, agentAcquireScore);
        activeAgentMapSize.incrementAndGet();
        agentsAcquired.increment(); // Track acquisition statistics

        log.debug("Acquired agent {} with score {}", agentType, agentAcquireScore);
        if (attemptedThisCycle != null) {
          attemptedThisCycle.add(agentType);
        }
      } else {
        // Failed to acquire (another instance got it first)
        if (maxConcurrentSemaphore != null) {
          maxConcurrentSemaphore.release();
        }
        log.debug("Agent {} was acquired by another instance, releasing permit", agentType);
        if (attemptedThisCycle != null) {
          attemptedThisCycle.add(agentType);
        }
      }
    }

    return agentsAcquiredThisCycle;
  }

  /**
   * Determine if the provided agent type belongs to this shard according to the configured {@link
   * ShardingFilter}.
   *
   * <p>Details: - Uses the registered {@link Agent} if available; otherwise uses a lightweight stub
   * based on agentType only (providerName defaults to "unknown"). - When sharding is disabled, a
   * {@link com.netflix.spinnaker.cats.cluster.NoopShardingFilter} is wired which always returns
   * true, so ownership checks pass and cleanup proceeds. - Returns false on unexpected errors to
   * avoid cross-shard deletions in cleanup flows.
   */
  @VisibleForTesting
  boolean belongsToThisShard(String agentType) {
    try {
      AgentWorker worker = agents.get(agentType);
      Agent agent = worker != null ? worker.getAgent() : new AgentTypeOnlyStub(agentType);
      return shardingFilter.filter(agent);
    } catch (Exception e) {
      log.debug("Unable to determine shard ownership for {}", agentType, e);
      return false;
    }
  }

  /** Minimal Agent implementation for sharding checks when only agentType is available. */
  private static final class AgentTypeOnlyStub implements com.netflix.spinnaker.cats.agent.Agent {
    private final String agentType;

    AgentTypeOnlyStub(String agentType) {
      this.agentType = agentType;
    }

    @Override
    public String getAgentType() {
      return agentType;
    }

    @Override
    public String getProviderName() {
      return "unknown";
    }

    @Override
    public com.netflix.spinnaker.cats.agent.AgentExecution getAgentExecution(
        com.netflix.spinnaker.cats.provider.ProviderRegistry providerRegistry) {
      throw new UnsupportedOperationException("Not supported in shard ownership checks");
    }
  }

  /**
   * Register an agent for scheduling.
   *
   * @param agent The agent to register
   * @param agentExecution The execution wrapper for the agent
   * @param executionInstrumentation Instrumentation for tracking agent execution
   */
  public void registerAgent(
      Agent agent,
      AgentExecution agentExecution,
      ExecutionInstrumentation executionInstrumentation) {
    if (!isAgentEnabled(agent)) {
      log.debug(
          "Agent is not enabled (agent: {}, agentType: {}, pattern: {})",
          agent.getClass().getSimpleName(),
          agent.getAgentType(),
          enabledAgentPattern.pattern());
      return;
    }

    AgentWorker worker = new AgentWorker(agent, agentExecution, executionInstrumentation, this);

    // Collision detection: warn if replacing an existing agent with the same type
    AgentWorker existing = agents.get(agent.getAgentType());
    if (existing != null) {
      log.warn(
          "Agent type collision detected: {} (replacing {} with {})",
          agent.getAgentType(),
          existing.getAgent().getClass().getSimpleName(),
          agent.getClass().getSimpleName());
    }

    agents.put(agent.getAgentType(), worker);
    agentMapSize.set(agents.size()); // Update statistics

    // Update cached minimum interval based on this agent if enabled
    try {
      if (isAgentEnabled(agent)) {
        long intervalSec = Math.max(0L, intervalProvider.getInterval(agent).getInterval() / 1000L);
        if (intervalSec > 0L) {
          cachedMinEnabledIntervalSec.getAndUpdate(
              prev -> (prev == 0L) ? intervalSec : Math.min(prev, intervalSec));
        }
      }
    } catch (Exception ignore) {
      // Best-effort only
    }

    // Log enhanced registration info with operational details
    AgentIntervalProvider.Interval interval = intervalProvider.getInterval(agent);
    String initialScore = agentScore(agent);

    log.debug(
        "Registered agent {} (interval {}s/timeout {}s) for scheduling [score {}/total agents {}]",
        agent.getAgentType(),
        interval.getInterval() / 1000,
        interval.getTimeout() / 1000,
        initialScore,
        agents.size());

    // Persist the agent into Redis immediately only if scripts are initialized. When scripts are
    // not yet initialized (e.g., during early scheduler bootstrap), defer to repopulation so that
    // initial-registration jitter (when enabled) can be applied and to preserve historical tests.
    if (scriptManager.isInitialized()) {
      try {
        scheduleAgentInRedis(agent, 0L);
      } catch (Exception e) {
        log.warn(
            "Failed to write initial Redis entry for agent {} – will rely on repopulation: {}",
            agent.getAgentType(),
            e);
      }
    } else {
      log.debug(
          "Deferring initial Redis write for agent {} until repopulation (scripts not initialized)",
          agent.getAgentType());
    }
  }

  /**
   * Unregisters an agent from the scheduler. This removes the agent from local tracking and
   * prevents it from being scheduled for execution.
   *
   * @param agent The agent to unregister
   */
  public void unregisterAgent(Agent agent) {
    String agentType = agent.getAgentType();
    agents.remove(agentType);

    // Remove any local failure backoff state for this agent (important for dynamically removed
    // agents)
    failureStreaks.remove(agentType);

    // Check if agent has an active RunState (meaning it's currently executing with a permit).
    // We must NOT remove from activeAgents if a permit is held, otherwise permit_mismatch occurs
    // (heldPermits > activeAgents.size). The worker's finally block will handle cleanup when
    // the permit is actually released.
    Future<?> future = activeAgentsFutures.remove(agentType);
    RunState runState = runStates.get(agentType);

    if (runState != null && runState.permitHeld.get()) {
      // Agent is currently executing with a permit held - cancel future but DEFER activeAgents
      // cleanup to the worker's finally block. This prevents permit_mismatch where we remove
      // from activeAgents before the permit is released.
      if (future != null) {
        future.cancel(false);
      }
      log.debug(
          "Unregister deferred: agent {} has permit held, cleanup deferred to worker finally",
          agentType);
    } else {
      // No permit held - safe to remove from activeAgents immediately
      boolean wasActive = activeAgents.remove(agentType) != null;
      if (wasActive) {
        activeAgentMapSize.decrementAndGet();
      }

      if (future != null) {
        // Future exists but no RunState or permit not held - cancel and log
        future.cancel(false);
        if (runState == null) {
          log.warn(
              "Unexpected state: agent {} has future but no RunState during unregistration",
              agentType);
        }
      }
    }

    log.debug("Unregistered agent {} from scheduling", agentType);

    // Recompute cached minimum interval conservatively when an agent is removed
    try {
      long minSec =
          agents.values().stream()
              .map(AgentWorker::getAgent)
              .filter(this::isAgentEnabled)
              .mapToLong(a -> intervalProvider.getInterval(a).getInterval() / 1000L)
              .filter(v -> v > 0L)
              .min()
              .orElse(0L);
      cachedMinEnabledIntervalSec.set(minSec);
    } catch (Exception ignore) {
      // Best-effort only
    }
  }

  /**
   * Removes an agent from active tracking. Called when an agent execution completes or is
   * interrupted.
   *
   * @param agentType The type identifier of the agent to remove
   */
  @VisibleForTesting
  void removeActiveAgent(String agentType) {
    // Redis work (working→waiting transition) is now handled atomically by
    // conditionalReleaseAgent() via atomicRescheduleInRedis(). This method only cleans up local
    // in-memory tracking.
    //
    // Critical: Capture removed value to ensure atomic consistency between map and counter
    String removedScore = activeAgents.remove(agentType);
    if (removedScore != null) {
      // Only decrement counter if we actually removed something
      activeAgentMapSize.decrementAndGet();
      // Remove future tracking - this cleanup is non-critical if it fails
      activeAgentsFutures.remove(agentType);
      log.debug(
          "Removed agent {} from active tracking (Redis handled by atomicReschedule)", agentType);
    }
  }

  /**
   * Removes an agent from active tracking AND releases its permit via CAS.
   *
   * <p>This method should be used by cleanup paths (reconcile validation, state consistency checks)
   * that need to remove agents that may still be holding permits. It uses the same CAS mechanism as
   * the worker's finally block to ensure exactly-once permit release.
   *
   * <p>Unlike {@link #removeActiveAgent(String)}, this method:
   *
   * <ul>
   *   <li>Removes the RunState and releases the permit via CAS (if held)
   *   <li>Cancels any associated future (non-interrupt to allow graceful cleanup)
   *   <li>Removes from activeAgents and activeAgentsFutures
   * </ul>
   *
   * @param agentType The type identifier of the agent to remove
   * @return true if the agent was found and removed, false if not present
   */
  public boolean removeActiveAgentWithPermitRelease(String agentType) {
    // Step 1: Release permit via CAS (exactly-once, same pattern as worker finally block)
    RunState runState = runStates.remove(agentType);
    if (runState != null && runState.permitHeld.compareAndSet(true, false)) {
      if (maxConcurrentSemaphoreRef != null) {
        maxConcurrentSemaphoreRef.release();
        log.debug("Released permit for {} during cleanup with permit release", agentType);
      }
      // Cancel dead-man timer if present
      if (runState.deadmanHandle != null) {
        try {
          runState.deadmanHandle.cancel(false);
        } catch (Exception ignore) {
        }
      }
    }

    // Step 2: Cancel future (non-interrupt to allow worker's finally block to run if in progress)
    Future<?> future = activeAgentsFutures.remove(agentType);
    if (future != null && !future.isDone()) {
      future.cancel(false);
    }

    // Step 3: Remove from activeAgents map
    String removedScore = activeAgents.remove(agentType);
    if (removedScore != null) {
      activeAgentMapSize.decrementAndGet();
      log.debug("Removed agent {} from active tracking with permit release", agentType);
      return true;
    }

    return runState != null; // Return true if we found RunState even if not in activeAgents
  }

  /**
   * Get the number of currently active agents.
   *
   * @return number of active agents
   */
  public int getActiveAgentCount() {
    return activeAgents.size();
  }

  /**
   * Get the total number of registered agents (for stats).
   *
   * @return number of registered agents
   */
  public int getRegisteredAgentCount() {
    return agents.size();
  }

  /**
   * Get a snapshot of active agent futures for graceful shutdown. Returns a copy to avoid
   * concurrent modification issues.
   *
   * @return Map of agent type to Future for active agents
   */
  public Map<String, Future<?>> getActiveAgentsFuturesSnapshot() {
    Map<String, Future<?>> snapshot = new HashMap<>();
    for (Map.Entry<String, Future<?>> entry : activeAgentsFutures.entrySet()) {
      snapshot.put(entry.getKey(), entry.getValue());
    }
    return snapshot;
  }

  /**
   * Get a registered agent by type.
   *
   * @param agentType The agent type to retrieve
   * @return The registered Agent, or null if not found
   */
  public Agent getRegisteredAgent(String agentType) {
    AgentWorker worker = agents.get(agentType);
    return worker != null ? worker.getAgent() : null;
  }

  /**
   * Check if an agent is currently executing (has a RunState with permitHeld=true).
   *
   * <p>This is used by cleanup paths to avoid racing with the worker's finally block. If this
   * returns true, the cleanup should be skipped and left to the worker's finally block.
   *
   * @param agentType The agent type to check
   * @return true if the agent has a RunState with permitHeld=true, false otherwise
   */
  public boolean isAgentCurrentlyExecuting(String agentType) {
    RunState runState = runStates.get(agentType);
    return runState != null && runState.permitHeld.get();
  }

  /**
   * Get the count of RunState entries (diagnostic for permit_mismatch investigation).
   *
   * @return number of entries in runStates map
   */
  public int getRunStatesCount() {
    return runStates.size();
  }

  /**
   * Get the count of RunState entries that still have permitHeld=true (diagnostic for
   * permit_mismatch investigation).
   *
   * @return number of RunState entries with permitHeld=true
   */
  public int getRunStatesWithPermitHeldCount() {
    int count = 0;
    for (RunState rs : runStates.values()) {
      if (rs.permitHeld.get()) {
        count++;
      }
    }
    return count;
  }

  /**
   * Get the active agents map for zombie cleanup.
   *
   * @return map of active agents (agentType -> completionDeadline)
   */
  @VisibleForTesting
  Map<String, String> getActiveAgentsMap() {
    return activeAgents;
  }

  /**
   * Get active agents futures for cleanup services.
   *
   * @return Map of active agent futures
   */
  @VisibleForTesting
  Map<String, Future<?>> getActiveAgentsFutures() {
    return activeAgentsFutures;
  }

  /**
   * Get the current size of the agent futures map.
   *
   * @return Current number of agent futures being tracked
   */
  @VisibleForTesting
  int getFuturesMapSize() {
    return activeAgentsFutures.size();
  }

  long getOldestOverdueSeconds() {
    return lastOldestOverdueSeconds.get();
  }

  long getReadyCountSnapshot() {
    return lastReadyCount.get();
  }

  long getCapacityPerCycleSnapshot() {
    return lastCapacityPerCycle.get();
  }

  @VisibleForTesting
  int getCompletionQueueSize() {
    return completionQueue.size();
  }

  @VisibleForTesting
  long getServerClientOffsetMs() {
    return serverClientOffset.get();
  }

  /**
   * Returns current wall-clock time adjusted by the Redis server-client offset.
   *
   * <p>Used by cleanup services to avoid additional Redis TIME calls and to keep time sourcing
   * consistent across acquisition and cleanup paths.
   */
  public long nowMsWithOffset() {
    return System.currentTimeMillis() + serverClientOffset.get();
  }

  /**
   * Compute the original ready time (in epoch seconds) for an agent currently in the working set.
   *
   * <p>Working score encodes the completion deadline: acquire_time + timeout. To preserve the
   * agent's original position when re-queuing (e.g., during orphan cleanup), we subtract the
   * provider-configured timeout to recover the original ready score used while in the waiting set.
   *
   * @param agentType The agent identifier
   * @param workingScoreSeconds The score from the working set as a decimal string (epoch seconds)
   * @return The original ready time in epoch seconds as a string, or null if unavailable
   */
  public String computeOriginalReadySecondsFromWorkingScore(
      String agentType, String workingScoreSeconds) {
    try {
      if (agentType == null || workingScoreSeconds == null) {
        return null;
      }
      Agent agent = getAgentByType(agentType);
      if (agent == null) {
        return null;
      }
      long timeoutMs = intervalProvider.getInterval(agent).getTimeout();
      long timeoutSeconds = Math.max(0L, timeoutMs / 1000L);
      long workingSeconds = Long.parseLong(workingScoreSeconds);
      long originalReadySeconds = Math.max(0L, workingSeconds - timeoutSeconds);
      return String.valueOf(originalReadySeconds);
    } catch (Exception e) {
      return null;
    }
  }

  /**
   * Check if the scheduler is in a degraded state. Part of health monitoring interface.
   *
   * @return true if degraded, false otherwise
   */
  public boolean isDegraded() {
    return lastDegraded.get();
  }

  @VisibleForTesting
  String getDegradedReason() {
    return lastDegradedReason.get();
  }

  /**
   * Result of a set consistency check.
   *
   * <p>Contains the number of agents sampled and any violations found (agents present in both
   * waiting and working sets simultaneously).
   */
  public static final class ConsistencyCheckResult {
    private final int sampled;
    private final int violations;
    private final List<String> violatingAgents;

    public ConsistencyCheckResult(int sampled, int violations, List<String> violatingAgents) {
      this.sampled = sampled;
      this.violations = violations;
      this.violatingAgents =
          violatingAgents != null ? violatingAgents : java.util.Collections.emptyList();
    }

    public int getSampled() {
      return sampled;
    }

    public int getViolations() {
      return violations;
    }

    public List<String> getViolatingAgents() {
      return violatingAgents;
    }

    public boolean hasViolations() {
      return violations > 0;
    }
  }

  /**
   * Check the mutual exclusion invariant: no agent should exist in both waiting and working sets.
   *
   * <p>This method samples agents from the waiting set and verifies none are also present in the
   * working set. This is a read-only diagnostic operation for defense-in-depth observability.
   *
   * <p>While Lua scripts enforce this invariant at write time, external Redis modifications (CLI,
   * other clients) could cause inconsistency. This check detects such violations.
   *
   * @param sampleSize Number of agents to sample (0 returns empty result)
   * @return Result containing sample count and any violations found
   */
  public ConsistencyCheckResult checkSetConsistency(int sampleSize) {
    if (sampleSize <= 0) {
      return new ConsistencyCheckResult(0, 0, java.util.Collections.emptyList());
    }

    try (Jedis jedis = jedisPool.getResource()) {
      // Sample agents from the waiting set
      List<String> sampledAgents = sampleAgentsFromWaitingSet(jedis, sampleSize);

      if (sampledAgents.isEmpty()) {
        return new ConsistencyCheckResult(0, 0, java.util.Collections.emptyList());
      }

      // Check each sampled agent against the working set using pipelined ZSCORE
      List<String> violations = checkAgentsInWorkingSet(jedis, sampledAgents);

      if (!violations.isEmpty()) {
        log.warn(
            "Set consistency check found {} violation(s) in {} sampled agents: {}",
            violations.size(),
            sampledAgents.size(),
            violations);
      }

      return new ConsistencyCheckResult(sampledAgents.size(), violations.size(), violations);

    } catch (Exception e) {
      log.debug("Set consistency check failed", e);
      // Return empty result on failure - don't let diagnostic failures affect health
      return new ConsistencyCheckResult(0, 0, java.util.Collections.emptyList());
    }
  }

  /** Sample agents from the waiting set using ZRANDMEMBER (Redis 6.2+) with fallback to ZRANGE. */
  private List<String> sampleAgentsFromWaitingSet(Jedis jedis, int sampleSize) {
    List<String> sampled = new ArrayList<>();

    try {
      // Try ZRANDMEMBER first (Redis 6.2+)
      // zrandmember with count returns a List<String>
      Object result = jedis.zrandmember(WAITING_SET, sampleSize);
      if (result instanceof List) {
        @SuppressWarnings("unchecked")
        List<String> randomMembers = (List<String>) result;
        if (!randomMembers.isEmpty()) {
          sampled.addAll(randomMembers);
          return sampled;
        }
      }
    } catch (Exception e) {
      // ZRANDMEMBER not available (Redis < 6.2) or failed - fall back to ZRANGE
      log.debug("ZRANDMEMBER not available, falling back to ZRANGE sampling", e);
    }

    // Fallback: use ZRANGE with random offset
    try {
      long setSize = jedis.zcard(WAITING_SET);
      if (setSize <= 0) {
        return sampled;
      }

      // For small sets, just get everything
      if (setSize <= sampleSize) {
        Set<String> all = jedis.zrange(WAITING_SET, 0, -1);
        if (all != null) {
          sampled.addAll(all);
        }
        return sampled;
      }

      // For larger sets, sample with random offset
      long maxOffset = Math.max(0, setSize - sampleSize);
      long randomOffset = (long) (Math.random() * (maxOffset + 1));
      Set<String> window = jedis.zrange(WAITING_SET, randomOffset, randomOffset + sampleSize - 1);
      if (window != null) {
        sampled.addAll(window);
      }
    } catch (Exception e) {
      log.debug("ZRANGE fallback sampling failed", e);
    }

    return sampled;
  }

  /**
   * Check if any of the given agents exist in the working set using pipelined ZSCORE.
   *
   * @return List of agent names that exist in the working set (violations)
   */
  private List<String> checkAgentsInWorkingSet(Jedis jedis, List<String> agents) {
    List<String> violations = new ArrayList<>();

    if (agents.isEmpty()) {
      return violations;
    }

    try {
      // Pipeline ZSCORE calls for efficiency
      Pipeline pipeline = jedis.pipelined();
      List<Response<Double>> responses = new ArrayList<>(agents.size());

      for (String agent : agents) {
        responses.add(pipeline.zscore(WORKING_SET, agent));
      }

      pipeline.sync();

      // Check responses for non-null scores (indicating presence in working set)
      for (int i = 0; i < agents.size(); i++) {
        try {
          Double score = responses.get(i).get();
          if (score != null) {
            // Agent exists in both sets - this is a violation
            violations.add(agents.get(i));
          }
        } catch (Exception e) {
          // Individual response failure - skip this agent
          log.debug("Failed to get ZSCORE response for agent {}", agents.get(i), e);
        }
      }
    } catch (Exception e) {
      log.debug("Pipeline ZSCORE check failed", e);
    }

    return violations;
  }

  /**
   * Get circuit breaker status for monitoring.
   *
   * @return Map containing status of each circuit breaker
   */
  public Map<String, String> getCircuitBreakerStatus() {
    Map<String, String> status = new HashMap<>();
    status.put("acquisition", acquisitionCircuitBreaker.getStatus());
    status.put("redis", redisCircuitBreaker.getStatus());
    return status;
  }

  /**
   * Get the current Redis circuit breaker state for watchdog diagnostics.
   *
   * @return breaker state, or {@code null} if unavailable
   */
  public PrioritySchedulerCircuitBreaker.State getRedisCircuitBreakerState() {
    return redisCircuitBreaker.getState();
  }

  /**
   * Expose agent properties for scheduler diagnostics/watchdog decisions.
   *
   * @return current {@link PriorityAgentProperties}
   */
  public PriorityAgentProperties getAgentProperties() {
    return agentProperties;
  }

  /** Reset circuit breakers (for recovery/testing). */
  public void resetCircuitBreakers() {
    acquisitionCircuitBreaker.reset();
    redisCircuitBreaker.reset();
    log.info("Circuit breakers manually reset");
  }

  /**
   * Retrieves an agent by its type identifier from the registered agents map. Used during graceful
   * shutdown to properly re-queue active agents.
   *
   * @param agentType The type identifier of the agent to retrieve
   * @return The agent if found, null otherwise
   */
  @VisibleForTesting
  Agent getAgentByType(String agentType) {
    AgentWorker worker = agents.get(agentType);
    return worker != null ? worker.getAgent() : null;
  }

  /**
   * Conditionally re-queue an agent during graceful shutdown if it's still in working. This
   * approach respects agents that completed during shutdown and avoids race conditions. Uses
   * ownership verification to ensure we only move agents this instance actually owns.
   */
  public void forceRequeueAgentForShutdown(Agent agent, String expectedScore) {
    String agentType = agent.getAgentType();

    try (Jedis jedis = jedisPool.getResource()) {
      // Calculate cadence-based or jittered execution score for restart (seconds since epoch)
      long offsetMs = computeShutdownRescheduleOffsetMs(agent, expectedScore);
      String nextScore = score(jedis, offsetMs);

      log.debug(
          "Shutdown re-queue attempt: {} expected_score={} next_score={}",
          agentType,
          expectedScore,
          nextScore);
      // Check current state in Redis before attempting swap
      Double currentWorkingScore = jedis.zscore(WORKING_SET, agentType);
      Double currentWaitingScore = jedis.zscore(WAITING_SET, agentType);
      log.debug(
          "Redis state before swap: {} {}={} {}={}",
          agentType,
          WORKING_SET,
          currentWorkingScore,
          WAITING_SET,
          currentWaitingScore);

      // Use moveAgentsConditional - only moves if agent is in working with expected score
      Object result =
          scriptManager.evalshaWithSelfHeal(
              jedis,
              RedisScriptManager.MOVE_AGENTS_CONDITIONAL,
              java.util.Arrays.asList(WORKING_SET, WAITING_SET),
              java.util.Arrays.asList(agentType, expectedScore, nextScore));

      // Check final state
      Double finalWorkingScore = jedis.zscore(WORKING_SET, agentType);
      Double finalWaitingScore = jedis.zscore(WAITING_SET, agentType);
      log.debug(
          "Redis state after swap: {} {}={} {}={} result={}",
          agentType,
          WORKING_SET,
          finalWorkingScore,
          WAITING_SET,
          finalWaitingScore,
          result);

      if (result != null && "swapped".equals(result)) {
        log.info("Successfully re-queued agent {} for shutdown restart", agentType);
      } else {
        log.warn(
            "Agent {} not re-queued (already completed or moved during shutdown) (expected_working={}, current_working={}, current_waiting={}, result={})",
            agentType,
            expectedScore,
            currentWorkingScore,
            currentWaitingScore,
            result);
      }

    } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
      log.warn(
          "Redis connection error while re-queuing {} during shutdown: {}",
          agentType,
          e.getMessage());
    } catch (Exception e) {
      log.error("Failed to conditionally re-queue agent {} during shutdown", agentType, e);
    }
  }

  /**
   * Get advanced scheduling statistics for monitoring and diagnostics. Exposes scheduler metrics
   * for external monitoring systems.
   *
   * @return AgentAcquisitionStats with detailed metrics including agent counts, acquisitions,
   *     executions, and failures
   */
  public AgentAcquisitionStats getAdvancedStats() {
    return new AgentAcquisitionStats(
        agentMapSize.get(),
        activeAgentMapSize.get(),
        agentsAcquired.sum(),
        agentsExecuted.sum(),
        agentsFailed.sum(),
        activeAgentsFutures.size());
  }

  /** Reset execution statistics counters. Useful for periodic reporting. */
  public void resetExecutionStats() {
    agentsAcquired.reset();
    agentsExecuted.reset();
    agentsFailed.reset();
  }

  private boolean isAgentEnabled(Agent agent) {
    String agentType = agent.getAgentType();

    // Check if this agent matches the sharding filter
    if (!shardingFilter.filter(agent)) {
      return false;
    }

    // Check enabled pattern
    if (!enabledAgentPattern.matcher(agentType).matches()) {
      return false;
    }

    // Check if agent is disabled (pattern or list)
    if (isAgentDisabled(agentType)) {
      log.debug("Agent {} is disabled", agentType);
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
    return disabledAgentPattern != null && disabledAgentPattern.matcher(agentType).matches();
  }

  /**
   * Repopulate Redis with known agents from the local agents map. Performs differential sync to
   * avoid unnecessary Redis operations.
   *
   * @param jedis Jedis connection to Redis
   */
  private void repopulateRedisAgents(Jedis jedis) {
    // Take a snapshot to prevent concurrent modification during repopulation
    Map<String, AgentWorker> agentsSnapshot = new ConcurrentHashMap<>(agents);
    int totalAgents = agentsSnapshot.size();
    if (log.isDebugEnabled()) {
      log.debug("Repopulation check for {} known agents", totalAgents);
    }

    if (totalAgents == 0) {
      log.debug("No agents to repopulate");
      return;
    }

    try {
      // Get current Redis state from both sets
      Set<String> localAgents = agentsSnapshot.keySet();
      Set<String> redisAgents = getCurrentRedisAgents(jedis, localAgents);

      // Calculate what needs to be added (missing agents from this instance)
      Set<String> toAdd =
          localAgents.stream()
              .filter(agent -> !redisAgents.contains(agent))
              .collect(Collectors.toSet());

      // True NOOP if nothing to add
      if (toAdd.isEmpty()) {
        log.debug("Repopulation: Redis state is consistent, no missing agents");
        // Note: cachedMinEnabledIntervalSec is event-driven, updated on agent registration
        return;
      }

      log.debug("Repopulation: +{} missing agents to add", toAdd.size());

      // Add missing agents from this instance
      addMissingAgents(jedis, toAdd);
      // Note: cachedMinEnabledIntervalSec is event-driven (updated when agents are registered),
      // not when repopulation runs. Repopulation syncs Redis for already-registered agents.

    } catch (Exception e) {
      log.warn("Repopulation failed, falling back to full sync", e);
      repopulateRedisAgentsFallback(jedis);
    }
  }

  /**
   * Gets all agent names currently in both Redis sets (working and waiting) using pipelining. This
   * method retrieves all agents from both Redis sorted sets in a single operation.
   *
   * @param jedis Redis connection to use
   * @param localAgentNames Set of local agent names to check in Redis
   * @return Set containing all agent names from both Redis sets
   */
  private Set<String> getCurrentRedisAgents(Jedis jedis, Set<String> localAgentNames) {
    // Avoid full-set scans: check presence for locally registered agents only
    List<String> agentNames = new ArrayList<>(localAgentNames);
    if (agentNames.isEmpty()) {
      return java.util.Collections.emptySet();
    }

    // Primary: Use ZMSCORE batches to check presence in working/waiting with minimal Redis work
    try {
      int batchSize;
      int configured = 0;
      try {
        configured = schedulerProperties.getBatchOperations().getBatchSize();
      } catch (Exception ignore) {
        // best effort
      }
      if (configured > 0) {
        batchSize = configured;
      } else {
        batchSize = agentNames.size();
      }
      Set<String> allAgents = new HashSet<>();
      for (int start = 0; start < agentNames.size(); start += batchSize) {
        int end = Math.min(start + batchSize, agentNames.size());
        List<String> batch = agentNames.subList(start, end);

        @SuppressWarnings("unchecked")
        List<Long> presence =
            (List<Long>)
                scriptManager.evalshaWithSelfHeal(
                    jedis,
                    RedisScriptManager.ZMSCORE_AGENTS,
                    Arrays.asList(WORKING_SET, WAITING_SET),
                    batch);

        for (int i = 0; i < batch.size(); i++) {
          Long presenceValue = (presence != null && i < presence.size()) ? presence.get(i) : 0L;
          if (presenceValue != null && presenceValue != 0L) {
            allAgents.add(batch.get(i));
          }
        }
      }
      return allAgents;
    } catch (Exception zmscoreEx) {
      // Fallback 1: Use existing Lua script query to avoid full scans
      // If scripts aren't initialized, try to initialize them first
      if (zmscoreEx instanceof IllegalStateException
          && zmscoreEx.getMessage() != null
          && zmscoreEx.getMessage().contains("Scripts not initialized")) {
        try {
          scriptManager.initializeScripts();
        } catch (Exception initEx) {
          log.warn("Failed to initialize scripts during getCurrentRedisAgents", initEx);
        }
      }
      log.warn("ZMSCORE presence check failed, falling back to Lua script", zmscoreEx);
      try {
        @SuppressWarnings("unchecked")
        List<String> results =
            (List<String>)
                scriptManager.evalshaWithSelfHeal(
                    jedis,
                    RedisScriptManager.SCORE_AGENTS,
                    Arrays.asList(WORKING_SET, WAITING_SET),
                    agentNames);

        Set<String> allAgents = new HashSet<>();
        for (int resultIndex = 0; resultIndex < results.size(); resultIndex += 3) {
          String agent = results.get(resultIndex);
          String workScore = results.get(resultIndex + 1);
          String waitScore = results.get(resultIndex + 2);
          if (!"null".equals(workScore) || !"null".equals(waitScore)) {
            allAgents.add(agent);
          }
        }
        return allAgents;
      } catch (Exception luaEx) {
        // Fallback 2: Full-set scan
        log.warn("Lua presence check failed, falling back to full-set scan", luaEx);
        Pipeline pipeline = jedis.pipelined();
        Response<Set<String>> waitingAgents = pipeline.zrange(WAITING_SET, 0, -1);
        Response<Set<String>> workingAgents = pipeline.zrange(WORKING_SET, 0, -1);
        pipeline.sync();
        Set<String> allAgents = new HashSet<>(waitingAgents.get());
        allAgents.addAll(workingAgents.get());
        return allAgents;
      }
    }
  }

  /**
   * Adds missing agents to Redis with appropriate scores. Delegates to either batch or individual
   * processing based on configuration. This is a critical component of the differential update
   * system that only adds agents not already present in Redis.
   *
   * @param jedis Redis connection to use
   * @param agentsToAdd Set of agent types to add to Redis
   */
  private void addMissingAgents(Jedis jedis, Set<String> agentsToAdd) {
    if (agentsToAdd == null || agentsToAdd.isEmpty()) {
      return;
    }
    if (schedulerProperties.getBatchOperations().isEnabled()) {
      addMissingAgentsBatch(jedis, agentsToAdd);
    } else {
      addMissingAgentsIndividual(jedis, agentsToAdd);
    }
  }

  private void addMissingAgentsBatch(Jedis jedis, Set<String> agentsToAdd) {
    List<String> batchArgs = new ArrayList<>();
    for (String agentType : agentsToAdd) {
      AgentWorker worker = agents.get(agentType);
      if (worker != null) {
        long jitterSec = computeInitialRegistrationJitterSeconds();
        String registrationScore = score(jedis, jitterSec * 1000L);
        if (!validateAgentScorePair(agentType, registrationScore, "repopulate_missing_batch")) {
          continue;
        }
        batchArgs.add(agentType);
        batchArgs.add(registrationScore);
      }
    }

    if (!batchArgs.isEmpty()) {
      try {
        List<?> result =
            (List<?>)
                scriptManager.evalshaWithSelfHeal(
                    jedis,
                    RedisScriptManager.ADD_AGENTS,
                    Arrays.asList(WORKING_SET, WAITING_SET),
                    batchArgs);
        int added = parseAddAgentsCount(result);
        log.debug("Batch added {} missing agents to Redis", added);
        if (added > 0) {
          metrics.incrementRepopulateAdded(added);
        }
      } catch (Exception e) {
        log.warn("Batch add failed, using individual mode", e);
        addMissingAgentsIndividual(jedis, agentsToAdd);
      }
    }
  }

  /**
   * Add missing agents to Redis one by one.
   *
   * @param jedis Jedis connection to Redis
   * @param agentsToAdd Set of agent types to add
   */
  private void addMissingAgentsIndividual(Jedis jedis, Set<String> agentsToAdd) {
    int added = 0;
    for (String agentType : agentsToAdd) {
      AgentWorker worker = agents.get(agentType);
      if (worker != null) {
        long jitterSec = computeInitialRegistrationJitterSeconds();
        String newScore = score(jedis, jitterSec * 1000L);
        try {
          Object result =
              scriptManager.evalshaWithSelfHeal(
                  jedis,
                  RedisScriptManager.ADD_AGENT,
                  Arrays.asList(WORKING_SET, WAITING_SET),
                  Arrays.asList(agentType, newScore));
          if (result != null && ((Long) result).intValue() == 1) {
            added++;
          }
        } catch (Exception e) {
          log.warn("Failed to add missing agent {}", agentType, e);
        }
      }
    }
    log.debug("Individual added {} missing agents to Redis", added);
    if (added > 0) {
      metrics.incrementRepopulateAdded(added);
    }
  }

  /**
   * Compute a non-negative jitter in seconds for initial registration of new agents. Returns 0 when
   * jitter is disabled.
   */
  private long computeInitialRegistrationJitterSeconds() {
    int window = schedulerProperties.getJitter().getInitialRegistrationSeconds();
    if (window <= 0) {
      return 0L;
    }
    // Use [1, window] inclusive to avoid zero-second placements that may appear slightly in the
    // past due to server/client second-boundary races when observed immediately after insert.
    return java.util.concurrent.ThreadLocalRandom.current().nextInt(1, window + 1);
  }

  /**
   * Fallback to full repopulation logic if smart sync fails.
   *
   * @param jedis Jedis connection to Redis
   */
  private void repopulateRedisAgentsFallback(Jedis jedis) {
    int totalAgents = agents.size();
    log.debug("Fallback: Full repopulation of {} agents", totalAgents);

    try {
      // Use batch scoring if enabled and we have multiple agents
      Map<String, String> agentScores;
      if (schedulerProperties.getBatchOperations().isEnabled() && totalAgents > 1) {
        // Batch scoring for multiple agents
        agentScores = batchAgentScore(jedis, agents.values());
        log.debug("Batch scored {} agents", agentScores.size());
      } else {
        // Fallback to individual scoring
        agentScores = new HashMap<>();
        for (AgentWorker worker : agents.values()) {
          agentScores.put(worker.getAgent().getAgentType(), agentScore(worker.getAgent()));
        }
        log.debug("Individual scored {} agents", agentScores.size());
      }

      // Batch add agents to Redis in chunks to avoid memory issues
      int configuredBatchSize = schedulerProperties.getBatchOperations().getBatchSize();
      // If batchSize is 0 (unbounded), use total agent count to process all in one batch
      int effectiveBatchSize = configuredBatchSize > 0 ? configuredBatchSize : agentScores.size();
      // Ensure we have at least 1 to avoid division by zero in logging
      int safeBatchSize = Math.max(1, effectiveBatchSize);
      int processed = 0;
      int totalAdded = 0;

      List<String> batchArgs = new ArrayList<>();
      for (Map.Entry<String, String> entry : agentScores.entrySet()) {
        String agentType = entry.getKey();
        String deadlineScoreString = entry.getValue();
        boolean scoreNumeric = deadlineScoreString != null && deadlineScoreString.matches("^\\d+$");
        boolean agentNumeric = agentType != null && agentType.matches("^\\d+$");
        if (!scoreNumeric || agentNumeric) {
          metrics.incrementInvalidPair("repopulate_fallback_batch");
          continue;
        }
        batchArgs.add(agentType); // agent name
        batchArgs.add(deadlineScoreString); // score
        processed++;

        // Process batch when we reach batch size or end of agents
        // Use effectiveBatchSize for the condition (handles unbounded case correctly)
        if (batchArgs.size() >= effectiveBatchSize * 2 || processed == agentScores.size()) {
          try {
            @SuppressWarnings("unchecked")
            List<Object> result =
                (List<Object>)
                    scriptManager.evalshaWithSelfHeal(
                        jedis,
                        RedisScriptManager.ADD_AGENTS,
                        Arrays.asList(WORKING_SET, WAITING_SET),
                        batchArgs);

            if (result.size() >= 1) {
              totalAdded += ((Long) result.get(0)).intValue();
            }

            // Use safeBatchSize for division to avoid ArithmeticException
            log.debug(
                "Batch added {} agents to Redis (batch {} of {})",
                batchArgs.size() / 2,
                (processed + safeBatchSize - 1) / safeBatchSize,
                (totalAgents + safeBatchSize - 1) / safeBatchSize);

          } catch (Exception e) {
            log.warn(
                "Batch repopulation failed for {} agents, using individual mode: {}",
                batchArgs.size() / 2,
                e);

            // Fallback: Use pipeline with individual addAgent script
            Pipeline pipeline = jedis.pipelined();
            for (int argIndex = 0; argIndex < batchArgs.size(); argIndex += 2) {
              pipeline.evalsha(
                  scriptManager.getScriptSha(RedisScriptManager.ADD_AGENT),
                  Arrays.asList(WORKING_SET, WAITING_SET),
                  Arrays.asList(batchArgs.get(argIndex), batchArgs.get(argIndex + 1)));
            }
            List<Object> pipelineResults = pipeline.syncAndReturnAll();

            // Count successful additions (addAgent returns 1 for success, 0 for already exists)
            for (Object result : pipelineResults) {
              if (result != null && ((Long) result).intValue() == 1) {
                totalAdded++;
              }
            }
          }

          batchArgs.clear();
        }
      }

      log.debug(
          "Repopulated Redis with {} agents ({} actually added/updated)", totalAgents, totalAdded);
      if (totalAdded > 0) {
        metrics.incrementRepopulateAdded(totalAdded);
      }

    } catch (Exception e) {
      log.error("Batch repopulation failed completely, falling back to individual operations", e);

      // Complete fallback to pipeline with individual addAgent scripts
      Pipeline pipeline = jedis.pipelined();
      for (AgentWorker worker : agents.values()) {
        String agentType = worker.getAgent().getAgentType();
        String nextScore = agentScore(worker.getAgent());

        // Use individual addAgent script for reliability
        pipeline.evalsha(
            scriptManager.getScriptSha(RedisScriptManager.ADD_AGENT),
            Arrays.asList(WORKING_SET, WAITING_SET),
            Arrays.asList(agentType, nextScore));
      }
      pipeline.sync(); // Execute all operations in a single network round trip
      log.debug("Pipeline fallback repopulated Redis with {} agents", totalAgents);
    }
  }

  /**
   * Process queued agent completions for metrics and observability only.
   *
   * <p>Redis state transitions (working→waiting) are now handled atomically by the worker thread in
   * conditionalReleaseAgent(). This method only drains the completion queue for metrics tracking
   * and observability - no Redis writes occur here.
   *
   * <p>The completion queue is retained for:
   *
   * <ul>
   *   <li>Metrics: count of completions per cycle
   *   <li>Observability: queue depth monitoring
   *   <li>Debugging: completion details logging
   * </ul>
   */
  private void processQueuedCompletions() {
    List<AgentCompletion> completions = drainCompletionQueue();
    if (completions.isEmpty()) {
      return;
    }

    // Metrics only - Redis work already done by atomicRescheduleInRedis()
    int successCount = 0;
    int failureCount = 0;
    for (AgentCompletion completion : completions) {
      if (completion.success) {
        successCount++;
      } else {
        failureCount++;
      }
    }

    log.debug(
        "Drained {} completions for metrics (success={}, failures={}) - Redis handled by workers",
        completions.size(),
        successCount,
        failureCount);

    // Drop references to completion payloads early to allow GC
    completions.clear();
  }

  /**
   * Process agents that failed Redis scheduling and were queued for recovery. This method is called
   * from saturatePool to retry scheduling agents that previously failed due to transient Redis
   * errors.
   *
   * <p>Agents are retried with their original offset. If an agent exceeds MAX_RECOVERY_ATTEMPTS, it
   * is logged as permanently failed and dropped (will be recovered by periodic repopulation).
   *
   * @param jedis Redis connection for scheduling operations
   */
  private void processRecoveryQueue(Jedis jedis) {
    if (scheduleRecoveryQueue.isEmpty()) {
      return;
    }

    int queueSize = scheduleRecoveryQueue.size();
    log.debug("Processing {} agents from schedule recovery queue", queueSize);

    int recovered = 0;
    int failed = 0;
    int dropped = 0;

    AgentRecovery recovery;
    while ((recovery = scheduleRecoveryQueue.poll()) != null) {
      String agentType = recovery.agent.getAgentType();

      // attemptCount is the number of completed recovery attempts; drop once it reaches the max
      if (recovery.attemptCount >= MAX_RECOVERY_ATTEMPTS) {
        // Exceeded max recovery attempts - drop agent (will be recovered by repopulation)
        log.error(
            "Agent {} permanently failed after {} recovery attempts - dropping (will recover via repopulation)",
            agentType,
            recovery.attemptCount);
        metrics.incrementScheduleRecovery(false);
        dropped++;
        continue;
      }

      try {
        String nextScore = score(jedis, recovery.offsetMs);

        Object result =
            scriptManager.evalshaWithSelfHeal(
                jedis,
                RedisScriptManager.ADD_AGENT,
                java.util.Arrays.asList(WORKING_SET, WAITING_SET),
                java.util.Arrays.asList(agentType, nextScore));

        // ADD_AGENT returns 1 if agent was added, 0 if already present in either set.
        // Either outcome means the agent is now in Redis - treat both as success.
        boolean scheduled = result != null && ((Long) result).intValue() == 1;
        if (scheduled) {
          log.debug(
              "Successfully recovered agent {} on attempt {}",
              agentType,
              recovery.attemptCount + 1);
        } else {
          // Agent already exists in Redis (likely re-added by repopulation) - treat as success
          log.debug("Agent {} already scheduled during recovery (result={})", agentType, result);
        }

        metrics.incrementScheduleRecovery(true);
        recovered++;
      } catch (Exception e) {
        log.warn(
            "Recovery attempt {} failed for agent {}: {}",
            recovery.attemptCount + 1,
            agentType,
            e.getMessage());

        // Re-queue with incremented attempt count
        scheduleRecoveryQueue.offer(
            new AgentRecovery(recovery.agent, recovery.offsetMs, recovery.attemptCount + 1));
        metrics.incrementScheduleRecovery(false);
        failed++;
      }
    }

    // Only log at info level if there's something noteworthy (failures or drops)
    if (dropped > 0 || failed > 0) {
      log.info(
          "Recovery queue processed: recovered={}, failed={}, dropped={}",
          recovered,
          failed,
          dropped);
    } else if (recovered > 0) {
      log.debug(
          "Recovery queue processed: recovered={}, failed={}, dropped={}",
          recovered,
          failed,
          dropped);
    }
  }

  /**
   * Drain all pending agent completions from the queue for processing.
   *
   * <p>Thread-safety: Only called from single-threaded scheduler executor, but queue accepts offers
   * from agent work pool threads.
   *
   * @return List of all pending completions, or empty list if none.
   */
  private List<AgentCompletion> drainCompletionQueue() {
    java.util.List<AgentCompletion> completions = REUSABLE_COMPLETIONS.get();
    completions.clear();
    int queueSize = completionQueue.size();
    log.debug("Draining completion queue, current size: {}", queueSize);

    AgentCompletion completion;
    while ((completion = completionQueue.poll()) != null) {
      log.debug(
          "Drained completion for agent: {} (success={})",
          completion.agent.getAgentType(),
          completion.success);
      completions.add(completion);
    }

    log.debug("Drained {} completions from queue", completions.size());
    return completions;
  }

  /**
   * Compute exponential backoff for throttled failures.
   *
   * @param backoffCfg throttled policy (base, multiplier, cap)
   * @param streak current failure streak (1-based)
   * @return backoff in milliseconds (capped)
   */
  private long computeExponentialBackoffMs(FailureBackoffProperties backoffCfg, int streak) {
    long base = backoffCfg.getThrottled().getBaseMs();
    double multiplier = backoffCfg.getThrottled().getMultiplier();
    long cap = backoffCfg.getThrottled().getCapMs();
    double factor = Math.pow(multiplier, Math.max(0, streak - 1));
    long raw = (long) Math.round(base * factor);
    return Math.min(raw, cap);
  }

  /**
   * Apply symmetric jitter in the range [-ratio, +ratio] to a positive base delay.
   *
   * @param baseMs base delay in milliseconds
   * @param jitterRatio ratio in [0.0, 1.0]
   * @return jittered delay (>= 0), coerced to at least 1ms if base > 0
   */
  private long applyJitter(long baseMs, double jitterRatio) {
    if (jitterRatio <= 0.0) {
      return baseMs;
    }
    double boundedRatio = Math.max(0.0, Math.min(1.0, jitterRatio));
    java.util.concurrent.ThreadLocalRandom random =
        java.util.concurrent.ThreadLocalRandom.current();
    double delta = (random.nextDouble() * 2.0 * boundedRatio) - boundedRatio; // [-ratio, +ratio]
    double jittered = baseMs * (1.0 + delta);
    if (jittered < 0.0) {
      return 0L;
    }
    long result = (long) Math.round(jittered);
    return result == 0L ? 1L : result;
  }

  /**
   * Validate that an agent/score pair is safe to send to Redis scripts. Increments invalid-pair
   * metrics with the given context on failure.
   */
  private boolean validateAgentScorePair(String agentType, String score, String context) {
    boolean scoreNumeric = score != null && score.matches("^\\d+$");
    boolean agentNumeric = agentType != null && agentType.matches("^\\d+$");
    if (!scoreNumeric || agentNumeric) {
      metrics.incrementInvalidPair(context);
      return false;
    }
    return true;
  }

  /** Parse the count returned by ADD_AGENTS script which typically returns [count, ...]. */
  private int parseAddAgentsCount(Object result) {
    try {
      if (result instanceof java.util.List) {
        java.util.List<?> list = (java.util.List<?>) result;
        if (!list.isEmpty()) {
          Object c0 = list.get(0);
          if (c0 instanceof Number) {
            return ((Number) c0).intValue();
          } else if (c0 instanceof String) {
            try {
              return Integer.parseInt((String) c0);
            } catch (Exception ignore) {
            }
          } else if (c0 instanceof byte[]) {
            try {
              return Integer.parseInt(
                  new String((byte[]) c0, java.nio.charset.StandardCharsets.UTF_8));
            } catch (Exception ignore) {
            }
          }
        }
      }
    } catch (Exception ignore) {
    }
    return 0;
  }

  /**
   * Attempt to acquire an agent for execution.
   *
   * @param jedis Jedis connection to Redis
   * @param agent The agent to acquire
   * @return The acquire score if successful, null otherwise
   */
  private String tryAcquireAgent(Jedis jedis, Agent agent, Long nowMsCached) {
    try {
      String agentType = agent.getAgentType();
      // Generate completion deadline: current_time + agent_timeout
      long agentTimeout = intervalProvider.getInterval(agent).getTimeout();
      String deadlineScore = score(jedis, agentTimeout, nowMsCached);

      // Atomically try to move agent from waiting -> working using Lua script
      // Script ensures only one instance can successfully acquire each agent
      // Args: [WORKING_SET, WAITING_SET, agentType, deadlineScore]
      Object result =
          scriptManager.evalshaWithSelfHeal(
              jedis,
              RedisScriptManager.MOVE_AGENTS,
              Arrays.asList(WORKING_SET, WAITING_SET), // Redis keys
              Arrays.asList(agentType, deadlineScore)); // Agent name and completion deadline

      // moveAgents script returns the score on success, nil on failure
      if (result != null) {
        // Handle different return types from Redis/Jedis
        String scoreString;
        if (result instanceof String) {
          scoreString = (String) result;
        } else if (result instanceof Long) {
          scoreString = String.valueOf(result);
        } else if (result instanceof byte[]) {
          scoreString = new String((byte[]) result, java.nio.charset.StandardCharsets.UTF_8);
        } else {
          log.warn(
              "Unexpected return type from MOVE_AGENTS for agent {}: {}",
              agentType,
              result.getClass().getName());
          metrics.incrementAcquireValidationFailure("unexpected_type");
          return null;
        }

        // Validate that the score is numeric (should be Unix timestamp in seconds)
        // This guards against Redis type coercion surprises or external mutations
        if (scoreString == null || scoreString.isEmpty()) {
          log.warn("Empty acquire score from MOVE_AGENTS for agent {}", agentType);
          metrics.incrementAcquireValidationFailure("empty_score");
          return null;
        }

        boolean numeric = true;
        for (int charIndex = 0; charIndex < scoreString.length(); charIndex++) {
          char ch = scoreString.charAt(charIndex);
          if (ch < '0' || ch > '9') {
            numeric = false;
            break;
          }
        }

        if (!numeric) {
          log.warn(
              "Non-numeric acquire score from MOVE_AGENTS for agent {}: '{}' (type={})",
              agentType,
              scoreString,
              result.getClass().getSimpleName());
          metrics.incrementAcquireValidationFailure("non_numeric_score");
          return null;
        }

        return scoreString; // Return the validated acquire score
      }
      return null; // Agent was acquired by another instance
    } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
      log.warn("Redis connection error while acquiring {}", agent.getAgentType(), e);
      return null;
    } catch (Exception e) {
      log.warn("Failed to acquire agent {}", agent.getAgentType(), e);
      return null;
    }
  }

  /**
   * Get the current score of an agent in the working or waiting set.
   *
   * <p>Behavior:
   *
   * <ul>
   *   <li>Working agents: Calculate NEXT execution time (current + interval)
   *   <li>Waiting agents: Keep existing score (regardless of overdue status)
   *   <li>New agents (not in Redis): Execute immediately (score = 0)
   * </ul>
   *
   * @param agent The agent to check
   * @return The current score of the agent, or "unknown" if Redis is unavailable
   */
  private String agentScore(Agent agent) {
    try (Jedis jedis = jedisPool.getResource()) {
      Pipeline pipeline = jedis.pipelined();

      // Queue both score lookups in a single pipeline
      Response<Double> workingScore = pipeline.zscore(WORKING_SET, agent.getAgentType());
      Response<Double> waitingScore = pipeline.zscore(WAITING_SET, agent.getAgentType());

      pipeline.sync();

      // If agent is currently working, calculate next execution from now
      if (workingScore.get() != null) {
        String result = score(jedis, intervalProvider.getInterval(agent).getInterval());
        log.debug("Agent {} working - next execution scheduled: {}", agent.getAgentType(), result);
        return result;
      }

      // If agent is waiting, keep existing score regardless of overdue status
      // Overdue agents will be naturally picked up by saturatePool() since their score <=
      // currentTime
      if (waitingScore.get() != null) {
        // All Redis scores are stored as seconds since epoch for consistent priority scheduling
        long waitingTimeSeconds = waitingScore.get().longValue();
        log.debug(
            "Agent {} waiting - keeping existing score: {} (preserves priority ordering)",
            agent.getAgentType(),
            waitingTimeSeconds);
        return String.valueOf(waitingTimeSeconds);
      }

      // Only NEW agents (not in Redis) get immediate execution priority
      String result = score(jedis, 0L, null);
      log.debug(
          "Agent {} is new - giving immediate execution priority: {}",
          agent.getAgentType(),
          result);
      return result;
    } catch (Exception e) {
      log.debug("Could not get agent score from Redis for {}", agent.getAgentType(), e);
      return "unknown";
    }
  }

  /**
   * Batch version of agentScore() that processes multiple agents in a single Redis call.
   *
   * @param jedis Redis connection to use
   * @param agents Collection of agents to score
   * @return Map of agent type to calculated score
   */
  private Map<String, String> batchAgentScore(Jedis jedis, Collection<AgentWorker> agents) {
    if (agents.isEmpty()) {
      return new HashMap<>();
    }

    try {
      // Prepare agent names for batch lookup
      List<String> agentNames =
          agents.stream()
              .map(worker -> worker.getAgent().getAgentType())
              .collect(Collectors.toList());

      log.debug("Batch scoring {} agents", agentNames.size());

      // Single Redis call to get all agent scores
      @SuppressWarnings("unchecked")
      List<String> results =
          (List<String>)
              scriptManager.evalshaWithSelfHeal(
                  jedis,
                  RedisScriptManager.SCORE_AGENTS,
                  Arrays.asList(WORKING_SET, WAITING_SET),
                  agentNames);

      // Process results: [agent1, workScore1, waitScore1, agent2, workScore2, waitScore2, ...]
      Map<String, String> agentScores = new HashMap<>();
      Map<String, Agent> agentMap =
          agents.stream()
              .collect(
                  Collectors.toMap(
                      worker -> worker.getAgent().getAgentType(), AgentWorker::getAgent));

      for (int resultIndex = 0; resultIndex < results.size(); resultIndex += 3) {
        String agentType = results.get(resultIndex);
        String workingScoreString = results.get(resultIndex + 1);
        String waitingScoreString = results.get(resultIndex + 2);

        Agent agent = agentMap.get(agentType);
        if (agent == null) {
          log.warn("Agent {} not found in batch scoring map", agentType);
          continue;
        }

        String calculatedScore =
            calculateAgentScore(jedis, agent, workingScoreString, waitingScoreString);
        agentScores.put(agentType, calculatedScore);
      }

      log.debug("Batch scored {} agents successfully", agentScores.size());
      return agentScores;

    } catch (Exception e) {
      log.warn("Batch agent scoring failed, falling back to individual scoring", e);
      // Fallback to individual scoring
      Map<String, String> scores = new HashMap<>();
      for (AgentWorker worker : agents) {
        scores.put(worker.getAgent().getAgentType(), agentScore(worker.getAgent()));
      }
      return scores;
    }
  }

  /**
   * Calculate the score for an agent based on its current Redis state. This exactly matches the
   * logic from agentScore() to ensure consistent behavior.
   *
   * <p>The behavior is:
   *
   * <ul>
   *   <li>Working agents: Calculate NEXT execution time (current + interval)
   *   <li>Waiting agents: Keep existing score (regardless of overdue status)
   *   <li>New agents (not in Redis): Execute immediately (score = 0)
   * </ul>
   */
  private String calculateAgentScore(
      Jedis jedis, Agent agent, String workingScoreString, String waitingScoreString) {
    try {
      // If agent is currently working, calculate next execution time (current + interval)
      // This matches original agentScore() behavior for working agents
      if (!"null".equals(workingScoreString)) {
        String result = score(jedis, intervalProvider.getInterval(agent).getInterval());
        log.debug("Agent {} working - next execution scheduled: {}", agent.getAgentType(), result);
        return result;
      }

      // If agent is waiting, keep existing score regardless of overdue status
      // Overdue agents will be naturally picked up by saturatePool() since their score <=
      // currentTime
      if (!"null".equals(waitingScoreString)) {
        try {
          long waitingTimeSeconds = Long.parseLong(waitingScoreString);
          log.debug(
              "Agent {} waiting - keeping existing score: {} (preserves priority ordering)",
              agent.getAgentType(),
              waitingScoreString);
          return String.valueOf(waitingTimeSeconds);
        } catch (NumberFormatException e) {
          log.debug(
              "Invalid waiting score for agent {}: {}", agent.getAgentType(), waitingScoreString);
        }
      }
    } catch (Exception e) {
      log.error("Error calculating score for agent {}", agent.getAgentType(), e);
      // Fall through to default case
    }

    try {
      // Only NEW agents (not in Redis) get immediate execution priority
      String result = score(jedis, 0L, null);
      log.debug("Agent {} new - immediate execution: {}", agent.getAgentType(), result);
      return result;
    } catch (Exception e) {
      log.error(
          "Error generating immediate execution score for agent {}: {}", agent.getAgentType(), e);
      // Return immediate execution score as fallback - this matches agentScore() behavior
      // where Redis failures still allow the agent to be scheduled
      return String.valueOf(System.currentTimeMillis() / 1000);
    }
  }

  /**
   * Generate a Redis score timestamp in seconds with server-client time synchronization.
   *
   * <p>Redis scores must be consistent format for priority scheduling to work correctly. We use
   * seconds (not milliseconds) to match Redis TIME command format and ensure consistent scoring
   * across all agents and instances.
   *
   * <p>This method performs periodic Redis TIME synchronization to handle clock skew between
   * multiple clouddriver instances and the Redis server.
   *
   * @param jedis Redis connection for TIME command synchronization
   * @param offset Offset in milliseconds to add to current time
   * @return Score as seconds since epoch, synchronized with Redis server time
   */
  private String score(Jedis jedis, Long offsetMs) {
    // Maintain and refresh the server-client offset cache when needed
    long now = System.currentTimeMillis();
    long lastCheck = lastTimeCheck.get();
    long timeCacheDurationMs = schedulerProperties.getTimeCacheDurationMs();
    if (now - lastCheck > timeCacheDurationMs) {
      try {
        List<String> times = jedis.time();
        if (times != null && times.size() == 2) {
          long serverTimeSeconds = Long.parseLong(times.get(0));
          long serverTimeMicros = Long.parseLong(times.get(1));
          long serverTimeMs = (serverTimeSeconds * 1000L) + (serverTimeMicros / 1000L);
          serverClientOffset.set(serverTimeMs - now);
          lastTimeCheck.set(now);
          log.debug("Updated Redis TIME sync offset: {}ms", serverTimeMs - now);
        }
      } catch (Exception e) {
        log.warn("Failed to get Redis server time, using client time", e);
      }
    }

    java.util.function.LongSupplier supplier = this::nowMsWithOffset;
    return com.netflix.spinnaker.cats.redis.cluster.support.RedisTimeUtils.scoreFromMsDelay(
        jedis, offsetMs != null ? offsetMs : 0L, supplier);
  }

  // Overload that prefers a per-cycle cached Redis TIME (ms) if provided; else falls back to offset
  // cache
  private String score(Jedis jedis, Long offsetMs, Long nowMsCached) {
    java.util.function.LongSupplier supplier =
        (nowMsCached != null && nowMsCached > 0) ? () -> nowMsCached : this::nowMsWithOffset;
    return com.netflix.spinnaker.cats.redis.cluster.support.RedisTimeUtils.scoreFromMsDelay(
        jedis, offsetMs != null ? offsetMs : 0L, supplier);
  }

  /**
   * Queue or immediately schedule an agent after execution completes.
   *
   * <p>Successes preserve cadence for the next run when possible. Failures are queued with failure
   * metadata that will be used to compute a class-based backoff offset before re-scheduling the
   * agent into waiting.
   *
   * @param agent the agent that finished
   * @param deadlineScore the acquire deadline score (working) captured at acquisition time
   * @param success whether execution completed successfully
   * @param failureClass coarse classification for failures (ignored on success)
   * @param cause the original failure (optional; used for logging)
   */
  public void conditionalReleaseAgent(
      Agent agent,
      String deadlineScore,
      boolean success,
      FailureClass failureClass,
      Throwable cause) {
    String agentType = agent.getAgentType();
    String throwableClassName = cause != null ? cause.getClass().getName() : null;

    try {
      // Log OOM diagnostics if applicable
      if (!success && cause instanceof java.lang.OutOfMemoryError) {
        String msg = String.valueOf(cause.getMessage());
        String oomType = "unknown";
        if (msg != null) {
          String lower = msg.toLowerCase(java.util.Locale.ROOT);
          if (lower.contains("heap") || lower.contains("gc overhead")) {
            oomType = "heap";
          } else if (lower.contains("direct buffer")) {
            oomType = "direct";
          } else if (lower.contains("metaspace")) {
            oomType = "metaspace";
          } else if (lower.contains("unable to create new native thread")) {
            oomType = "native-thread";
          }
        }
        log.warn(
            "Agent {} encountered OutOfMemoryError (type={}) — applying throttled backoff",
            agentType,
            oomType);
      }

      // Compute scheduling offset (handles success/failure, backoff, streaks)
      long offsetMs;
      if (shuttingDown.get()) {
        offsetMs = computeShutdownRescheduleOffsetMs(agent, deadlineScore);
        log.debug(
            "Shutdown re-queue agent {} with offset {} ms (deadline_score={})",
            agentType,
            offsetMs,
            deadlineScore);
      } else {
        offsetMs =
            computeRescheduleOffset(
                agent, deadlineScore, success, failureClass, throwableClassName);
      }

      // ATOMIC RESCHEDULE: Move from working to waiting in a single Redis operation.
      // This eliminates the race condition between completion queue processing and
      // worker cleanup that previously caused agent loss.
      atomicRescheduleInRedis(agent, Math.max(0L, offsetMs));

      // Queue completion for metrics/observability only (no longer used for Redis state)
      if (!success) {
        completionQueue.offer(
            new AgentCompletion(
                agent,
                deadlineScore,
                false,
                failureClass != null ? failureClass : FailureClass.UNKNOWN,
                throwableClassName));
      } else {
        completionQueue.offer(new AgentCompletion(agent, deadlineScore, true));
      }

      log.debug(
          "Atomically rescheduled agent {}: success={}, offset_ms={}, failure_class={}",
          agentType,
          success,
          offsetMs,
          failureClass);

    } catch (redis.clients.jedis.exceptions.JedisConnectionException e) {
      log.warn("Redis connection error during atomic reschedule for {}", agentType, e);
      // Agent will be recovered by zombie/orphan cleanup
    } catch (Exception e) {
      log.error(
          "Failed atomic reschedule for agent {} - will rely on cleanup services for recovery",
          agentType,
          e);
    }
  }

  /**
   * Compute reschedule offset for a completed agent run. This method is called directly from the
   * worker thread during completion to enable atomic rescheduling.
   *
   * <p>Success: attempts to preserve cadence relative to the original acquire time; falls back to
   * scheduling after the agent's interval.
   *
   * <p>Failure: applies class-based backoff when enabled; otherwise uses the agent's {@code
   * errorInterval}. Updates failure streaks atomically.
   *
   * @param agent the agent that completed
   * @param deadlineScore the working set score (acquire time + timeout) from acquisition
   * @param success whether the execution succeeded
   * @param failureClass failure classification (ignored on success)
   * @param throwableClassName original exception class name for logging (may be null)
   * @return offset in milliseconds from now for next scheduling
   */
  private long computeRescheduleOffset(
      Agent agent,
      String deadlineScore,
      boolean success,
      FailureClass failureClass,
      String throwableClassName) {

    String agentType = agent.getAgentType();

    if (success) {
      // Reset failure streak on success
      failureStreaks.remove(agentType);

      // Compute next schedule based on original acquire score when possible to preserve cadence
      try {
        AgentIntervalProvider.Interval interval = intervalProvider.getInterval(agent);
        long intervalMs = interval.getInterval();

        if (deadlineScore != null) {
          try {
            long deadlineScoreSeconds = Long.parseLong(deadlineScore);
            long agentTimeoutMs = interval.getTimeout();
            long originalAcquireMs = (deadlineScoreSeconds * 1000L) - agentTimeoutMs;
            long desiredNextRunMs = originalAcquireMs + intervalMs;
            long nowMs = System.currentTimeMillis() + serverClientOffset.get();
            long offsetMs = desiredNextRunMs - nowMs;
            return Math.max(offsetMs, 0L);
          } catch (NumberFormatException ignored) {
            // Fall through to simple interval scheduling
          }
        }
        return intervalMs;
      } catch (Exception e) {
        log.warn(
            "Failed to calculate success offset for agent {}, using default interval",
            agentType,
            e);
        try {
          return intervalProvider.getInterval(agent).getInterval();
        } catch (Exception ignored) {
          return 0L;
        }
      }
    }

    // Failure path: compute backoff with streak tracking
    FailureClass fclass = failureClass != null ? failureClass : FailureClass.UNKNOWN;
    int streak = failureStreaks.merge(agentType, 1, Integer::sum);
    FailureBackoffProperties backoffCfg = schedulerProperties.getFailureBackoff();

    long offsetMs = 0L;
    try {
      AgentIntervalProvider.Interval interval = intervalProvider.getInterval(agent);

      if (!backoffCfg.isEnabled()) {
        offsetMs = interval.getErrorInterval();
      } else {
        switch (fclass) {
          case PERMANENT_FORBIDDEN:
            offsetMs = backoffCfg.getPermanentForbiddenBackoffMs();
            break;
          case THROTTLED:
            offsetMs = computeExponentialBackoffMs(backoffCfg, streak);
            break;
          case SERVER_ERROR:
          case TRANSIENT:
            if (streak <= backoffCfg.getMaxImmediateRetries()) {
              offsetMs = 0L;
            } else {
              offsetMs = interval.getErrorInterval();
            }
            break;
          case UNKNOWN:
          default:
            offsetMs = interval.getErrorInterval();
        }
      }

      // Apply jitter if configured and offset > 0
      if (offsetMs > 0L) {
        offsetMs = applyJitter(offsetMs, schedulerProperties.getJitter().getFailureBackoffRatio());
        // Enforce whole-second scheduling to avoid undershooting by truncation
        offsetMs = ((offsetMs + 999L) / 1000L) * 1000L;
      }
    } catch (Exception e) {
      log.warn(
          "Failed to compute failure backoff for agent {} (class: {}, streak: {}), defaulting to 0",
          agentType,
          fclass,
          streak,
          e);
      offsetMs = 0L;
    }

    if (throwableClassName != null) {
      log.warn(
          "Agent {} failed with {} -> applying backoff {} ms (class={}, streak={})",
          agentType,
          throwableClassName,
          offsetMs,
          fclass,
          streak);
    } else {
      log.warn(
          "Agent {} failed -> applying backoff {} ms (class={}, streak={})",
          agentType,
          offsetMs,
          fclass,
          streak);
    }

    return Math.max(0L, offsetMs);
  }

  /**
   * Compute shutdown requeue offset using acquire-based cadence when available; fallback to a small
   * whole-second jitter window from configuration.
   */
  private long computeShutdownRescheduleOffsetMs(Agent agent, String deadlineScore) {
    try {
      AgentIntervalProvider.Interval interval = intervalProvider.getInterval(agent);
      long intervalMs = interval.getInterval();
      if (deadlineScore != null) {
        try {
          long deadlineScoreSeconds = Long.parseLong(deadlineScore);
          long agentTimeoutMs = interval.getTimeout();
          long originalAcquireMs = (deadlineScoreSeconds * 1000L) - agentTimeoutMs;
          long desiredNextRunMs = originalAcquireMs + intervalMs;
          long nowMs = System.currentTimeMillis() + serverClientOffset.get();
          return Math.max(desiredNextRunMs - nowMs, 0L);
        } catch (NumberFormatException ignored) {
          // Fall through to jitter fallback
        }
      }
    } catch (Exception e) {
      // Ignore and use jitter fallback
    }

    int windowSec = 0;
    try {
      windowSec = Math.max(0, schedulerProperties.getJitter().getShutdownSeconds());
    } catch (Exception ignored) {
      windowSec = 0;
    }
    if (windowSec <= 0) {
      return 0L;
    }
    int seconds = java.util.concurrent.ThreadLocalRandom.current().nextInt(1, windowSec + 1);
    return seconds * 1000L;
  }

  /**
   * Schedule an agent in Redis with the specified offset.
   *
   * @param agent The agent to schedule
   * @param offsetMs Offset from current time in milliseconds
   */
  public void scheduleAgentInRedis(Agent agent, long offsetMs) {
    String agentType = agent.getAgentType();
    int retryCount = 0;
    int maxRetries = 3;

    while (retryCount < maxRetries) {
      try (Jedis jedis = jedisPool.getResource()) {
        String nextScore = score(jedis, offsetMs);

        log.debug(
            "Scheduling agent {} in Redis with score: {} (attempt {})",
            agentType,
            nextScore,
            retryCount + 1);

        // During shutdown, attempt to move agent from working to waiting if present.
        // If move fails (agent was concurrently removed), fall through to add operation.
        if (shuttingDown.get()) {
          Double workingScore = jedis.zscore(WORKING_SET, agentType);
          if (workingScore != null) {
            String expectedScore = String.valueOf(workingScore.longValue());
            log.debug(
                "Agent {} in working during shutdown, attempting conditional move", agentType);
            Object moveResult =
                scriptManager.evalshaWithSelfHeal(
                    jedis,
                    RedisScriptManager.MOVE_AGENTS_CONDITIONAL,
                    java.util.Arrays.asList(WORKING_SET, WAITING_SET),
                    java.util.Arrays.asList(agentType, expectedScore, nextScore));

            boolean moved = moveResult != null && "swapped".equals(moveResult);
            if (moved) {
              log.debug("Agent {} moved from working to waiting", agentType);
              return;
            } else {
              log.debug(
                  "Agent {} move failed (concurrent modification), falling back to add", agentType);
            }
          }
        }

        // Add agent to waiting set if not already present in either set
        Object result =
            scriptManager.evalshaWithSelfHeal(
                jedis,
                RedisScriptManager.ADD_AGENT,
                java.util.Arrays.asList(WORKING_SET, WAITING_SET),
                java.util.Arrays.asList(agentType, nextScore));

        boolean scheduled = result != null && ((Long) result).intValue() == 1;
        log.debug("Agent {} scheduled in Redis: {}, result: {}", agentType, scheduled, result);
        return; // Success - exit retry loop

      } catch (Exception e) {
        retryCount++;
        if (retryCount >= maxRetries) {
          log.error(
              "Failed to schedule agent {} in Redis after {} attempts", agentType, maxRetries, e);
          metrics.incrementScheduleRetryExhausted();

          // Queue for recovery on next scheduler cycle (unless shutting down)
          if (!shuttingDown.get()) {
            scheduleRecoveryQueue.offer(new AgentRecovery(agent, offsetMs));
            log.warn(
                "Agent {} queued for recovery on next scheduler cycle (recovery_queue_size={})",
                agentType,
                scheduleRecoveryQueue.size());
          }
        } else {
          log.warn(
              "Failed to schedule agent {} in Redis (attempt {}), retrying: {}",
              agentType,
              retryCount,
              e.getMessage());
          try {
            Thread.sleep(100 * retryCount); // Backoff for next attempt
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted during Redis retry backoff for agent {}", agentType);
            metrics.incrementScheduleRetryExhausted();
            // Still queue for recovery if interrupted
            if (!shuttingDown.get()) {
              scheduleRecoveryQueue.offer(new AgentRecovery(agent, offsetMs));
            }
            return;
          }
        }
      }
    }
  }

  /**
   * Atomically reschedule an agent from working set to waiting set. This is the core operation for
   * The worker thread completes the full Redis state transition, eliminating the race condition
   * between completion queue processing and worker cleanup.
   *
   * <p>This method uses the RESCHEDULE_AGENT script which handles all cases:
   *
   * <ul>
   *   <li>Agent in working set: remove from working, add to waiting with new score
   *   <li>Agent in neither set: add to waiting (defensive, handles concurrent cleanup)
   *   <li>Agent already in waiting: no-op (already rescheduled by another path)
   * </ul>
   *
   * @param agent the agent to reschedule
   * @param offsetMs offset from current time for the new waiting score
   */
  private void atomicRescheduleInRedis(Agent agent, long offsetMs) {
    String agentType = agent.getAgentType();

    try (Jedis jedis = jedisPool.getResource()) {
      String nextScore = score(jedis, offsetMs);

      if (!validateAgentScorePair(agentType, nextScore, "atomic_reschedule")) {
        log.warn("Invalid agent/score pair for atomic reschedule: agent={}", agentType);
        return;
      }

      Object result =
          scriptManager.evalshaWithSelfHeal(
              jedis,
              RedisScriptManager.RESCHEDULE_AGENT,
              java.util.Arrays.asList(WORKING_SET, WAITING_SET),
              java.util.Arrays.asList(agentType, nextScore));

      String resultStr = result != null ? result.toString() : "null";
      log.debug(
          "Atomic reschedule for agent {}: result={}, score={}", agentType, resultStr, nextScore);

      // Log based on result for observability
      if ("added".equals(resultStr)) {
        // Agent was already removed from working (concurrent cleanup) - log for visibility
        log.debug("Agent {} was not in working during reschedule (concurrent cleanup)", agentType);
      }
      // "moved" = normal completion, "exists" = already in waiting (no-op)

    } catch (Exception e) {
      log.error("Failed atomic reschedule for agent {}: {}", agentType, e.getMessage(), e);
      metrics.incrementAtomicRescheduleFailed();
      // Agent will be recovered by zombie/orphan cleanup - no recovery queue needed
      // since the agent is still in working set and will be detected as stuck
    }
  }

  /** Set the shutdown flag to coordinate graceful shutdown across all operations. */
  public void setShuttingDown(boolean shuttingDown) {
    this.shuttingDown.set(shuttingDown);
    log.info("AgentAcquisitionService shutdown flag set to: {}", shuttingDown);

    if (shuttingDown) {
      // Drain and process all queued completions during shutdown
      // to ensure we don't lose any agents
      processCompletionQueueForShutdown();
    }
  }

  /**
   * Drains and processes all queued completions during shutdown. Ensures pending agent completions
   * are properly recorded in Redis.
   */
  private void processCompletionQueueForShutdown() {
    int queueSize = completionQueue.size();
    if (queueSize == 0) {
      log.info("No queued completions to process during shutdown");
      return;
    }

    log.info("Processing {} queued agent completions during shutdown", queueSize);

    // Process all queued completions with immediate scheduling (0ms offset)
    List<AgentCompletion> completions = drainCompletionQueue();
    try (Jedis jedis = jedisPool.getResource()) {
      int processed = 0;

      // Process each completion with cadence-based or jittered offset to avoid restart bursts
      for (AgentCompletion completion : completions) {
        try {
          String agentType = completion.agent.getAgentType();
          long offsetMs =
              computeShutdownRescheduleOffsetMs(completion.agent, completion.deadlineScore);
          String score = score(jedis, offsetMs);

          Object result =
              scriptManager.evalshaWithSelfHeal(
                  jedis,
                  RedisScriptManager.ADD_AGENT,
                  Arrays.asList(WORKING_SET, WAITING_SET),
                  Arrays.asList(agentType, score));

          if (result != null && ((Long) result).intValue() == 1) {
            processed++;
            log.debug("Shutdown processed agent completion: {}", agentType);
          }
        } catch (Exception e) {
          log.error(
              "Failed to process agent completion during shutdown: {}",
              completion.agent.getAgentType(),
              e);
        }
      }

      log.info(
          "Successfully processed {}/{} agent completions during shutdown",
          processed,
          completions.size());
    } catch (Exception e) {
      log.error("Failed to process completion queue during shutdown", e);
    } finally {
      try {
        completions.clear();
        if (completions instanceof java.util.ArrayList) {
          ((java.util.ArrayList<?>) completions).trimToSize();
        }
      } catch (Exception ignore) {
        // Best-effort – trimming failure is harmless
      }
    }
  }

  /**
   * Checks if the service is in the process of shutting down. This flag affects agent completion
   * handling - during shutdown, agent completions are processed immediately rather than queued.
   *
   * @return true if shutdown is in progress, false otherwise
   */
  public boolean isShuttingDown() {
    return shuttingDown.get();
  }

  /**
   * Sets the graceful shutdown flag to coordinate agent re-queuing during shutdown. When enabled,
   * active agents are moved back to the waiting set with immediate execution scores to ensure they
   * run after service restart.
   *
   * @param gracefulShutdown true to enable graceful shutdown mode, false otherwise
   */
  public void setGracefulShutdown(boolean gracefulShutdown) {
    this.gracefulShutdown.set(gracefulShutdown);
    log.debug("AgentAcquisitionService graceful shutdown flag set to: {}", gracefulShutdown);
  }

  /**
   * Checks if graceful shutdown is in progress. This flag affects agent handling during shutdown -
   * graceful shutdown attempts to re-queue in-progress agents back to Redis for pickup after
   * restart.
   *
   * @return true if graceful shutdown is in progress, false otherwise
   */
  @VisibleForTesting
  boolean isGracefulShutdown() {
    return gracefulShutdown.get();
  }

  /**
   * Remove ThreadLocal buffers held by the current thread to release per-thread memory. Intended to
   * be invoked on the owning executor thread during shutdown.
   */
  public void removeThreadLocals() {
    try {
      REUSABLE_WORKERS_SET.remove();
    } catch (Exception ignore) {
    }
    try {
      REUSABLE_CANDIDATE_AGENTS.remove();
    } catch (Exception ignore) {
    }
    try {
      REUSABLE_CANDIDATE_WORKERS.remove();
    } catch (Exception ignore) {
    }
    try {
      REUSABLE_ELIGIBLE_AGENTS.remove();
    } catch (Exception ignore) {
    }
    try {
      REUSABLE_AGENT_SCORE_PAIRS.remove();
    } catch (Exception ignore) {
    }
    try {
      REUSABLE_COMPLETIONS.remove();
    } catch (Exception ignore) {
    }
  }

  /**
   * Submit an agent to the thread pool with proper rejection handling and permit management.
   *
   * @param worker The agent worker to submit
   * @param agentWorkPool The thread pool to submit to
   * @param maxConcurrentSemaphore The semaphore for concurrency control (may be null)
   * @return The Future for the submitted task, or null if submission failed
   */
  private java.util.concurrent.Future<?> submitAgentWithRejectionHandling(
      AgentWorker worker, ExecutorService agentWorkPool, Semaphore maxConcurrentSemaphore) {

    String agentType = worker.getAgent().getAgentType();

    try {
      // Design decision: Nested anonymous class (FutureTask with done() callback)
      // --------------------------------------------------------------------------
      // This pattern is required to guarantee exactly-once semaphore release even when tasks are
      // cancelled before run() starts (pre-start cancellation). Alternative approaches considered:
      // 1. CompletableFuture: Doesn't integrate cleanly with ExecutorService.submit()
      // 2. Separate completion callback: Would require additional synchronization
      // 3. ExecutorCompletionService: Adds complexity without solving pre-start cancellation
      // JVM optimizes anonymous class instantiation well (no reflection, direct bytecode). The
      // per-agent allocation is acceptable since agent execution itself is the expensive part.
      java.util.concurrent.FutureTask<Void> futureTask =
          new java.util.concurrent.FutureTask<Void>(worker, null) {
            @Override
            protected void done() {
              try {
                // Remove from tracking map when the task completes (best-effort)
                activeAgentsFutures.remove(agentType, this);

                // Exactly-once permit release fallback: if the worker's finally block did not
                // run (e.g., cancelled before start), release the permit here.
                RunState runStateForAgent = runStates.remove(agentType);
                if (runStateForAgent != null
                    && runStateForAgent.permitHeld.compareAndSet(true, false)) {
                  Semaphore semaphoreToRelease =
                      maxConcurrentSemaphore != null
                          ? maxConcurrentSemaphore
                          : maxConcurrentSemaphoreRef;
                  if (semaphoreToRelease != null) {
                    semaphoreToRelease.release();
                    log.debug(
                        "Released semaphore permit for agent {} in completion listener", agentType);
                  }
                }
              } catch (Exception e) {
                // Never propagate from listener
                log.debug("Completion listener failed for {}", agentType, e);
              }
            }
          };

      // Submit to pool; only track the future after a successful submit
      java.util.concurrent.Future<?> future = agentWorkPool.submit(futureTask);
      activeAgentsFutures.put(agentType, future);
      log.debug("Successfully submitted agent {} to thread pool", agentType);
      return future;

    } catch (java.util.concurrent.RejectedExecutionException rex) {
      // Handle rejection - release permit and track metric
      log.warn(
          "Agent {} submission rejected by thread pool (queue full or pool shutdown)", agentType);

      // Critical: Clean up RunState and release permit via CAS to maintain consistency.
      // RunState was created in the submit loop before calling this method.
      RunState runStateToClean = runStates.remove(agentType);
      if (runStateToClean != null && runStateToClean.permitHeld.compareAndSet(true, false)) {
        try {
          Semaphore semaphoreToRelease =
              maxConcurrentSemaphore != null ? maxConcurrentSemaphore : maxConcurrentSemaphoreRef;
          if (semaphoreToRelease != null) {
            semaphoreToRelease.release();
            log.debug("Released semaphore permit for rejected agent {}", agentType);
          }
        } catch (Exception ignore) {
        }
      }

      // Critical: Remove from activeAgents to prevent permit_mismatch.
      // activeAgents entry was added before submission attempt.
      String removed = activeAgents.remove(agentType);
      if (removed != null) {
        activeAgentMapSize.decrementAndGet();
        log.debug("Cleaned up activeAgents for rejected agent {}", agentType);
      }

      // Track rejection metric
      metrics.incrementSubmissionFailure("rejected");

      // Requeue the agent preserving its original readiness priority
      requeueRejectedAgent(worker);

      return null;

      // Design note: Catch Throwable to handle all submission failure modes.
      // - Critical: A permit was acquired before calling this method. If an Error occurs during
      //   FutureTask creation or executor.submit(), we must release the permit to prevent leaks.
      // - Examples: OutOfMemoryError creating FutureTask, ThreadDeath during submit, etc.
    } catch (Throwable e) {
      // Handle other submission errors
      log.error("Failed to submit agent {} due to unexpected error", agentType, e);

      // Critical: Clean up RunState and release permit via CAS to maintain consistency.
      // RunState was created in the submit loop before calling this method.
      RunState runStateToClean = runStates.remove(agentType);
      if (runStateToClean != null && runStateToClean.permitHeld.compareAndSet(true, false)) {
        try {
          Semaphore semaphoreToRelease =
              maxConcurrentSemaphore != null ? maxConcurrentSemaphore : maxConcurrentSemaphoreRef;
          if (semaphoreToRelease != null) {
            semaphoreToRelease.release();
            log.debug("Released semaphore permit for failed submission of agent {}", agentType);
          }
        } catch (Exception ignore) {
        }
      }

      // Critical: Remove from activeAgents to prevent permit_mismatch.
      // activeAgents entry was added before submission attempt.
      String removed = activeAgents.remove(agentType);
      if (removed != null) {
        activeAgentMapSize.decrementAndGet();
        log.debug("Cleaned up activeAgents for failed submission of agent {}", agentType);
      }

      // Track generic submission failure
      metrics.incrementSubmissionFailure(e.getClass().getSimpleName());

      // Requeue to avoid lingering working entries on submission errors
      requeueRejectedAgent(worker);

      return null;
    }
  }

  /**
   * Requeue an agent that was rejected due to thread pool saturation. This preserves the agent's
   * original priority in the queue to maintain fairness.
   *
   * @param worker The agent worker that was rejected
   */
  private void requeueRejectedAgent(AgentWorker worker) {
    String agentType = worker.getAgent().getAgentType();

    try (Jedis jedis = jedisPool.getResource()) {
      // Ensure we don't leak local run-state or active tracking on submission failure
      runStates.remove(agentType);
      String removedScore = activeAgents.remove(agentType);
      if (removedScore != null) {
        activeAgentMapSize.decrementAndGet();
        activeAgentsFutures.remove(agentType);
      }

      // Calculate the score to preserve queue position
      String requeueScore;

      if (worker.deadlineScore != null) {
        // We have the acquire score (deadline = acquisition_time + timeout)
        // Calculate when the agent was originally ready to maintain its position
        try {
          long deadlineScoreSeconds = Long.parseLong(worker.deadlineScore);
          long timeoutSeconds =
              intervalProvider.getInterval(worker.getAgent()).getTimeout() / 1000L;
          long originalReadySeconds = deadlineScoreSeconds - timeoutSeconds;

          // Preserve exact original ready time to maintain strict FIFO fairness
          requeueScore = String.valueOf(originalReadySeconds);

          log.debug(
              "Requeueing rejected agent {} with score {} (preserve original ready time)",
              agentType,
              requeueScore);
        } catch (Exception e) {
          // Fallback to immediate readiness if calculation fails
          log.warn(
              "Failed to calculate original ready time for agent {}, using immediate score",
              agentType,
              e);
          requeueScore = score(jedis, 0L, null);
        }
      } else {
        // No acquire score available, make it immediately eligible
        requeueScore = score(jedis, 0L, null);
        log.debug(
            "Requeueing rejected agent {} with score {} (immediate, no acquire score)",
            agentType,
            requeueScore);
      }

      // Prefer an atomic working -> waiting move with ownership verification
      boolean requeued = false;
      try {
        if (worker.deadlineScore != null) {
          Object swapResult =
              scriptManager.evalshaWithSelfHeal(
                  jedis,
                  RedisScriptManager.MOVE_AGENTS_CONDITIONAL,
                  java.util.Arrays.asList(WORKING_SET, WAITING_SET),
                  java.util.Arrays.asList(agentType, worker.deadlineScore, requeueScore));
          if (swapResult != null && "swapped".equals(swapResult)) {
            requeued = true;
            log.info(
                "Requeued rejected agent {} from working -> waiting (score preserved: {})",
                agentType,
                requeueScore);
          }
        }
      } catch (Exception e) {
        log.warn("Conditional move failed while requeueing rejected agent {}", agentType, e);
      }

      if (!requeued) {
        // Fallback: remove from working if still owned (score match), then add to waiting
        boolean removedFromWorking = false;
        try {
          if (worker.deadlineScore != null) {
            @SuppressWarnings("unchecked")
            java.util.List<Object> removeResult =
                (java.util.List<Object>)
                    scriptManager.evalshaWithSelfHeal(
                        jedis,
                        RedisScriptManager.REMOVE_AGENTS_CONDITIONAL,
                        java.util.Collections.singletonList(WORKING_SET),
                        java.util.Arrays.asList(agentType, worker.deadlineScore));
            int count =
                removeResult != null && removeResult.size() >= 1
                    ? ((Long) removeResult.get(0)).intValue()
                    : 0;
            removedFromWorking = count > 0;
          }
        } catch (Exception e) {
          log.warn("Conditional remove-from-working failed for {}", agentType, e);
        }

        if (removedFromWorking || worker.deadlineScore == null) {
          // Safe to add back to waiting only if we removed from working (or have no score)
          Object addResult =
              scriptManager.evalshaWithSelfHeal(
                  jedis,
                  RedisScriptManager.ADD_AGENT,
                  java.util.Arrays.asList(WORKING_SET, WAITING_SET),
                  java.util.Arrays.asList(agentType, requeueScore));
          boolean scheduled = addResult != null && ((Long) addResult).intValue() == 1;
          if (scheduled) {
            log.info(
                "Requeued rejected agent {} into waiting with score {}", agentType, requeueScore);
          } else {
            log.debug(
                "Agent {} already present during requeue attempt (add_result={})",
                agentType,
                addResult);
          }
        } else {
          // Could not verify ownership to safely move; leave as-is for zombie/orphan cleanup
          log.warn(
              "Could not safely requeue rejected agent {} (ownership mismatch) - will rely on cleanup",
              agentType);
        }
      }

    } catch (Exception e) {
      log.error(
          "Failed to requeue rejected agent {} - will be picked up in next repopulation",
          agentType,
          e);
    }
  }

  /** Runnable wrapper for agent execution that handles resource management and monitoring. */
  static class AgentWorker implements Runnable {
    private final Agent agent;
    private final AgentExecution agentExecution;
    private final ExecutionInstrumentation executionInstrumentation;
    private final AgentAcquisitionService acquisitionService;
    private Semaphore maxConcurrentSemaphore; // Semaphore to release when execution completes

    // Set by acquisition service when agent is acquired
    String deadlineScore;

    AgentWorker(
        Agent agent,
        AgentExecution agentExecution,
        ExecutionInstrumentation executionInstrumentation,
        AgentAcquisitionService acquisitionService) {
      this.agent = agent;
      this.agentExecution = agentExecution;
      this.executionInstrumentation = executionInstrumentation;
      this.acquisitionService = acquisitionService;
      this.maxConcurrentSemaphore = null; // Will be set before execution
    }

    @Override
    public void run() {
      String agentType = agent.getAgentType();
      long startTimeMs = System.currentTimeMillis();
      boolean success = false;
      FailureClass failureClass = null;
      Throwable capturedCause = null;

      try {
        // Mark as started for fairness accounting
        try {
          RunState runStateForAgent = acquisitionService.runStates.get(agentType);
          if (runStateForAgent != null) {
            runStateForAgent.started.set(true);
          }
        } catch (Exception e) {
          log.debug("Failed to mark run-state started for {}", agentType, e);
        }
        log.debug("Starting execution of agent {}", agentType);
        executionInstrumentation.executionStarted(agent);
        agentExecution.executeAgent(agent);
        executionInstrumentation.executionCompleted(agent, elapsedTimeMs(startTimeMs));
        success = true;
        acquisitionService.agentsExecuted.increment(); // Track successful executions
        log.debug("Agent {} execution completed successfully", agentType);

        // Design note: Catch Throwable (not just Exception) to handle all failure modes.
        // - Purpose: Ensure cleanup and requeueing occur even for Errors (OutOfMemoryError,
        //   StackOverflowError, etc.) that agents may encounter during cloud provider API calls
        //   or data processing.
        // - Critical: If we only caught Exception, Errors would bypass failure classification and
        //   proper requeueing, leaving orphaned entries in the working set that orphan cleanup
        //   would need to handle later.
        // - Policy: The finally block guarantees permit release and Redis cleanup regardless of
        //   failure type. This catch block ensures we properly classify the failure and requeue
        //   with appropriate backoff (e.g., OutOfMemoryError -> THROTTLED with exponential
        // backoff).
      } catch (Throwable cause) {
        if (cause instanceof InterruptedException) {
          log.warn(
              "Agent {} execution was interrupted (likely due to zombie cleanup or shutdown)",
              agentType);
          Thread.currentThread().interrupt(); // Restore interrupt status
        } else if (cause instanceof Error) {
          log.error(
              "Agent {} execution failed with Error after {}ms - this may indicate serious JVM issues",
              agentType,
              elapsedTimeMs(startTimeMs),
              cause);
        } else {
          log.error(
              "Agent {} execution failed after {}ms", agentType, elapsedTimeMs(startTimeMs), cause);
        }

        acquisitionService.agentsFailed.increment(); // Track failed executions
        executionInstrumentation.executionFailed(agent, cause, elapsedTimeMs(startTimeMs));
        capturedCause = cause;
        failureClass = acquisitionService.classifyFailure(cause);

        // Record per-agent failure metric for debugging agent-specific issues
        try {
          acquisitionService.metrics.incrementRunFailure(
              agentType, agent.getProviderName(), cause.getClass().getSimpleName());
        } catch (Exception metricEx) {
          log.debug("Failed to record per-agent failure metric for {}", agentType, metricEx);
        }
      } finally {
        // Cancel any scheduled dead-man action FIRST
        try {
          RunState runState = acquisitionService.runStates.get(agentType);
          if (runState != null && runState.deadmanHandle != null) {
            try {
              boolean cancelled = runState.deadmanHandle.cancel(false);
              if (!cancelled && log.isDebugEnabled()) {
                log.debug("Dead-man timer already fired for {}", agentType);
              }
            } catch (Exception cancelEx) {
              log.debug("Dead-man handle cancel failed for {}", agentType, cancelEx);
            }
            runState.deadmanHandle = null;
          }
        } catch (Exception ex) {
          log.debug("Dead-man cleanup failed for {}", agentType, ex);
        }

        // Queue completion BEFORE removing from tracking to avoid race with cleanup services
        acquisitionService.conditionalReleaseAgent(
            agent, deadlineScore, success, failureClass, capturedCause);

        // Critical: Exactly-once permit release (perform BEFORE Redis cleanup)
        RunState runStateForAgent = acquisitionService.runStates.remove(agentType);
        if (runStateForAgent == null) {
          // Unexpected: RunState should exist - may indicate double cleanup or unregister race
          if (maxConcurrentSemaphore != null) {
            maxConcurrentSemaphore.release();
            log.warn(
                "Released permit for {} with no RunState (unexpected - possible double cleanup)",
                agentType);
          }
        } else {
          // Release permit via CAS - ensures exactly-once release
          if (runStateForAgent.permitHeld.compareAndSet(true, false)) {
            if (maxConcurrentSemaphore != null) {
              maxConcurrentSemaphore.release();
              log.debug("Released permit for {}", agentType);
            }
          } else {
            // CAS failed - permit already released by another path (e.g.,
            // removeActiveAgentWithPermitRelease)
            // This is unexpected in normal flow but valid if cleanup raced with worker completion
            acquisitionService.metrics.incrementCasContention("worker_completion");
            log.warn(
                "Permit for {} already released (CAS failed) - cleanup raced with worker completion",
                agentType);
          }
        }

        // Now remove from active tracking and Redis
        acquisitionService.removeActiveAgent(agentType);

        log.debug("Agent {} execution cleanup completed", agentType);
      }
    }

    /**
     * Get the agent associated with this worker.
     *
     * @return The agent
     */
    public Agent getAgent() {
      return agent;
    }

    /**
     * Get the acquire score for this agent.
     *
     * @return The acquire score
     */
    public String getAcquireScore() {
      return deadlineScore;
    }

    /**
     * Set the semaphore before execution (called from saturatePool)
     *
     * @param maxConcurrentSemaphore The semaphore to use for resource management
     */
    void setMaxConcurrentSemaphore(Semaphore maxConcurrentSemaphore) {
      this.maxConcurrentSemaphore = maxConcurrentSemaphore;
    }
  }

  /**
   * Classify a failure throwable into a coarse-grained {@code FailureClass} without introducing
   * provider SDK dependencies.
   *
   * <p>Heuristics: - InterruptedException -> TRANSIENT (handled earlier by restoring interrupt) -
   * IO/connectivity/timeouts -> TRANSIENT - AWS AmazonServiceException (via reflection): 403 ->
   * PERMANENT_FORBIDDEN (AccessDenied or similar) 429 -> THROTTLED 5xx -> SERVER_ERROR - Messages
   * containing throttling hints -> THROTTLED - Otherwise -> UNKNOWN
   */
  FailureClass classifyFailure(Throwable cause) {
    if (cause == null) {
      return FailureClass.UNKNOWN;
    }

    if (cause instanceof InterruptedException) {
      return FailureClass.TRANSIENT;
    }

    // Treat memory pressure as throttling to exponentially back off agent executions
    if (cause instanceof java.lang.OutOfMemoryError) {
      return FailureClass.THROTTLED;
    }

    if (cause instanceof java.net.SocketTimeoutException
        || cause instanceof java.net.ConnectException
        || cause instanceof java.net.SocketException
        || cause instanceof java.io.IOException) {
      return FailureClass.TRANSIENT;
    }

    // Reflective checks to avoid hard dependencies on provider libraries
    try {
      Class<?> sceClass = Class.forName("com.amazonaws.SdkClientException");
      if (sceClass.isAssignableFrom(cause.getClass())) {
        return FailureClass.TRANSIENT;
      }
    } catch (ClassNotFoundException ignored) {
    }

    try {
      Class<?> aceClass = Class.forName("com.amazonaws.AmazonClientException");
      if (aceClass.isAssignableFrom(cause.getClass())) {
        return FailureClass.TRANSIENT;
      }
    } catch (ClassNotFoundException ignored) {
    }

    try {
      Class<?> jooqDae = Class.forName("org.jooq.exception.DataAccessException");
      if (jooqDae.isAssignableFrom(cause.getClass())) {
        return FailureClass.TRANSIENT;
      }
    } catch (ClassNotFoundException ignored) {
    }

    try {
      Class<?> aseClass = Class.forName("com.amazonaws.AmazonServiceException");
      if (aseClass.isAssignableFrom(cause.getClass())) {
        Integer status = null;
        String errorCode = null;
        try {
          java.lang.reflect.Method getStatusCode = aseClass.getMethod("getStatusCode");
          Object sc = getStatusCode.invoke(cause);
          if (sc instanceof Integer) {
            status = (Integer) sc;
          }
        } catch (Exception ignored) {
        }
        try {
          java.lang.reflect.Method getErrorCode = aseClass.getMethod("getErrorCode");
          Object ec = getErrorCode.invoke(cause);
          if (ec instanceof String) {
            errorCode = (String) ec;
          }
        } catch (Exception ignored) {
        }

        // Some throttling variants are communicated via errorCode even with 400s
        if (errorCode != null) {
          String codeLower = errorCode.toLowerCase(java.util.Locale.ROOT);
          if (codeLower.contains("throttl")
              || codeLower.contains("toomanyrequests")
              || codeLower.contains("requestlimitexceeded")
              || codeLower.contains("slowdown")
              || codeLower.contains("provisionedthroughputexceeded")
              || codeLower.contains("requestthrottled")) {
            return FailureClass.THROTTLED;
          }
        }

        if (status != null) {
          if (status == 403) {
            if (errorCode != null
                && errorCode.toLowerCase(java.util.Locale.ROOT).contains("accessdenied")) {
              return FailureClass.PERMANENT_FORBIDDEN;
            }
            return FailureClass.PERMANENT_FORBIDDEN;
          }
          if (status == 429) {
            return FailureClass.THROTTLED;
          }
          if (status >= 500 && status < 600) {
            return FailureClass.SERVER_ERROR;
          }
          if (status >= 400 && status < 500) {
            return FailureClass.UNKNOWN;
          }
        }
        return FailureClass.UNKNOWN;
      }
    } catch (ClassNotFoundException ignored) {
      // AWS SDK not present in this module
    }

    String msg = String.valueOf(cause.getMessage()).toLowerCase(java.util.Locale.ROOT);
    if (msg.contains("throttl")
        || msg.contains("rate exceeded")
        || msg.contains("too many requests")) {
      return FailureClass.THROTTLED;
    }

    return FailureClass.UNKNOWN;
  }

  /**
   * Try to manually acquire a lock on an agent.
   *
   * <p>Note: Manual locking is not supported by this scheduler to maintain thread safety and proper
   * coordination between multiple scheduler instances. Manual locking would bypass the carefully
   * designed Redis-based coordination mechanisms.
   *
   * @param agent The agent to lock
   * @return Always returns null (manual locking not supported)
   */
  public AgentLock tryLockAgent(Agent agent) {
    // Manual locking is not supported to maintain thread safety and proper coordination
    log.debug(
        "Manual locking not supported for agent {} - use automatic scheduling",
        agent.getAgentType());
    return null;
  }

  /**
   * Try to release a manually acquired agent lock.
   *
   * <p>Since manual locking is not supported, this always returns false.
   *
   * @param lock The lock to release
   * @return Always returns false (manual locking not supported)
   */
  public boolean tryReleaseAgent(AgentLock lock) {
    // Manual locking/releasing is not supported
    log.debug(
        "Manual lock release not supported for agent {} - locks are managed automatically",
        lock.getAgent().getAgentType());
    return false;
  }

  /**
   * Check if an agent lock is still valid.
   *
   * <p>Since manual locking is not supported, this always returns false.
   *
   * @param lock The lock to validate
   * @return Always returns false (manual locking not supported)
   */
  public boolean isLockValid(AgentLock lock) {
    // Manual locking is not supported, so manual locks are never valid
    log.debug(
        "Manual lock validation not supported for agent {} - locks are managed automatically",
        lock.getAgent().getAgentType());
    return false;
  }
}
