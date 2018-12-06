/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.clouddriver.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.redis.cache.RedisCache.CacheMetrics
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions
import com.netflix.spinnaker.cats.redis.cache.RedisNamedCacheFactory
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.redis.cluster.ClusteredAgentScheduler
import com.netflix.spinnaker.cats.redis.cluster.ClusteredSortAgentScheduler
import com.netflix.spinnaker.cats.cluster.DefaultNodeIdentity
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider
import com.netflix.spinnaker.clouddriver.core.RedisConfigurationProperties
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.JedisPool

@Configuration
@ConditionalOnExpression('${redis.enabled:true}')
@EnableConfigurationProperties(RedisConfigurationProperties)
class JedisCacheConfig {

  @Bean
  RedisClientDelegate redisClientDelegate(JedisPool jedisPool) {
    new JedisClientDelegate(jedisPool)
  }

  @Bean
  NamedCacheFactory cacheFactory(
    RedisClientDelegate redisClientDelegate,
    ObjectMapper objectMapper,
    RedisCacheOptions redisCacheOptions,
    CacheMetrics cacheMetrics) {
    new RedisNamedCacheFactory(redisClientDelegate, objectMapper, redisCacheOptions, cacheMetrics)
  }

  @Bean
  @ConditionalOnProperty(value = "caching.writeEnabled", matchIfMissing = true)
  AgentScheduler agentScheduler(RedisConfigurationProperties redisConfigurationProperties,
                                RedisClientDelegate redisClientDelegate,
                                JedisPool jedisPool,
                                AgentIntervalProvider agentIntervalProvider,
                                NodeStatusProvider nodeStatusProvider,
                                DynamicConfigService dynamicConfigService) {
    if (redisConfigurationProperties.scheduler.equalsIgnoreCase("default")) {
      URI redisUri = URI.create(redisConfigurationProperties.connection)
      String redisHost = redisUri.host
      int redisPort = redisUri.port
      if (redisPort == -1) {
        redisPort = 6379
      }
      new ClusteredAgentScheduler(
        redisClientDelegate,
        new DefaultNodeIdentity(redisHost, redisPort),
        agentIntervalProvider,
        nodeStatusProvider,
        redisConfigurationProperties.agent.enabledPattern,
        redisConfigurationProperties.agent.agentLockAcquisitionIntervalSeconds,
        dynamicConfigService)
    } else if (redisConfigurationProperties.scheduler.equalsIgnoreCase("sort")) {
      new ClusteredSortAgentScheduler(jedisPool, nodeStatusProvider, agentIntervalProvider, redisConfigurationProperties.parallelism ?: -1);
    } else {
      throw new IllegalStateException("redis.scheduler must be one of 'default', 'sort', or ''.");
    }
  }
}
