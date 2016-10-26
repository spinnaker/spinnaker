/*
 * Copyright 2015 Netflix, Inc.
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
import com.netflix.discovery.DiscoveryClient
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.redis.JedisPoolSource
import com.netflix.spinnaker.cats.redis.JedisSource
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions
import com.netflix.spinnaker.cats.redis.cache.RedisNamedCacheFactory
import com.netflix.spinnaker.cats.redis.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.redis.cluster.ClusteredAgentScheduler
import com.netflix.spinnaker.cats.redis.cluster.ClusteredSortAgentScheduler
import com.netflix.spinnaker.cats.redis.cluster.DefaultNodeIdentity
import com.netflix.spinnaker.cats.redis.cluster.DefaultNodeStatusProvider
import com.netflix.spinnaker.cats.redis.cluster.NodeStatusProvider
import com.netflix.spinnaker.clouddriver.core.RedisConfigurationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.JedisPool

import java.util.concurrent.TimeUnit

@Configuration
@ConditionalOnProperty('redis.connection')
@EnableConfigurationProperties(RedisConfigurationProperties)
class RedisCacheConfig {
  @Bean
  JedisSource jedisSource(JedisPool jedisPool) {
    new JedisPoolSource(jedisPool)
  }

  @Bean
  @ConfigurationProperties("caching.redis")
  RedisCacheOptions.Builder redisCacheOptionsBuilder() {
    return RedisCacheOptions.builder();
  }

  @Bean
  RedisCacheOptions redisCacheOptions(RedisCacheOptions.Builder redisCacheOptionsBuilder) {
    return redisCacheOptionsBuilder.build()
  }

  @Bean
  NamedCacheFactory cacheFactory(
    JedisSource jedisSource,
    ObjectMapper objectMapper,
    RedisCacheOptions redisCacheOptions) {
    new RedisNamedCacheFactory(jedisSource, objectMapper, redisCacheOptions, null)
  }

  @Bean
  AgentIntervalProvider agentIntervalProvider(RedisConfigurationProperties redisConfigurationProperties) {
    new CustomSchedulableAgentIntervalProvider(TimeUnit.SECONDS.toMillis(redisConfigurationProperties.poll.intervalSeconds), TimeUnit.SECONDS.toMillis(redisConfigurationProperties.poll.timeoutSeconds))
  }

  @Bean
  @ConditionalOnBean(DiscoveryClient)
  EurekaStatusNodeStatusProvider discoveryStatusNodeStatusProvider(DiscoveryClient discoveryClient) {
    new EurekaStatusNodeStatusProvider(discoveryClient)
  }

  @Bean
  @ConditionalOnMissingBean(NodeStatusProvider)
  DefaultNodeStatusProvider nodeStatusProvider() {
    new DefaultNodeStatusProvider()
  }

  @Bean
  @ConditionalOnProperty(value = 'caching.writeEnabled', matchIfMissing = true)
  AgentScheduler agentScheduler(RedisConfigurationProperties redisConfigurationProperties, JedisSource jedisSource, AgentIntervalProvider agentIntervalProvider, NodeStatusProvider nodeStatusProvider) {
    if (redisConfigurationProperties.scheduler.equalsIgnoreCase('default')) {
      URI redisUri = URI.create(redisConfigurationProperties.connection)
      String redisHost = redisUri.getHost()
      int redisPort = redisUri.getPort()
      if (redisPort == -1) {
        redisPort = 6379
      }
      new ClusteredAgentScheduler(jedisSource, new DefaultNodeIdentity(redisHost, redisPort), agentIntervalProvider, nodeStatusProvider)
    } else if (redisConfigurationProperties.scheduler.equalsIgnoreCase('sort')) {
      new ClusteredSortAgentScheduler(jedisSource, nodeStatusProvider, agentIntervalProvider, redisConfigurationProperties.parallelism ?: -1);
    } else {
      throw new IllegalStateException("redis.scheduler must be one of 'default', 'sort', or ''.");
    }
  }
}
