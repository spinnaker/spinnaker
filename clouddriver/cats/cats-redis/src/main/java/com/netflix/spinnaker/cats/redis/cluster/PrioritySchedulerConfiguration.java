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

import static com.netflix.spinnaker.cats.redis.cluster.support.ExecutorUtils.newNamedCachedThreadPool;
import static com.netflix.spinnaker.cats.redis.cluster.support.ExecutorUtils.newNamedSingleThreadScheduledExecutor;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Configuration management for the PriorityAgentScheduler.
 *
 * <p>Manages thread pools, semaphores, agent patterns, and runtime configuration. Configuration is
 * cached via Spring Boot @ConfigurationProperties.
 */
@Component
@Slf4j
public class PrioritySchedulerConfiguration {

  private final PriorityAgentProperties agentProperties;
  private final PrioritySchedulerProperties schedulerProperties;

  // Runtime configuration
  private volatile Pattern enabledAgentPattern;
  private volatile Pattern disabledAgentPattern;
  private volatile ExecutorService agentWorkPool;
  private volatile ScheduledExecutorService schedulerExecutorService;
  private volatile Semaphore maxConcurrentSemaphore;

  /**
   * Constructs a new PriorityConfiguration instance with the provided properties.
   *
   * @param agentProperties Configuration properties for agent management
   * @param schedulerProperties Configuration properties for scheduler behavior
   */
  public PrioritySchedulerConfiguration(
      PriorityAgentProperties agentProperties, PrioritySchedulerProperties schedulerProperties) {
    this.agentProperties = agentProperties;
    this.schedulerProperties = schedulerProperties;

    initializeConfiguration();
  }

  /** Initialize all configuration-dependent resources. */
  private void initializeConfiguration() {
    // Compile enabled agent pattern
    this.enabledAgentPattern = Pattern.compile(agentProperties.getEnabledPattern());
    log.info("Enabled agent pattern: {}", agentProperties.getEnabledPattern());

    // Compile disabled agent pattern if provided
    if (!agentProperties.getDisabledPattern().isEmpty()) {
      this.disabledAgentPattern = Pattern.compile(agentProperties.getDisabledPattern());
      log.info("Disabled agent pattern: {}", agentProperties.getDisabledPattern());
    } else {
      this.disabledAgentPattern = null;
      log.info("No disabled agent pattern configured");
    }

    // Create thread pool for agent execution
    createAgentWorkPool();

    // Create scheduler executor service
    createSchedulerExecutorService();

    // Create semaphore for concurrency control
    createConcurrencyControl();

    log.info("Scheduler configuration initialized successfully");
  }

  /**
   * Get the agent execution thread pool.
   *
   * @return ExecutorService for executing agents
   */
  public ExecutorService getAgentWorkPool() {
    return agentWorkPool;
  }

  /**
   * Get the scheduler executor service.
   *
   * @return ScheduledExecutorService for scheduler timing
   */
  public ScheduledExecutorService getSchedulerExecutorService() {
    return schedulerExecutorService;
  }

  /**
   * Get the concurrency control semaphore.
   *
   * @return Optional semaphore for instance-wide concurrency control
   */
  public Semaphore getMaxConcurrentSemaphore() {
    return maxConcurrentSemaphore;
  }

  /**
   * Get the compiled pattern for enabled agents.
   *
   * @return Pattern for filtering enabled agents
   */
  public Pattern getEnabledAgentPattern() {
    return enabledAgentPattern;
  }

  /**
   * Get the compiled pattern for disabled agents.
   *
   * @return Pattern for filtering disabled agents, or null if no pattern configured
   */
  public Pattern getDisabledAgentPattern() {
    return disabledAgentPattern;
  }

  /**
   * Get the scheduler interval in milliseconds.
   *
   * @return scheduler interval
   */
  public long getSchedulerIntervalMs() {
    return schedulerProperties.getIntervalMs();
  }

  /**
   * Get the Redis refresh period in seconds.
   *
   * @return refresh period
   */
  public int getRedisRefreshPeriod() {
    return schedulerProperties.getRefreshPeriodSeconds();
  }

  /**
   * Health summary logging period in milliseconds. If configured as <= 0 seconds, returns 0 to
   * indicate the feature is disabled.
   */
  public long getHealthSummaryPeriodMs() {
    int sec = schedulerProperties.getHealthSummaryPeriodSeconds();
    return sec <= 0 ? 0L : java.util.concurrent.TimeUnit.SECONDS.toMillis(sec);
  }

  /**
   * Get the maximum concurrent agents.
   *
   * @return max concurrent agents
   */
  public int getMaxConcurrentAgents() {
    return agentProperties.getMaxConcurrentAgents();
  }

  /**
   * Check if zombie cleanup is enabled.
   *
   * @return true if zombie cleanup is enabled
   */
  public boolean isZombieCleanupEnabled() {
    return schedulerProperties.getZombieCleanup().isEnabled();
  }

  /**
   * Get the zombie threshold in milliseconds.
   *
   * @return zombie threshold
   */
  public long getZombieThresholdMs() {
    return schedulerProperties.getZombieCleanup().getThresholdMs();
  }

  /**
   * Get the zombie cleanup interval in milliseconds.
   *
   * @return zombie cleanup interval
   */
  public long getZombieIntervalMs() {
    return schedulerProperties.getZombieCleanup().getIntervalMs();
  }

  /**
   * Get the zombie cleanup batch size.
   *
   * @return batch size for zombie cleanup
   */
  public int getZombieCleanupBatchSize() {
    return schedulerProperties.getBatchOperations().getBatchSize();
  }

  /**
   * Check if orphan cleanup is enabled for this pod.
   *
   * @return true if orphan cleanup is enabled
   */
  public boolean isOrphanCleanupEnabled() {
    return schedulerProperties.getOrphanCleanup().isEnabled();
  }

  /**
   * Get the orphan threshold in milliseconds.
   *
   * @return orphan threshold
   */
  public long getOrphanThresholdMs() {
    return schedulerProperties.getOrphanCleanup().getThresholdMs();
  }

  /**
   * Get the orphan cleanup interval in milliseconds.
   *
   * @return orphan cleanup interval
   */
  public long getOrphanIntervalMs() {
    return schedulerProperties.getOrphanCleanup().getIntervalMs();
  }

  /**
   * Get the orphan cleanup batch size.
   *
   * @return batch size for orphan cleanup
   */
  public int getOrphanCleanupBatchSize() {
    return schedulerProperties.getBatchOperations().getBatchSize();
  }

  /**
   * Get the orphan cleanup leadership TTL in milliseconds.
   *
   * @return leadership TTL
   */
  public long getOrphanCleanupLeadershipTtlMs() {
    return schedulerProperties.getOrphanCleanup().getLeadershipTtlMs();
  }

  /**
   * Check if all pods should be forced to participate in orphan cleanup.
   *
   * @return true if all pods should participate
   */
  public boolean isForceOrphanCleanupAllPods() {
    return schedulerProperties.getOrphanCleanup().isForceAllPods();
  }

  /**
   * Check if batch operations are enabled.
   *
   * @return true if batch operations are enabled
   */
  public boolean isBatchOperationsEnabled() {
    return schedulerProperties.getBatchOperations().isEnabled();
  }

  // === Cleanup executor shutdown timeouts (sourced from sub-blocks) ===

  /**
   * Get the graceful shutdown await time for zombie cleanup executor.
   *
   * @return shutdown await time in milliseconds
   */
  public long getZombieExecutorShutdownAwaitMs() {
    return schedulerProperties.getZombieCleanup().getExecutorShutdownAwaitMs();
  }

  /**
   * Get the forced shutdown await time for zombie cleanup executor.
   *
   * @return forced shutdown await time in milliseconds
   */
  public long getZombieExecutorShutdownForceAwaitMs() {
    return schedulerProperties.getZombieCleanup().getExecutorShutdownForceAwaitMs();
  }

  /**
   * Get the graceful shutdown await time for orphan cleanup executor.
   *
   * @return shutdown await time in milliseconds
   */
  public long getOrphanExecutorShutdownAwaitMs() {
    return schedulerProperties.getOrphanCleanup().getExecutorShutdownAwaitMs();
  }

  /**
   * Get the forced shutdown await time for orphan cleanup executor.
   *
   * @return forced shutdown await time in milliseconds
   */
  public long getOrphanExecutorShutdownForceAwaitMs() {
    return schedulerProperties.getOrphanCleanup().getExecutorShutdownForceAwaitMs();
  }

  /**
   * Get the graceful shutdown await time for reconcile executor.
   *
   * @return shutdown await time in milliseconds
   */
  public long getReconcileExecutorShutdownAwaitMs() {
    return safeReconcile().getExecutorShutdownAwaitMs();
  }

  /**
   * Get the forced shutdown await time for reconcile executor.
   *
   * @return forced shutdown await time in milliseconds
   */
  public long getReconcileExecutorShutdownForceAwaitMs() {
    return safeReconcile().getExecutorShutdownForceAwaitMs();
  }

  /**
   * Get the maximum time budget for zombie cleanup operations.
   *
   * @return run budget in milliseconds (0 = unlimited)
   */
  public long getZombieRunBudgetMs() {
    return schedulerProperties.getZombieCleanup().getRunBudgetMs();
  }

  /**
   * Get the maximum time budget for orphan cleanup operations.
   *
   * @return run budget in milliseconds (0 = unlimited)
   */
  public long getOrphanRunBudgetMs() {
    return schedulerProperties.getOrphanCleanup().getRunBudgetMs();
  }

  /**
   * Get the maximum time budget for reconcile operations.
   *
   * @return run budget in milliseconds (0 = unlimited)
   */
  public long getReconcileRunBudgetMs() {
    return safeReconcile().getRunBudgetMs();
  }

  /**
   * Get reconcile properties with null-safe fallback.
   *
   * @return reconcile properties (never null)
   */
  private ReconcileProperties safeReconcile() {
    ReconcileProperties reconcileProps = schedulerProperties.getReconcile();
    return reconcileProps != null ? reconcileProps : new ReconcileProperties();
  }

  /** Shutdown all managed resources. */
  public void shutdown() {
    log.info("Shutting down scheduler configuration resources");

    if (agentWorkPool != null) {
      agentWorkPool.shutdown();
      try {
        if (!agentWorkPool.awaitTermination(30, TimeUnit.SECONDS)) {
          agentWorkPool.shutdownNow();
        }
      } catch (InterruptedException e) {
        agentWorkPool.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    if (schedulerExecutorService != null) {
      schedulerExecutorService.shutdown();
      try {
        if (!schedulerExecutorService.awaitTermination(10, TimeUnit.SECONDS)) {
          schedulerExecutorService.shutdownNow();
        }
      } catch (InterruptedException e) {
        schedulerExecutorService.shutdownNow();
        Thread.currentThread().interrupt();
      }
    }

    log.info("Scheduler configuration shutdown completed");
  }

  /** Creates the agent work pool. */
  private void createAgentWorkPool() {
    this.agentWorkPool = newNamedCachedThreadPool("PriorityAgentWorker-%d");
    log.info("Created agent work pool");
  }

  /** Creates the scheduler executor service. */
  private void createSchedulerExecutorService() {
    this.schedulerExecutorService =
        newNamedSingleThreadScheduledExecutor("PriorityAgentScheduler-%d");

    log.info("Created scheduler executor service");
  }

  /**
   * Creates the concurrency control semaphore based on the configured maximum concurrent agents.
   */
  private void createConcurrencyControl() {
    int maxConcurrentAgents = agentProperties.getMaxConcurrentAgents();

    if (maxConcurrentAgents <= 0) {
      this.maxConcurrentSemaphore = null; // unbounded mode; callers null-check
      log.info("Concurrency semaphore disabled (unbounded mode)");
      return;
    }

    this.maxConcurrentSemaphore = new Semaphore(maxConcurrentAgents);
    log.info("Created concurrency semaphore with {} permits", maxConcurrentAgents);
  }
}
