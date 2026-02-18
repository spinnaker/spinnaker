/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.clouddriver.cache;

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.cats.agent.AgentScheduler;
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider;
import com.netflix.spinnaker.cats.cluster.DefaultNodeIdentity;
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider;
import com.netflix.spinnaker.cats.cluster.ShardingFilter;
import com.netflix.spinnaker.cats.redis.cluster.ClusteredAgentScheduler;
import com.netflix.spinnaker.cats.redis.cluster.ClusteredSortAgentScheduler;
import com.netflix.spinnaker.cats.redis.cluster.PriorityAgentProperties;
import com.netflix.spinnaker.cats.redis.cluster.PriorityAgentScheduler;
import com.netflix.spinnaker.cats.redis.cluster.PrioritySchedulerMetrics;
import com.netflix.spinnaker.cats.redis.cluster.PrioritySchedulerProperties;
import com.netflix.spinnaker.clouddriver.core.RedisConfigurationProperties;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate;
import java.net.URI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import redis.clients.jedis.JedisPool;

@Configuration
@EnableConfigurationProperties({PriorityAgentProperties.class, PrioritySchedulerProperties.class})
@ConditionalOnProperty(value = "caching.write-enabled", matchIfMissing = true)
public class AgentSchedulerConfig {

  private static final Logger log = LoggerFactory.getLogger(AgentSchedulerConfig.class);
  private static final int DEFAULT_REDIS_PORT = 6379;

  @Bean
  public PrioritySchedulerMetrics prioritySchedulerMetrics(Registry registry) {
    return new PrioritySchedulerMetrics(registry);
  }

  /**
   * Creates the "default" Redis agent scheduler. This bean is only created if redis.scheduler.type
   * is "default" or not specified.
   */
  @Bean
  @ConditionalOnProperty(
      value = "redis.scheduler.type",
      havingValue = "default",
      matchIfMissing = true)
  @ConditionalOnExpression("${redis.enabled:true} && ${redis.scheduler.enabled:true}")
  public AgentScheduler defaultRedisAgentScheduler(
      RedisConfigurationProperties redisConfigurationProperties,
      RedisClientDelegate redisClientDelegate,
      AgentIntervalProvider agentIntervalProvider,
      NodeStatusProvider nodeStatusProvider,
      HealthEndpoint healthEndpoint,
      DynamicConfigService dynamicConfigService,
      ShardingFilter shardingFilter) {
    log.info("Creating ClusteredAgentScheduler (default)");
    URI redisUri;
    try {
      redisUri = URI.create(redisConfigurationProperties.getConnection());
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException(
          "Invalid Redis connection URI: " + redisConfigurationProperties.getConnection(), e);
    }
    String redisHost = redisUri.getHost();
    int redisPort = redisUri.getPort() == -1 ? DEFAULT_REDIS_PORT : redisUri.getPort();

    return new ClusteredAgentScheduler(
        redisClientDelegate,
        new DefaultNodeIdentity(redisHost, redisPort),
        agentIntervalProvider,
        nodeStatusProvider,
        healthEndpoint,
        redisConfigurationProperties.getAgent().getEnabledPattern(),
        redisConfigurationProperties.getAgent().getAgentLockAcquisitionIntervalSeconds(),
        dynamicConfigService,
        shardingFilter);
  }

  /**
   * Creates the "sort" Redis agent scheduler. This bean is only created if redis.scheduler.type is
   * "sort".
   */
  @Bean
  @ConditionalOnProperty(value = "redis.scheduler.type", havingValue = "sort")
  @ConditionalOnExpression("${redis.enabled:true} && ${redis.scheduler.enabled:true}")
  public AgentScheduler sortRedisAgentScheduler(
      JedisPool jedisPool,
      NodeStatusProvider nodeStatusProvider,
      AgentIntervalProvider agentIntervalProvider,
      RedisConfigurationProperties redisConfigurationProperties) {
    log.info("Creating ClusteredSortAgentScheduler (sort)");

    int parallelism = redisConfigurationProperties.getScheduler().getParallelism();
    if (parallelism > 0) {
      log.info(
          "ClusteredSortAgentScheduler using parallelism: {} (max concurrent agents)", parallelism);
    } else if (parallelism == -1) {
      log.info("ClusteredSortAgentScheduler using unlimited parallelism");
    } else {
      log.warn(
          "Invalid parallelism value: {}. ClusteredSortAgentScheduler requires positive value or -1",
          parallelism);
    }

    if (!redisConfigurationProperties.getAgent().getDisabledAgents().isEmpty()) {
      log.warn(
          "redis.agent.disabledAgents ({} agents) is NOT supported by ClusteredSortAgentScheduler and will be ignored. "
              + "Consider migrating to priority scheduler for agent filtering support.",
          redisConfigurationProperties.getAgent().getDisabledAgents().size());
    }

    return new ClusteredSortAgentScheduler(
        jedisPool, nodeStatusProvider, agentIntervalProvider, parallelism);
  }

  /**
   * Creates the "priority" Redis agent scheduler. This bean is only created if redis.scheduler.type
   * is "priority". Uses Spring dependency injection for configuration properties.
   */
  @Bean
  @ConditionalOnProperty(value = "redis.scheduler.type", havingValue = "priority")
  @ConditionalOnExpression("${redis.enabled:true} && ${redis.scheduler.enabled:true}")
  public AgentScheduler priorityRedisAgentScheduler(
      JedisPool jedisPool,
      NodeStatusProvider nodeStatusProvider,
      AgentIntervalProvider agentIntervalProvider,
      ShardingFilter shardingFilter,
      PriorityAgentProperties agentProperties,
      PrioritySchedulerProperties schedulerProperties,
      PrioritySchedulerMetrics metrics,
      RedisConfigurationProperties redisConfigurationProperties) {
    log.info("Creating PriorityAgentScheduler [priority]");

    int parallelism = redisConfigurationProperties.getScheduler().getParallelism();
    if (parallelism != 0) {
      log.warn(
          "redis.scheduler.parallelism ({}) is completely ignored by PriorityAgentScheduler. "
              + "Use redis.agent.max-concurrent-agents instead (current: {})",
          parallelism,
          agentProperties.getMaxConcurrentAgents());
    }
    if (!redisConfigurationProperties.getAgent().getDisabledAgents().isEmpty()) {
      log.warn(
          "redis.agent.disabledAgents ({} agents) is ignored by PriorityAgentScheduler. "
              + "Use redis.agent.disabled-pattern instead (current: '{}')",
          redisConfigurationProperties.getAgent().getDisabledAgents().size(),
          agentProperties.getDisabledPattern());
    }

    // Log scheduler configuration for operational visibility
    log.info(
        "[priority] core: max-concurrent-agents={} interval-ms={} refresh-period-seconds={} time-cache-duration-ms={}",
        agentProperties.getMaxConcurrentAgents(),
        schedulerProperties.getIntervalMs(),
        schedulerProperties.getRefreshPeriodSeconds(),
        schedulerProperties.getTimeCacheDurationMs());

    log.info(
        "[priority] filters: enabled-pattern='{}' disabled-pattern='{}'",
        agentProperties.getEnabledPattern(),
        agentProperties.getDisabledPattern());

    int healthSummarySec = schedulerProperties.getHealthSummaryPeriodSeconds();
    log.info(
        "[priority] health: health-summary-period-seconds={} ({})",
        healthSummarySec,
        (healthSummarySec <= 0 ? "disabled" : "enabled"));

    // Circuit breaker
    log.info(
        "[priority] circuit-breaker: enabled={} failure-threshold={} failure-window-ms={} cooldown-ms={} half-open-duration-ms={}",
        schedulerProperties.getCircuitBreaker().isEnabled(),
        schedulerProperties.getCircuitBreaker().getFailureThreshold(),
        schedulerProperties.getCircuitBreaker().getFailureWindowMs(),
        schedulerProperties.getCircuitBreaker().getCooldownMs(),
        schedulerProperties.getCircuitBreaker().getHalfOpenDurationMs());

    // Jitter
    log.info(
        "[priority] jitter: initial-registration-seconds={} shutdown-seconds={} failure-backoff-ratio={}",
        schedulerProperties.getJitterInitialRegistrationSeconds(),
        schedulerProperties.getJitterShutdownSeconds(),
        schedulerProperties.getJitterFailureBackoffRatio());

    // Failure-aware backoff
    log.info(
        "[priority] failure-backoff: enabled={} max-immediate-retries={} permanent-forbidden-ms={} throttled[base-ms={},multiplier={},cap-ms={}]",
        schedulerProperties.isFailureBackoffEnabled(),
        schedulerProperties.getFailureBackoffMaxImmediateRetries(),
        schedulerProperties.getFailureBackoffPermanentForbiddenBackoffMs(),
        schedulerProperties.getFailureBackoffThrottledBaseMs(),
        schedulerProperties.getFailureBackoffThrottledMultiplier(),
        schedulerProperties.getFailureBackoffThrottledCapMs());

    // Cleanup blocks
    log.info(
        "[priority] zombie-cleanup: enabled={} threshold-ms={} interval-ms={} run-budget-ms={} shutdown[await-ms={},force-await-ms={}]",
        schedulerProperties.isZombieCleanupEnabled(),
        schedulerProperties.getZombieThresholdMs(),
        schedulerProperties.getZombieIntervalMs(),
        schedulerProperties.getZombieRunBudgetMs(),
        schedulerProperties.getZombieExecutorShutdownAwaitMs(),
        schedulerProperties.getZombieExecutorShutdownForceAwaitMs());

    if (schedulerProperties.hasExceptionalAgents()) {
      log.info(
          "[priority] zombie-cleanup.exceptional: pattern='{}' threshold-ms={}",
          schedulerProperties.getExceptionalAgentsPattern(),
          schedulerProperties.getExceptionalAgentsThresholdMs());
    }

    log.info(
        "[priority] orphan-cleanup: enabled={} threshold-ms={} interval-ms={} leadership-ttl-ms={} force-all-pods={} remove-numeric-only-agents={} run-budget-ms={} shutdown[await-ms={},force-await-ms={}]",
        schedulerProperties.isOrphanCleanupEnabled(),
        schedulerProperties.getOrphanThresholdMs(),
        schedulerProperties.getOrphanIntervalMs(),
        schedulerProperties.getOrphanLeadershipTtlMs(),
        schedulerProperties.isOrphanForceAllPods(),
        schedulerProperties.isOrphanRemoveNumericOnlyAgents(),
        schedulerProperties.getOrphanRunBudgetMs(),
        schedulerProperties.getOrphanExecutorShutdownAwaitMs(),
        schedulerProperties.getOrphanExecutorShutdownForceAwaitMs());

    // Batch operations & reconcile
    log.info(
        "[priority] batch-operations: enabled={} batch-size={} chunk-attempt-multiplier={}",
        schedulerProperties.getBatchOperations().isEnabled(),
        schedulerProperties.getBatchOperations().getBatchSize(),
        schedulerProperties.getBatchOperations().getChunkAttemptMultiplier());

    log.info(
        "[priority] reconcile: shutdown[await-ms={},force-await-ms={}] run-budget-ms={}",
        schedulerProperties.getReconcileExecutorShutdownAwaitMs(),
        schedulerProperties.getReconcileExecutorShutdownForceAwaitMs(),
        schedulerProperties.getReconcileRunBudgetMs());

    // Redis key structure
    log.info(
        "[priority] keys: prefix='{}' hash-tag='{}' waiting='{}' working='{}' cleanup-leader='{}'",
        schedulerProperties.getKeys().getPrefix(),
        schedulerProperties.getKeys().getHashTag(),
        schedulerProperties.getKeys().getWaitingSet(),
        schedulerProperties.getKeys().getWorkingSet(),
        schedulerProperties.getKeys().getCleanupLeaderKey());

    // Failure-aware backoff configuration intentionally not expanded here because nested
    // property types are package-private; see docs for details.

    return new PriorityAgentScheduler(
        jedisPool,
        nodeStatusProvider,
        agentIntervalProvider,
        shardingFilter,
        agentProperties,
        schedulerProperties,
        metrics);
  }
}
