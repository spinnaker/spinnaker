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

import javax.annotation.PostConstruct;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for the Redis priority scheduler.
 *
 * <p>Externalized configuration via Spring Boot @ConfigurationProperties.
 */
@Component
@ConfigurationProperties(prefix = "redis.scheduler")
@Getter
@Setter
@Slf4j
public class PrioritySchedulerProperties {

  /** Circuit breaker configuration for protecting against cascading failures. */
  @Getter
  @Setter
  public static class CircuitBreaker {
    /** Enable circuit breaker protection. Default: true. */
    private boolean enabled = true;

    /** Number of failures required to trip the circuit. Default: 5. */
    private int failureThreshold = 5;

    /** Time window for counting failures (ms). Default: 10000. */
    private long failureWindowMs = 10000;

    /** Cooldown period after circuit trips (ms). Default: 30000. */
    private long cooldownMs = 30000;

    /** Half-open testing period duration (ms). Default: 5000. */
    private long halfOpenDurationMs = 5000;
  }

  // === Core scheduling properties ===

  /**
   * How often the scheduler runs to check for ready agents (milliseconds). Controls the frequency
   * of the main scheduling loop. Config key: {@code redis.scheduler.interval-ms}
   */
  private long intervalMs = 1000L;

  /**
   * How often to refresh the Redis agent list (seconds). This helps recover from Redis failures and
   * ensures consistency. Config key: {@code redis.scheduler.refresh-period-seconds}
   */
  private int refreshPeriodSeconds = 30;

  /**
   * Period between health summary logs (seconds). Default: 600 (10 minutes).
   *
   * <p>If set to a value <= 0, the periodic health summary logging is disabled.
   *
   * <p>Config key: {@code redis.scheduler.health-summary-period-seconds}
   */
  private int healthSummaryPeriodSeconds = 600;

  /**
   * How long to cache Redis server time to reduce TIME command calls (milliseconds). Higher values
   * reduce Redis calls but may drift from server time. Config key: {@code
   * redis.scheduler.time-cache-duration-ms}
   */
  private long timeCacheDurationMs = 10000L; // 10 seconds

  // === Cleanup services ===

  /** Zombie cleanup configuration for stuck agents. */
  private ZombieCleanupProperties zombieCleanup = new ZombieCleanupProperties();

  /** Orphan cleanup configuration for agents from crashed instances. */
  private OrphanCleanupProperties orphanCleanup = new OrphanCleanupProperties();

  /** Reconcile offload executor shutdown timeouts. */
  private ReconcileProperties reconcile = new ReconcileProperties();

  // === Performance & resilience ===

  /**
   * Enable batch operations for Redis operations. When enabled, the scheduler will group agent
   * operations together in batches rather than processing them individually. This affects how
   * agents are acquired and scheduled.
   *
   * <p>All batchable workflows (acquisition, completion, zombie/orphan cleanup, repopulation)
   * respect this configuration. See {@link BatchOperations}.
   */
  private BatchOperations batchOperations = new BatchOperations();

  /** Circuit breaker configuration for protecting against cascading failures. */
  private CircuitBreaker circuitBreaker = new CircuitBreaker();

  /**
   * Failure-aware backoff configuration for agent failures.
   *
   * <p>This block controls how the scheduler delays subsequent executions after an agent run fails.
   * It enables class-based backoff (e.g., permanent forbidden, throttled, transient/server errors)
   * and optional jitter to avoid synchronized retries across pods.
   *
   * <p>Lombok disabled: getter is custom (lazy non-null initialization), setter is custom. We must
   * guarantee a non-null FailureBackoffProperties instance when read, and preserve explicit control
   * on writes.
   */
  @Getter(AccessLevel.NONE)
  @Setter(AccessLevel.NONE)
  private FailureBackoffProperties failureBackoff = new FailureBackoffProperties();

  /** Unified jitter configuration block (seconds-based for scheduling). */
  @Setter(AccessLevel.NONE)
  private JitterProperties jitter = new JitterProperties();

  /**
   * Configurable Redis key names and namespacing for the scheduler's data structures.
   *
   * <p>Includes base names for the waiting/working sets and the cleanup leadership key, plus
   * optional prefix and hash-tag. When {@code hashTag} is set (non-empty), the final Redis keys
   * will include the value wrapped in braces to ensure all keys hash to the same slot on Redis
   * Cluster (e.g., {@code waiting{ps}}, {@code working{ps}}, {@code cleanup-leader{ps}}).
   *
   * <p>Lombok disabled: setter is custom and null-coalesces to a default Keys instance.
   */
  @Setter(AccessLevel.NONE)
  private Keys keys = new Keys();

  /**
   * Returns the failure-aware backoff configuration. Never returns null.
   *
   * @return the current failure backoff configuration
   */
  public FailureBackoffProperties getFailureBackoff() {
    if (failureBackoff == null) {
      failureBackoff = new FailureBackoffProperties();
    }
    return failureBackoff;
  }

  /**
   * Sets the failure-aware backoff configuration.
   *
   * @param failureBackoff a non-null configuration object controlling failure backoff behavior
   */
  public void setFailureBackoff(FailureBackoffProperties failureBackoff) {
    this.failureBackoff = failureBackoff;
  }

  public JitterProperties getJitter() {
    if (jitter == null) {
      jitter = new JitterProperties();
    }
    return jitter;
  }

  public void setJitter(JitterProperties jitter) {
    this.jitter = jitter != null ? jitter : new JitterProperties();
  }

  /**
   * Returns the configured Redis key naming and namespacing options. Never returns null.
   *
   * @return key naming configuration
   */
  public Keys getKeys() {
    if (keys == null) {
      keys = new Keys();
    }
    return keys;
  }

  /** Sets the Redis key naming and namespacing options. */
  public void setKeys(Keys keys) {
    this.keys = keys != null ? keys : new Keys();
  }

  /**
   * Batch operations configuration block.
   *
   * <pre>
   * redis:
   *   scheduler:
   *     batch-operations:
   *       enabled: true
   *       batch-size: 50
   * </pre>
   */
  @Getter
  @Setter
  public static class BatchOperations {
    /** Enable batch operations globally (acquisition, cleanup, completion, repopulation). */
    private boolean enabled = true;

    /**
     * Maximum number of items to process in a single batch operation.
     *
     * <p>Applies to all batch operations: agent acquisition, completions, cleanup, repopulation.
     *
     * <p>Default: 0 (unbounded - process all available items)
     */
    private int batchSize = 0;

    /**
     * Multiplier for chunk attempts during acquisition to handle filtering. The base number of
     * attempts is calculated as: ceiling(availableSlots / batchSize). This multiplier accounts for
     * agents that get filtered out (e.g., due to sharding).
     *
     * <p>Examples: - 0 or negative = No multiplier, use base attempts only (default - no extra
     * filtering tolerance) - 1.0 = Same as base attempts (minimum attempts) - 2.0 = Double the
     * attempts to handle up to 50% filtering - 3.0 = Triple the attempts to handle up to 67%
     * filtering
     *
     * <p>Default: 0.0 (no multiplier - adjust if you see warnings about unfilled slots)
     */
    private double chunkAttemptMultiplier = 0.0;
  }

  public boolean isZombieCleanupEnabled() {
    return zombieCleanup.isEnabled();
  }

  public long getZombieThresholdMs() {
    return zombieCleanup.getThresholdMs();
  }

  public long getZombieIntervalMs() {
    return zombieCleanup.getIntervalMs();
  }

  public boolean hasExceptionalAgents() {
    return zombieCleanup.getExceptionalAgents() != null
        && !zombieCleanup.getExceptionalAgents().getPattern().isEmpty();
  }

  public String getExceptionalAgentsPattern() {
    return zombieCleanup.getExceptionalAgents().getPattern();
  }

  public long getExceptionalAgentsThresholdMs() {
    return zombieCleanup.getExceptionalAgents().getThresholdMs();
  }

  // Lazily compiled exceptional-agents regex for reuse across services
  private volatile java.util.regex.Pattern exceptionalAgentsPatternCache;

  public java.util.regex.Pattern getExceptionalAgentsPatternCompiled() {
    try {
      String pattern = getExceptionalAgentsPattern();
      if (pattern == null || pattern.trim().isEmpty()) {
        exceptionalAgentsPatternCache = null;
        return null;
      }
      java.util.regex.Pattern cached = exceptionalAgentsPatternCache;
      if (cached == null || !pattern.equals(cached.pattern())) {
        exceptionalAgentsPatternCache = java.util.regex.Pattern.compile(pattern);
      }
      return exceptionalAgentsPatternCache;
    } catch (Exception e) {
      exceptionalAgentsPatternCache = null;
      return null;
    }
  }

  public boolean isOrphanCleanupEnabled() {
    return orphanCleanup.isEnabled();
  }

  public long getOrphanThresholdMs() {
    return orphanCleanup.getThresholdMs();
  }

  public long getOrphanIntervalMs() {
    return orphanCleanup.getIntervalMs();
  }

  public long getOrphanLeadershipTtlMs() {
    return orphanCleanup.getLeadershipTtlMs();
  }

  public boolean isOrphanForceAllPods() {
    return orphanCleanup.isForceAllPods();
  }

  /**
   * Whether numeric-only members in the waiting set should be removed during orphan cleanup.
   *
   * <p>Public proxy to avoid leaking package-private nested type.
   */
  public boolean isOrphanRemoveNumericOnlyAgents() {
    return orphanCleanup.isRemoveNumericOnlyAgents();
  }

  // === Jitter (public proxies) ===

  /** Initial registration jitter in whole seconds (0 disables). */
  public int getJitterInitialRegistrationSeconds() {
    JitterProperties jitterProps = getJitter();
    return jitterProps != null ? jitterProps.getInitialRegistrationSeconds() : 0;
  }

  /** Shutdown smoothing jitter in whole seconds (0 disables). */
  public int getJitterShutdownSeconds() {
    JitterProperties jitterProps = getJitter();
    return jitterProps != null ? jitterProps.getShutdownSeconds() : 0;
  }

  /** Ratio applied to non-zero failure backoff delays. Range [0.0, 1.0]. */
  public double getJitterFailureBackoffRatio() {
    JitterProperties jitterProps = getJitter();
    return jitterProps != null ? jitterProps.getFailureBackoffRatio() : 0.0d;
  }

  // === Failure backoff (public proxies) ===

  /** Master switch for failure-aware backoff. */
  public boolean isFailureBackoffEnabled() {
    FailureBackoffProperties fb = getFailureBackoff();
    return fb != null && fb.isEnabled();
  }

  /** Immediate retry count before applying error interval for transient/server errors. */
  public int getFailureBackoffMaxImmediateRetries() {
    FailureBackoffProperties fb = getFailureBackoff();
    return fb != null ? fb.getMaxImmediateRetries() : 0;
  }

  /** Fixed backoff for permanent forbidden errors (e.g., 403/AccessDenied). */
  public long getFailureBackoffPermanentForbiddenBackoffMs() {
    FailureBackoffProperties fb = getFailureBackoff();
    return fb != null ? fb.getPermanentForbiddenBackoffMs() : 0L;
  }

  /** Starting backoff for throttled errors. */
  public long getFailureBackoffThrottledBaseMs() {
    FailureBackoffProperties fb = getFailureBackoff();
    FailureBackoffProperties.ThrottledPolicy tp = fb != null ? fb.getThrottled() : null;
    return tp != null ? tp.getBaseMs() : 0L;
  }

  /** Exponential multiplier for throttled errors. */
  public double getFailureBackoffThrottledMultiplier() {
    FailureBackoffProperties fb = getFailureBackoff();
    FailureBackoffProperties.ThrottledPolicy tp = fb != null ? fb.getThrottled() : null;
    return tp != null ? tp.getMultiplier() : 0.0d;
  }

  /** Upper cap for throttled exponential backoff. */
  public long getFailureBackoffThrottledCapMs() {
    FailureBackoffProperties fb = getFailureBackoff();
    FailureBackoffProperties.ThrottledPolicy tp = fb != null ? fb.getThrottled() : null;
    return tp != null ? tp.getCapMs() : 0L;
  }

  // Public proxies to avoid leaking package-private types to other modules
  public long getZombieExecutorShutdownAwaitMs() {
    return zombieCleanup.getExecutorShutdownAwaitMs();
  }

  public long getZombieExecutorShutdownForceAwaitMs() {
    return zombieCleanup.getExecutorShutdownForceAwaitMs();
  }

  public long getOrphanExecutorShutdownAwaitMs() {
    return orphanCleanup.getExecutorShutdownAwaitMs();
  }

  public long getOrphanExecutorShutdownForceAwaitMs() {
    return orphanCleanup.getExecutorShutdownForceAwaitMs();
  }

  public long getZombieRunBudgetMs() {
    return zombieCleanup.getRunBudgetMs();
  }

  public long getOrphanRunBudgetMs() {
    return orphanCleanup.getRunBudgetMs();
  }

  public long getReconcileRunBudgetMs() {
    return getReconcile().getRunBudgetMs();
  }

  public long getReconcileExecutorShutdownAwaitMs() {
    return getReconcile().getExecutorShutdownAwaitMs();
  }

  public long getReconcileExecutorShutdownForceAwaitMs() {
    return getReconcile().getExecutorShutdownForceAwaitMs();
  }

  public ReconcileProperties getReconcile() {
    if (reconcile == null) {
      reconcile = new ReconcileProperties();
    }
    return reconcile;
  }

  public void setReconcile(ReconcileProperties reconcile) {
    this.reconcile = reconcile != null ? reconcile : new ReconcileProperties();
  }

  @PostConstruct
  void validate() {
    validatePositive(intervalMs, "redis.scheduler.interval-ms");
    validatePositive(refreshPeriodSeconds, "redis.scheduler.refresh-period-seconds");
    validateNonNegative(
        batchOperations.getBatchSize(), "redis.scheduler.batch-operations.batch-size");
    double multiplier = batchOperations.getChunkAttemptMultiplier();
    if (multiplier < 0 || !Double.isFinite(multiplier)) {
      throw new IllegalArgumentException(
          "redis.scheduler.batch-operations.chunk-attempt-multiplier must be >= 0 and finite (was "
              + multiplier
              + ")");
    }

    // Keys validation: non-empty base names
    if (keys == null) {
      keys = new Keys();
    }
    if (isBlank(keys.waitingSet)) {
      throw new IllegalArgumentException("redis.scheduler.keys.waiting-set must not be empty");
    }
    if (isBlank(keys.workingSet)) {
      throw new IllegalArgumentException("redis.scheduler.keys.working-set must not be empty");
    }
    if (isBlank(keys.cleanupLeaderKey)) {
      throw new IllegalArgumentException(
          "redis.scheduler.keys.cleanup-leader-key must not be empty");
    }

    // Jitter validation
    if (jitter == null) {
      jitter = new JitterProperties();
    }
    if (jitter.getInitialRegistrationSeconds() < 0) {
      throw new IllegalArgumentException(
          "redis.scheduler.jitter.initial-registration-seconds must be >= 0");
    }
    if (jitter.getShutdownSeconds() < 0) {
      throw new IllegalArgumentException("redis.scheduler.jitter.shutdown-seconds must be >= 0");
    }
    if (jitter.getFailureBackoffRatio() < 0.0 || jitter.getFailureBackoffRatio() > 1.0) {
      throw new IllegalArgumentException(
          "redis.scheduler.jitter.failure-backoff-ratio must be in [0.0, 1.0]");
    }
    // Cleanup validation within sub-blocks
    if (zombieCleanup == null) {
      zombieCleanup = new ZombieCleanupProperties();
    }
    if (orphanCleanup == null) {
      orphanCleanup = new OrphanCleanupProperties();
    }
    if (zombieCleanup.getExecutorShutdownAwaitMs() < 0
        || zombieCleanup.getExecutorShutdownForceAwaitMs() < 0) {
      throw new IllegalArgumentException(
          "redis.scheduler.zombie-cleanup.* shutdown timeouts must be >= 0");
    }
    validateNonNegative(
        zombieCleanup.getRunBudgetMs(), "redis.scheduler.zombie-cleanup.run-budget-ms");
    if (orphanCleanup.getExecutorShutdownAwaitMs() < 0
        || orphanCleanup.getExecutorShutdownForceAwaitMs() < 0) {
      throw new IllegalArgumentException(
          "redis.scheduler.orphan-cleanup.* shutdown timeouts must be >= 0");
    }
    validateNonNegative(
        orphanCleanup.getRunBudgetMs(), "redis.scheduler.orphan-cleanup.run-budget-ms");

    // Warn if leadership TTL is not sufficiently larger than run budget to prevent duplicate work
    // during cleanup. If cleanup takes longer than leadership TTL, another pod may acquire
    // leadership and start a duplicate cleanup pass.
    long leadershipTtlMs = orphanCleanup.getLeadershipTtlMs();
    long runBudgetMs = orphanCleanup.getRunBudgetMs();
    long minimumMarginMs = 60000L; // 1 minute margin
    if (runBudgetMs > 0 && leadershipTtlMs < runBudgetMs + minimumMarginMs) {
      log.warn(
          "Orphan cleanup leadership-ttl-ms ({}) should be >= run-budget-ms ({}) + {}ms margin "
              + "to prevent duplicate cleanup work when cleanup approaches budget limit. "
              + "Consider increasing leadership-ttl-ms to at least {}.",
          leadershipTtlMs,
          runBudgetMs,
          minimumMarginMs,
          runBudgetMs + minimumMarginMs);
    }

    if (reconcile == null) {
      reconcile = new ReconcileProperties();
    }
    if (reconcile.getExecutorShutdownAwaitMs() < 0
        || reconcile.getExecutorShutdownForceAwaitMs() < 0) {
      throw new IllegalArgumentException(
          "redis.scheduler.reconcile.* shutdown timeouts must be >= 0");
    }
    validateNonNegative(reconcile.getRunBudgetMs(), "redis.scheduler.reconcile.run-budget-ms");
  }

  private static void validatePositive(long value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be > 0 (was " + value + ")");
    }
  }

  private static void validatePositive(int value, String name) {
    if (value <= 0) {
      throw new IllegalArgumentException(name + " must be > 0 (was " + value + ")");
    }
  }

  private static void validateNonNegative(int value, String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " must be >= 0 (was " + value + ")");
    }
  }

  private static void validateNonNegative(long value, String name) {
    if (value < 0) {
      throw new IllegalArgumentException(name + " must be >= 0 (was " + value + ")");
    }
  }

  private static boolean isBlank(String str) {
    return str == null || str.trim().isEmpty();
  }

  /**
   * Redis key naming configuration.
   *
   * <p>Defaults use lowercase, function-oriented names.
   */
  @Getter
  @Setter
  public static class Keys {
    /** Base name of the waiting/ready set. Default: "waiting". */
    private String waitingSet = "waiting";
    /** Base name of the working/leased set. Default: "working". */
    private String workingSet = "working";
    /** Leadership key used for orphan cleanup coordination. Default: "cleanup-leader". */
    private String cleanupLeaderKey = "cleanup-leader";
    /**
     * Optional prefix added to all keys. Default: empty.
     *
     * <p>Lombok disabled: setter is custom to normalize null to empty string.
     */
    @Setter(AccessLevel.NONE)
    private String prefix = "";
    /**
     * Optional hash-tag value to force all keys into the same Redis Cluster slot. When non-empty,
     * the final keys will include the value wrapped in braces (e.g., "{ps}").
     *
     * <p>Lombok disabled: setter is custom to normalize null to empty string.
     */
    @Setter(AccessLevel.NONE)
    private String hashTag = "";

    public void setPrefix(String prefix) {
      this.prefix = prefix != null ? prefix : "";
    }

    public void setHashTag(String hashTag) {
      this.hashTag = hashTag != null ? hashTag : "";
    }
  }
}

/**
 * Failure-aware backoff configuration properties.
 *
 * <p>Controls how the scheduler backs off agents after failures. Backoff is applied by scheduling
 * the agent into the waiting set with a future score equal to the computed delay.
 */
@Getter
@Setter
class FailureBackoffProperties {
  /** Master switch for failure-aware backoff. */
  private boolean enabled = false;

  /** Number of immediate retries before applying errorInterval for transient/server errors. */
  private int maxImmediateRetries = 0;

  /** Fixed backoff for permanent forbidden errors (e.g., 403/AccessDenied). */
  private long permanentForbiddenBackoffMs = java.util.concurrent.TimeUnit.MINUTES.toMillis(30);

  /** Throttled backoff policy parameters. */
  private ThrottledPolicy throttled = new ThrottledPolicy();

  /** @return throttled backoff policy parameters */
  public ThrottledPolicy getThrottled() {
    if (throttled == null) {
      throttled = new ThrottledPolicy();
    }
    return throttled;
  }

  /**
   * Sets throttled backoff policy parameters.
   *
   * @param throttled policy (base, multiplier, cap)
   */
  public void setThrottled(ThrottledPolicy throttled) {
    this.throttled = throttled;
  }

  /** Parameters controlling exponential backoff for throttled failures. */
  @Getter
  @Setter
  static class ThrottledPolicy {
    /** Starting backoff for throttled errors. */
    private long baseMs = java.util.concurrent.TimeUnit.SECONDS.toMillis(30);
    /** Exponential multiplier for throttled errors. */
    private double multiplier = 2.0d;
    /** Upper cap for throttled exponential backoff. */
    private long capMs = java.util.concurrent.TimeUnit.MINUTES.toMillis(10);
  }
}

/**
 * Unified jitter configuration properties.
 *
 * <p>Seconds-based knobs affect Redis ZSET scores directly (whole-second granularity):
 *
 * <ul>
 *   <li>initialRegistrationSeconds
 *   <li>shutdownSeconds
 * </ul>
 *
 * The {@code failureBackoffRatio} is a dimensionless ratio applied to non-zero backoff delays.
 */
@Getter
@Setter
class JitterProperties {
  /** One-shot whole-second spread for first insert (new/missing agents). Default: 0 (disabled). */
  private int initialRegistrationSeconds = 0;

  /** Fallback whole-second smoothing for shutdown requeue when cadence metadata is unavailable. */
  private int shutdownSeconds = 0;

  /** ±ratio applied to non-zero failure backoff delays. Range [0.0, 1.0]. Default: 0.1. */
  private double failureBackoffRatio = 0.1d;
}

/**
 * Zombie cleanup configuration properties for stuck agents.
 *
 * <p>Configuration example (kebab-case):
 *
 * <pre>
 * redis:
 *   scheduler:
 *     zombie-cleanup:
 *       enabled: true
 *       threshold-ms: 30000       # 30s buffer
 *       interval-ms: 300000       # 5m cadence
 *       batch-size: 50
 *       exceptional-agents:
 *         pattern: ".*BigQuery.*"
 *         threshold-ms: 3600000   # 60m for exceptional agents
 * </pre>
 */
@Getter
@Setter
class ZombieCleanupProperties {

  /** Whether zombie cleanup is enabled. Can be disabled for debugging or during maintenance. */
  private boolean enabled = true;

  /**
   * How long an agent can run beyond its completion deadline before being considered a zombie
   * (milliseconds). This is a buffer beyond the expected completion time to account for Redis
   * delays and clock skew. Agents past deadline + threshold are considered stuck or orphaned and
   * are forcibly terminated. This buffer provides operational safety for Redis delays and clock
   * skew. Default: 30 seconds.
   */
  private long thresholdMs = 30000L; // 30 seconds

  /** How often to check for and clean up zombie agents (milliseconds). Default: 5 minutes. */
  private long intervalMs = 300000L; // 5 minutes

  /** Configuration for exceptional agents that require different zombie thresholds. */
  private ExceptionalAgentsProperties exceptionalAgents = new ExceptionalAgentsProperties();

  /** Graceful wait for zombie-cleanup executor shutdown (milliseconds). Default: 10000. */
  private long executorShutdownAwaitMs = 10000L;
  /** Forced wait after zombie-cleanup shutdownNow (milliseconds). Default: 5000. */
  private long executorShutdownForceAwaitMs = 5000L;
  /** Optional max runtime budget per cleanup pass (milliseconds). 0 disables. */
  private long runBudgetMs = 0L;

  public ExceptionalAgentsProperties getExceptionalAgents() {
    if (exceptionalAgents == null) {
      exceptionalAgents = new ExceptionalAgentsProperties();
    }
    return exceptionalAgents;
  }

  public void setExceptionalAgents(ExceptionalAgentsProperties exceptionalAgents) {
    this.exceptionalAgents = exceptionalAgents;
  }
}

/** Configuration properties for exceptional agents that require different zombie thresholds. */
@Getter
@Setter
class ExceptionalAgentsProperties {

  /**
   * Regular expression pattern to match agent names that should use exceptional thresholds. Empty
   * string means no exceptional agents. Example patterns: - ".*BigQuery.*" - matches agents
   * containing "BigQuery" - "^(AWS|GCP).*" - matches agents starting with "AWS" or "GCP" -
   * ".*Provider$" - matches agents ending with "Provider"
   */
  private String pattern = "";

  /**
   * Zombie threshold for agents matching the pattern (milliseconds). Default: 60 minutes for
   * exceptional agents that may need longer processing time.
   */
  private long thresholdMs = 3600000L; // 60 minutes
}

/** Orphan cleanup configuration properties for agents from crashed instances. */
@Getter
@Setter
class OrphanCleanupProperties {

  /** Whether orphan cleanup is enabled. Can be disabled for debugging or during maintenance. */
  private boolean enabled = true;

  /**
   * Additional time buffer beyond completion deadlines (working) or execution times (waiting)
   * before considering an agent orphaned (milliseconds). working orphans are agents past completion
   * deadline + buffer. waiting orphans are agents with execution times older than current time -
   * buffer. This buffer accounts for network partitions and Redis latency.
   */
  private long thresholdMs = 600000L; // 10 minutes

  /** How often to check for and clean up orphaned agents (milliseconds). Default: 5 minutes. */
  private long intervalMs = 300000L; // 5 minutes

  /**
   * TTL for distributed cleanup leadership lock (milliseconds). Only one pod gets cleanup
   * leadership at a time. Config key: {@code redis.scheduler.orphan-cleanup.leadership-ttl-ms}
   */
  private long leadershipTtlMs = 120000L; // 2 minutes

  /**
   * Force all pods to participate in orphan cleanup (not just leader). Disabled by default for
   * safety.
   */
  private boolean forceAllPods = false;

  /** Graceful wait for orphan-cleanup executor shutdown (milliseconds). Default: 10000. */
  private long executorShutdownAwaitMs = 10000L;
  /** Forced wait after orphan-cleanup shutdownNow (milliseconds). Default: 5000. */
  private long executorShutdownForceAwaitMs = 5000L;
  /** Optional max runtime budget per cleanup pass (milliseconds). 0 disables. */
  private long runBudgetMs = 0L;

  /** When true, numeric-only members in the waiting set are removed during orphan cleanup. */
  private boolean removeNumericOnlyAgents = true;
}

/** Reconcile executor shutdown tuning knobs. */
@Getter
@Setter
class ReconcileProperties {
  /** Graceful wait for reconcile executor shutdown (milliseconds). Default: 5000. */
  private long executorShutdownAwaitMs = 5000L;
  /** Forced wait after reconcile shutdownNow (milliseconds). Default: 2000. */
  private long executorShutdownForceAwaitMs = 2000L;
  /** Optional max runtime budget per reconcile pass (milliseconds). 0 disables. */
  private long runBudgetMs = 0L;
}
