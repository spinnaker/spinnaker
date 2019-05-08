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
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.migration.SerializationMigrator
import com.netflix.spinnaker.q.redis.RedisDeadMessageHandler
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
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import java.time.Clock
import java.time.Duration
import java.util.Optional

@Configuration
@ConditionalOnExpression("\${queue.redis.enabled:true}")
class RedisQueueShovelConfiguration {

  @Bean
  @ConditionalOnProperty("redis.connection-previous")
  fun previousQueueJedisPool(
    @Value("\${redis.connection:redis://localhost:6379}") mainConnection: String,
    @Value("\${redis.connection-previous:#{null}}") previousConnection: String?,
    @Value("\${redis.timeout:2000}") timeout: Int,
    redisPoolConfig: GenericObjectPoolConfig<*>,
    registry: Registry): Pool<Jedis> {
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

  @Bean(name = ["previousQueue"])
  @ConditionalOnBean(name = ["previousQueueJedisPool"]) fun previousRedisQueue(
    @Qualifier("previousQueueJedisPool") redisPool: Pool<Jedis>,
    redisQueueProperties: RedisQueueProperties,
    clock: Clock,
    deadMessageHandler: RedisDeadMessageHandler,
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


  @Bean
  @ConditionalOnBean(name = arrayOf("previousQueueJedisPool")) fun redisQueueShovel(
    queue: RedisQueue,
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
}
