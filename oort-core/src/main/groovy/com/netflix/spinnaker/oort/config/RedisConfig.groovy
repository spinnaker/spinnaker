/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentScheduler
import com.netflix.spinnaker.cats.cache.NamedCacheFactory
import com.netflix.spinnaker.cats.redis.JedisPoolSource
import com.netflix.spinnaker.cats.redis.JedisSource
import com.netflix.spinnaker.cats.redis.cache.RedisNamedCacheFactory
import com.netflix.spinnaker.cats.redis.cluster.ClusteredAgentScheduler
import com.netflix.spinnaker.cats.redis.cluster.DefaultAgentIntervalProvider
import com.netflix.spinnaker.cats.redis.cluster.DefaultNodeIdentity
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool

import java.util.concurrent.TimeUnit


@ConditionalOnProperty('redis.enabled')
@Configuration
class RedisConfig {

  @Bean
  JedisPool jedisPool(@Value('${redis.host:localhost}') String redisHost, @Value('${redis.port:6379}') int redisPort) {
    GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig()
    poolConfig.setMaxTotal(100)
    poolConfig.setMinIdle(25)
    poolConfig.setMaxIdle(100)
    new JedisPool(poolConfig, redisHost, redisPort)
  }

  @Bean
  HealthIndicator redisHealth(JedisPool jedisPool) {
    final JedisPool src = jedisPool
    new HealthIndicator() {
      @Override
      Health health() {
        Jedis jedis = null
        Health.Builder health = null
        try {
          jedis = src.resource
          if ('PONG'.equals(jedis.ping())) {
            health = Health.up()
          } else {
            health = Health.down()
          }
        } catch (Exception ex) {
          health = Health.down(ex)
        } finally {
          jedis?.close()
        }
        def internal = jedisPool.internalPool //thx groovy
        health.withDetail('maxIdle', internal.maxIdle)
        health.withDetail('minIdle', internal.minIdle)
        health.withDetail('numActive', internal.numActive)
        health.withDetail('numIdle', internal.numIdle)
        health.withDetail('numWaiters', internal.numWaiters)

        return health.build()
      }
    }
  }

  @Bean
  JedisSource jedisSource(JedisPool jedisPool) {
    new JedisPoolSource(jedisPool)
  }

  @Bean
  NamedCacheFactory cacheFactory(JedisSource jedisSource, ObjectMapper objectMapper) {
    new RedisNamedCacheFactory(jedisSource, objectMapper)
  }

  @Bean
  @ConditionalOnProperty(value = 'caching.writeEnabled', matchIfMissing = true)
  AgentScheduler agentScheduler(JedisSource jedisSource, @Value('${redis.host:localhost}') String redisHost, @Value('${redis.port:6379}') int redisPort, @Value('${redis.poll.intervalSeconds:30}') int pollIntervalSeconds, @Value('${redis.poll.timeoutSeconds:300}') int pollTimeoutSeconds) {
    new ClusteredAgentScheduler(jedisSource, new DefaultNodeIdentity(redisHost, redisPort), new DefaultAgentIntervalProvider(TimeUnit.SECONDS.toMillis(pollIntervalSeconds), TimeUnit.SECONDS.toMillis(pollTimeoutSeconds)))
  }
}
