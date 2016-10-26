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

import com.netflix.spinnaker.clouddriver.data.task.TaskRepository
import com.netflix.spinnaker.clouddriver.data.task.jedis.JedisTaskRepository
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.Protocol

@Configuration
@ConditionalOnProperty('redis.connection')
@EnableConfigurationProperties(RedisConfigurationProperties)
class RedisConfig {
  @Bean
  @ConfigurationProperties('redis')
  GenericObjectPoolConfig redisPoolConfig() {
    new GenericObjectPoolConfig(maxTotal: 100, maxIdle: 100, minIdle: 25)
  }

  @Bean
  TaskRepository taskRepository(JedisPool jedisPool, Optional<JedisPool> jedisPoolPrevious) {
    new JedisTaskRepository(jedisPool, jedisPoolPrevious)
  }

  @Bean
  JedisPool jedisPool(RedisConfigurationProperties redisConfigurationProperties,
                      GenericObjectPoolConfig redisPoolConfig) {
    return createPool(redisPoolConfig, redisConfigurationProperties.connection, redisConfigurationProperties.timeout)
  }

  private static JedisPool createPool(GenericObjectPoolConfig redisPoolConfig, String connection, int timeout) {
    URI redisConnection = URI.create(connection)

    String host = redisConnection.host
    int port = redisConnection.port == -1 ? Protocol.DEFAULT_PORT : redisConnection.port

    int database = Integer.parseInt((redisConnection.path ?: "/${Protocol.DEFAULT_DATABASE}").split('/', 2)[1])

    String password = redisConnection.userInfo ? redisConnection.userInfo.split(':', 2)[1] : null

    new JedisPool(redisPoolConfig ?: new GenericObjectPoolConfig(), host, port, timeout, password, database, null)
  }

  @Bean
  JedisPool jedisPoolPrevious(RedisConfigurationProperties redisConfigurationProperties) {
    if (redisConfigurationProperties.connection == redisConfigurationProperties.connectionPrevious || redisConfigurationProperties.connectionPrevious == null) {
      return null
    }
    return createPool(null, redisConfigurationProperties.connectionPrevious, 1000)
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
}
