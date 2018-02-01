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

package com.netflix.spinnaker.orca.config

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import java.lang.reflect.Field
import groovy.transform.CompileStatic
import org.apache.commons.pool2.impl.GenericObjectPool
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import redis.clients.jedis.*
import redis.clients.util.Pool

@Configuration
@CompileStatic
class RedisConfiguration {

  @Bean
  @ConfigurationProperties("redis")
  GenericObjectPoolConfig redisPoolConfig() {
    new GenericObjectPoolConfig(maxTotal: 100, maxIdle: 100, minIdle: 25)
  }

  @Bean(name = "jedisPool")
  @Primary
  Pool<Jedis> jedisPool(@Value('${redis.connection:redis://localhost:6379}') String connection,
                        @Value('${redis.timeout:2000}') int timeout,
                        GenericObjectPoolConfig redisPoolConfig,
                        Registry registry) {
    return createPool(redisPoolConfig, connection, timeout, registry, "jedisPool")
  }

  @Bean(name="jedisPoolPrevious")
  @ConditionalOnProperty("redis.connectionPrevious")
  @ConditionalOnExpression('${redis.connection} != ${redis.connectionPrevious}')
  JedisPool jedisPoolPrevious(@Value('${redis.connectionPrevious:#{null}}') String previousConnection,
                              @Value('${redis.timeout:2000}') int timeout,
                              Registry registry) {
    return createPool(null, previousConnection, timeout, registry, "jedisPoolPrevious")
  }

  @Bean(name="redisClientDelegate")
  @Primary
  RedisClientDelegate redisClientDelegate(@Qualifier("jedisPool") Pool<Jedis> jedisPool) {
    return new JedisClientDelegate(jedisPool)
  }

  @Bean(name="previousRedisClientDelegate")
  @ConditionalOnBean(name="jedisPoolPrevious")
  RedisClientDelegate previousRedisClientDelegate(@Qualifier("jedisPoolPrevious") JedisPool jedisPoolPrevious) {
    return new JedisClientDelegate(jedisPoolPrevious)
  }

  @Bean
  HealthIndicator redisHealth(@Qualifier("jedisPool") Pool<Jedis> jedisPool) {
    final Pool<Jedis> src = jedisPool
    final Field poolAccess = Pool.getDeclaredField("internalPool")
    poolAccess.setAccessible(true)
    new HealthIndicator() {
      @Override
      Health health() {
        Jedis jedis = null
        Health.Builder health = null
        try {
          jedis = src.getResource()
          if ("PONG".equals(jedis.ping())) {
            health = Health.up()
          } else {
            health = Health.down()
          }
        } catch (Exception ex) {
          health = Health.down(ex)
        } finally {
          jedis?.close()
        }
        GenericObjectPool<Jedis> internal = (GenericObjectPool<Jedis>) poolAccess.get(jedisPool)
        health.withDetail("maxIdle", internal.maxIdle)
        health.withDetail("minIdle", internal.minIdle)
        health.withDetail("numActive", jedisPool.numActive)
        health.withDetail("numIdle", jedisPool.numIdle)
        health.withDetail("numWaiters", jedisPool.numWaiters)

        return health.build()
      }
    }
  }

  static JedisPool createPool(GenericObjectPoolConfig redisPoolConfig, String connection, int timeout, Registry registry, String poolName) {
    URI redisConnection = URI.create(connection)

    String host = redisConnection.host
    int port = redisConnection.port == -1 ? Protocol.DEFAULT_PORT : redisConnection.port

    String redisConnectionPath = redisConnection.path ?: "/${Protocol.DEFAULT_DATABASE}"
    int database = Integer.parseInt(redisConnectionPath.split("/", 2)[1])

    String password = redisConnection.userInfo ? redisConnection.userInfo.split(":", 2)[1] : null

    JedisPool jedisPool = new JedisPool(redisPoolConfig ?: new GenericObjectPoolConfig(), host, port, timeout, password, database, null)
    return jedisPool
  }
}
