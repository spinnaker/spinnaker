/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.orca.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.jedis.JedisClientConfiguration;
import com.netflix.spinnaker.kork.jedis.RedisClientSelector;
import com.netflix.spinnaker.kork.jedis.lock.RedisLockManager;
import com.netflix.spinnaker.kork.lock.LockManager;
import com.netflix.spinnaker.kork.telemetry.InstrumentedProxy;
import com.netflix.spinnaker.orca.lock.RunOnLockAcquired;
import com.netflix.spinnaker.orca.lock.RunOnRedisLockAcquired;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.notifications.RedisClusterNotificationClusterLock;
import com.netflix.spinnaker.orca.notifications.RedisNotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.RedisExecutionRepository;
import groovy.util.logging.Slf4j;
import io.reactivex.rxjava3.core.Scheduler;
import java.time.Clock;
import java.util.Collections;
import java.util.Optional;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import redis.clients.jedis.JedisCluster;
import redis.clients.jedis.JedisPoolConfig;

@Slf4j
@Configuration
@ConditionalOnProperty(value = "redis.enabled", matchIfMissing = true)
@Import({JedisClientConfiguration.class, JedisConfiguration.class})
public class RedisConfiguration {

  private static final Logger log = LoggerFactory.getLogger(RedisConfiguration.class);

  public static class Clients {
    public static final String EXECUTION_REPOSITORY = "executionRepository";
    public static final String TASK_QUEUE = "taskQueue";
  }

  @Bean
  @ConditionalOnProperty(value = "execution-repository.redis.enabled", matchIfMissing = true)
  public ExecutionRepository redisExecutionRepository(
      Registry registry,
      RedisClientSelector redisClientSelector,
      @Qualifier("queryAllScheduler") Scheduler queryAllScheduler,
      @Qualifier("queryByAppScheduler") Scheduler queryByAppScheduler,
      @Value("${chunk-size.execution-repository:75}") Integer threadPoolChunkSize,
      @Value("${keiko.queue.redis.queue-name:}") String bufferedPrefix) {
    ExecutionRepository repository =
        new RedisExecutionRepository(
            registry,
            redisClientSelector,
            queryAllScheduler,
            queryByAppScheduler,
            threadPoolChunkSize,
            bufferedPrefix);
    return InstrumentedProxy.proxy(
        registry, repository, "redis.executionRepository", Collections.emptyMap());
  }

  @Bean
  @ConditionalOnProperty(
      value = "redis.cluster-enabled",
      havingValue = "false",
      matchIfMissing = true)
  @ConditionalOnMissingBean(NotificationClusterLock.class)
  public NotificationClusterLock redisNotificationClusterLock(
      RedisClientSelector redisClientSelector) {
    return new RedisNotificationClusterLock(redisClientSelector);
  }

  @Bean
  @ConditionalOnProperty(value = "redis.cluster-enabled")
  @ConditionalOnMissingBean(NotificationClusterLock.class)
  public NotificationClusterLock redisClusterNotificationClusterLock(JedisCluster cluster) {
    return new RedisClusterNotificationClusterLock(cluster);
  }

  @Bean
  @ConfigurationProperties("redis")
  public GenericObjectPoolConfig redisPoolConfig() {
    GenericObjectPoolConfig config = new JedisPoolConfig();
    config.setMaxTotal(100);
    config.setMaxIdle(100);
    config.setMinIdle(25);
    return config;
  }

  @Bean
  @Primary
  @ConditionalOnProperty(value = "redis.external-lock.enabled")
  public RunOnLockAcquired redisRunOnLockAcquired(LockManager lockManager) {
    log.info("Redis distributed locking enabled");
    return new RunOnRedisLockAcquired(lockManager);
  }

  @Bean
  @ConditionalOnProperty(value = "redis.external-lock.enabled")
  public LockManager lockManager(
      Clock clock,
      Registry registry,
      ObjectMapper mapper,
      RedisClientSelector redisClientSelector) {
    return new RedisLockManager(
        "RedisLockManager",
        clock,
        registry,
        mapper,
        redisClientSelector.primary("executionRepository"),
        Optional.empty(),
        Optional.empty());
  }
}
