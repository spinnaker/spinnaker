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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.jedis.JedisDriverProperties
import com.netflix.spinnaker.kork.jedis.JedisPoolFactory
import com.netflix.spinnaker.orca.q.QueueShovel
import com.netflix.spinnaker.q.Activator
import com.netflix.spinnaker.q.DeadMessageCallback
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.migration.SerializationMigrator
import com.netflix.spinnaker.q.redis.AbstractRedisQueue
import com.netflix.spinnaker.q.redis.RedisClusterQueue
import com.netflix.spinnaker.q.redis.RedisQueue
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.springframework.beans.factory.BeanInitializationException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.Protocol
import redis.clients.jedis.util.Pool
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.util.Optional

@Configuration
@ConditionalOnProperty(value = ["queue.redis.enabled"], matchIfMissing = true)
class RedisQueueShovelConfiguration {

  @Bean
  @ConditionalOnProperty("redis.connection-previous")
  @ConditionalOnExpression("\${redis.previous-cluster-enabled:false} == false")
  fun previousQueueJedisPool(
    @Value("\${redis.connection:redis://localhost:6379}") mainConnection: String,
    @Value("\${redis.connection-previous:#{null}}") previousConnection: String?,
    @Value("\${redis.timeout:2000}") timeout: Int,
    redisPoolConfig: GenericObjectPoolConfig<*>,
    registry: Registry
  ): Pool<Jedis> {
    if (mainConnection == previousConnection) {
      throw BeanInitializationException("previous Redis connection must not be the same as current connection")
    }

    return JedisPoolFactory(registry).build(
      "previousQueue",
      JedisDriverProperties().apply {
        connection = previousConnection
        timeoutMs = timeout
        poolConfig = redisPoolConfig
      },
      redisPoolConfig
    )
  }

  @Bean
  @ConditionalOnProperty("redis.connection-previous")
  @ConditionalOnExpression("\${redis.previous-cluster-enabled:false} == true")
  fun previousJedisCluster(
    @Value("\${redis.connection:redis://localhost:6379}") mainConnection: String,
    @Value("\${redis.connection-previous}") previousConnection: String,
    @Value("\${redis.timeout:2000}") timeout: Int,
    @Value("\${redis.maxattempts:4}") maxAttempts: Int,
    redisPoolConfig: GenericObjectPoolConfig<*>,
    registry: Registry
  ): JedisCluster {
    if (mainConnection == previousConnection) {
      throw BeanInitializationException("previous Redis connection must not be the same as current connection")
    }
    URI.create(previousConnection).let { cx ->
      val port = if (cx.port == -1) Protocol.DEFAULT_PORT else cx.port
      val password = cx.userInfo?.substringAfter(":")
      return JedisCluster(
        HostAndPort(cx.host, port),
        timeout,
        timeout,
        maxAttempts,
        password,
        redisPoolConfig
      )
    }
  }

  @Bean(name = ["previousQueue"])
  @ConditionalOnBean(name = ["previousQueueJedisPool"])
  fun previousRedisQueue(
    @Qualifier("previousQueueJedisPool") redisPool: Pool<Jedis>,
    redisQueueProperties: RedisQueueProperties,
    clock: Clock,
    deadMessageHandler: DeadMessageCallback,
    publisher: EventPublisher,
    redisQueueObjectMapper: ObjectMapper,
    serializationMigrator: Optional<SerializationMigrator>
  ) =
    RedisQueue(
      queueName = redisQueueProperties.queueName,
      pool = redisPool,
      clock = clock,
      deadMessageHandlers = listOf(deadMessageHandler),
      publisher = publisher,
      ackTimeout = Duration.ofSeconds(redisQueueProperties.ackTimeoutSeconds.toLong()),
      mapper = redisQueueObjectMapper,
      serializationMigrator = serializationMigrator
    )

  @Bean(name = ["previousClusterQueue"])
  @ConditionalOnBean(name = ["previousJedisCluster"])
  fun previousRedisClusterQueue(
    @Qualifier("previousJedisCluster") cluster: JedisCluster,
    redisQueueProperties: RedisQueueProperties,
    clock: Clock,
    deadMessageHandler: DeadMessageCallback,
    publisher: EventPublisher,
    redisQueueObjectMapper: ObjectMapper,
    serializationMigrator: Optional<SerializationMigrator>
  ) =
    RedisClusterQueue(
      queueName = redisQueueProperties.queueName,
      jedisCluster = cluster,
      clock = clock,
      mapper = redisQueueObjectMapper,
      deadMessageHandlers = listOf(deadMessageHandler),
      publisher = publisher,
      ackTimeout = Duration.ofSeconds(redisQueueProperties.ackTimeoutSeconds.toLong()),
      serializationMigrator = serializationMigrator
    )

  @Bean
  @ConditionalOnBean(name = ["previousQueueJedisPool"])
  fun redisQueueShovel(
    queue: AbstractRedisQueue,
    @Qualifier("previousQueue") previousQueueImpl: RedisQueue,
    registry: Registry,
    discoveryActivator: Activator
  ) =
    QueueShovel(
      queue = queue,
      previousQueue = previousQueueImpl,
      registry = registry,
      activator = discoveryActivator
    )

  @Bean
  @ConditionalOnBean(name = ["previousClusterQueue"])
  fun priorRedisClusterQueueShovel(
    queue: AbstractRedisQueue,
    @Qualifier("previousClusterQueue") previousQueueImpl: AbstractRedisQueue,
    registry: Registry,
    discoveryActivator: Activator
  ) =
    QueueShovel(
      queue = queue,
      previousQueue = previousQueueImpl,
      registry = registry,
      activator = discoveryActivator
    )
}
