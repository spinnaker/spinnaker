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

import com.netflix.discovery.EurekaClient
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.redis.cache.RedisCache.CacheMetrics
import com.netflix.spinnaker.cats.redis.cache.RedisCacheOptions
import com.netflix.spinnaker.cats.cluster.AgentIntervalProvider
import com.netflix.spinnaker.cats.cluster.DefaultNodeStatusProvider
import com.netflix.spinnaker.cats.cluster.NodeStatusProvider
import com.netflix.spinnaker.clouddriver.core.RedisConfigurationProperties
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

import java.util.concurrent.TimeUnit

@Configuration
@ConditionalOnExpression('${redis.enabled:true}')
@EnableConfigurationProperties(RedisConfigurationProperties)
class RedisCacheConfig {

  @Bean
  @ConfigurationProperties("caching.redis")
  RedisCacheOptions.Builder redisCacheOptionsBuilder() {
    return RedisCacheOptions.builder()
  }

  @Bean
  RedisCacheOptions redisCacheOptions(RedisCacheOptions.Builder redisCacheOptionsBuilder) {
    return redisCacheOptionsBuilder.build()
  }

  @Bean
  CacheMetrics cacheMetrics(Registry registry) {
    new SpectatorRedisCacheMetrics(registry)
  }

  @Bean
  AgentIntervalProvider agentIntervalProvider(RedisConfigurationProperties redisConfigurationProperties) {
    new CustomSchedulableAgentIntervalProvider(
      TimeUnit.SECONDS.toMillis(redisConfigurationProperties.poll.intervalSeconds),
      TimeUnit.SECONDS.toMillis(redisConfigurationProperties.poll.errorIntervalSeconds),
      TimeUnit.SECONDS.toMillis(redisConfigurationProperties.poll.timeoutSeconds)
    )
  }

  @Bean
  NodeStatusProvider nodeStatusProvider(Optional<EurekaClient> eurekaClient) {
    return eurekaClient.map({ new EurekaStatusNodeStatusProvider(it) }).orElseGet({ new DefaultNodeStatusProvider() })
  }
}
