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
package com.netflix.spinnaker.config

import com.netflix.spinnaker.kork.jedis.JedisClientDelegate
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.springframework.boot.actuate.health.Health
import org.springframework.boot.actuate.health.HealthIndicator
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPool
import redis.clients.jedis.Protocol
import java.net.URI

@Configuration
@ConditionalOnProperty("redis.enabled")
@EnableConfigurationProperties(JedisConfigurationProperties::class)
open class JedisConfiguration {

  @Bean
  @ConfigurationProperties("redis")
  open fun redisPoolConfig(): GenericObjectPoolConfig
    = GenericObjectPoolConfig().apply {
        maxTotal = 100
        maxIdle = 100
        minIdle = 25
      }

  @Bean(name = arrayOf("mainRedisClient")) open fun jedisClientDelegate(jedisPool: JedisPool?): JedisClientDelegate? {
    if (jedisPool == null) {
      return null
    }
    return JedisClientDelegate(jedisPool)
  }

  @Bean(name = arrayOf("previousRedisClient")) open fun jedisClientDelegatePrevious(jedisPoolPrevious: JedisPool?): JedisClientDelegate? {
    if (jedisPoolPrevious == null) {
      return null
    }
    return JedisClientDelegate(jedisPoolPrevious)
  }

  @Bean open fun jedisPool(redisConfigurationProperties: JedisConfigurationProperties,
                      genericObjectPoolConfig: GenericObjectPoolConfig?): JedisPool? {
    if (redisConfigurationProperties.connection == null) {
      return null
    }
    return createPool(genericObjectPoolConfig, redisConfigurationProperties.connection!!, redisConfigurationProperties.timeout)
  }

  @Bean open fun jedisPoolPrevious(redisConfigurationProperties: JedisConfigurationProperties): JedisPool? {
    if (redisConfigurationProperties.connectionPrevious == null) {
      return null
    }
    return createPool(null, redisConfigurationProperties.connectionPrevious!!, redisConfigurationProperties.timeout)
  }

  private fun createPool(redisPoolConfig: GenericObjectPoolConfig?, connection: String, timeout: Int): JedisPool {
    val redisConnection = URI.create(connection)
    val host = redisConnection.host
    val port = if (redisConnection.port == -1) Protocol.DEFAULT_PORT else redisConnection.port
    val db = Integer.parseInt(
      (if (redisConnection.path == "") "/${Protocol.DEFAULT_DATABASE}" else redisConnection.path).split("/")[1]
    )
    val password = if (redisConnection.userInfo == null) null else redisConnection.userInfo.split(":")[1]

    return JedisPool(redisPoolConfig ?: GenericObjectPoolConfig(), host, port, timeout, password, db, null)
  }

  @Bean open fun redisHealth(jedisPool: JedisPool) =
    HealthIndicator {
      var jedis: Jedis? = null
      var health: Health.Builder?
      try {
        jedis = jedisPool.resource
        health = if (jedis.ping() == "PONG") {
          Health.up()
        } else {
          Health.down()
        }
      } catch (e: Exception) {
        health = Health.down(e)
      } finally {
        jedis?.close()
      }
      health?.build()
    }
}
