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

package com.netflix.spinnaker.clouddriver.core

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.data.task.jedis.RedisTaskRepository
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import com.netflix.spinnaker.kork.jedis.telemetry.InstrumentedJedisPool
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.Protocol

@Configuration
@ConditionalOnExpression('${redis.enabled:true}')
@EnableConfigurationProperties(RedisConfigurationProperties)
class RedisConfig {
  @Bean
  @ConfigurationProperties('redis')
  GenericObjectPoolConfig redisPoolConfig() {
    new GenericObjectPoolConfig(maxTotal: 100, maxIdle: 100, minIdle: 25)
  }

  @Bean
  @ConditionalOnExpression('${redis.taskRepository.enabled:true}')
  TaskRepository taskRepository(RedisClientDelegate redisClientDelegate, Optional<RedisClientDelegate> redisClientDelegatePrevious) {
    new RedisTaskRepository(redisClientDelegate, redisClientDelegatePrevious)
  }

  @Bean
  RedisClientDelegate redisClientDelegate(JedisPool jedisPool) {
    return new JedisClientDelegate(jedisPool);
  }

  @Bean
  RedisClientDelegate redisClientDelegatePrevious(JedisPool jedisPoolPrevious) {
    return new JedisClientDelegate(jedisPoolPrevious)
  }

  @Bean
  JedisPool jedisPool(Registry registry,
                      RedisConfigurationProperties redisConfigurationProperties,
                      GenericObjectPoolConfig redisPoolConfig) {
    def jedisPool = createPool(
      registry,
      redisPoolConfig,
      redisConfigurationProperties.connection,
      redisConfigurationProperties.timeout,
      "primaryDefault"
    )

    if (jedisPool instanceof InstrumentedJedisPool) {
      GenericObjectPool internalPool = ((InstrumentedJedisPool) jedisPool).delegated.internalPool

      registry.gauge("jedis.pool.maxIdle", internalPool, { GenericObjectPool p -> return p.maxIdle as Double })
      registry.gauge("jedis.pool.minIdle", internalPool, { GenericObjectPool p -> return p.minIdle as Double })
      registry.gauge("jedis.pool.numActive", internalPool, { GenericObjectPool p -> return p.numActive as Double })
      registry.gauge("jedis.pool.numIdle", internalPool, { GenericObjectPool p -> return p.numIdle as Double })
      registry.gauge("jedis.pool.numWaiters", internalPool, { GenericObjectPool p -> return p.numWaiters as Double })
    }

    return jedisPool
  }

  private static JedisPool createPool(Registry registry,
                                      GenericObjectPoolConfig redisPoolConfig,
                                      String connection,
                                      int timeout,
                                      String name) {
    URI redisConnection = URI.create(connection)

    String host = redisConnection.host
    int port = redisConnection.port == -1 ? Protocol.DEFAULT_PORT : redisConnection.port

    int database = Integer.parseInt((redisConnection.path ?: "/${Protocol.DEFAULT_DATABASE}").split('/', 2)[1])

    String password = redisConnection.userInfo ? redisConnection.userInfo.split(':', 2)[1] : null

    new InstrumentedJedisPool(
      registry,
      new JedisPool(redisPoolConfig ?: new GenericObjectPoolConfig(), host, port, timeout, password, database, null),
      name
    )
  }

  @Bean
  JedisPool jedisPoolPrevious(Registry registry, RedisConfigurationProperties redisConfigurationProperties) {
    if (redisConfigurationProperties.connection == redisConfigurationProperties.connectionPrevious || redisConfigurationProperties.connectionPrevious == null) {
      return null
    }
    return createPool(registry, null, redisConfigurationProperties.connectionPrevious, 1000, "previousDefault")
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

        if (jedisPool instanceof InstrumentedJedisPool) {
          GenericObjectPool internalPool = ((InstrumentedJedisPool) jedisPool).delegated.internalPool //thx groovy
          health.withDetail('maxIdle', internalPool.maxIdle)
          health.withDetail('minIdle', internalPool.minIdle)
          health.withDetail('numActive', internalPool.numActive)
          health.withDetail('numIdle', internalPool.numIdle)
          health.withDetail('numWaiters', internalPool.numWaiters)
        }

        return health.build()
      }
    }
  }
}
