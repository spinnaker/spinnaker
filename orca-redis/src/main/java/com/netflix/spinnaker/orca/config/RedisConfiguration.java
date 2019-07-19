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

import com.netflix.spectator.api.Registry;
import com.netflix.spinnaker.kork.jedis.JedisClientConfiguration;
import com.netflix.spinnaker.kork.jedis.RedisClientSelector;
import com.netflix.spinnaker.orca.notifications.NotificationClusterLock;
import com.netflix.spinnaker.orca.notifications.RedisClusterNotificationClusterLock;
import com.netflix.spinnaker.orca.notifications.RedisNotificationClusterLock;
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository;
import com.netflix.spinnaker.orca.pipeline.persistence.jedis.RedisExecutionRepository;
import com.netflix.spinnaker.orca.telemetry.RedisInstrumentedExecutionRepository;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.*;
import redis.clients.jedis.JedisCluster;
import rx.Scheduler;

@Configuration
@Import({JedisClientConfiguration.class, JedisConfiguration.class})
public class RedisConfiguration {

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
    return new RedisInstrumentedExecutionRepository(
        new RedisExecutionRepository(
            registry,
            redisClientSelector,
            queryAllScheduler,
            queryByAppScheduler,
            threadPoolChunkSize,
            bufferedPrefix),
        registry);
  }

  @Bean
  @ConditionalOnProperty(
      value = "redis.cluster-enabled",
      havingValue = "false",
      matchIfMissing = true)
  public NotificationClusterLock redisNotificationClusterLock(
      RedisClientSelector redisClientSelector) {
    return new RedisNotificationClusterLock(redisClientSelector);
  }

  @Bean
  @ConditionalOnProperty(value = "redis.cluster-enabled")
  public NotificationClusterLock redisClusterNotificationClusterLock(JedisCluster cluster) {
    return new RedisClusterNotificationClusterLock(cluster);
  }

  @Bean
  @ConfigurationProperties("redis")
  public GenericObjectPoolConfig redisPoolConfig() {
    GenericObjectPoolConfig config = new GenericObjectPoolConfig();
    config.setMaxTotal(100);
    config.setMaxIdle(100);
    config.setMinIdle(25);
    return config;
  }
}
