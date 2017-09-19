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

import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.config.RedisConfiguration
import com.netflix.spinnaker.orca.q.redis.RedisDeadMessageHandler
import com.netflix.spinnaker.orca.q.redis.RedisQueue
import com.netflix.spinnaker.orca.q.QueueShovel
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.springframework.beans.factory.BeanInitializationException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import java.time.Clock
import java.time.Duration

@Configuration
@ConditionalOnExpression("\${queue.redis.enabled:true}")
open class RedisQueueShovelConfiguration {

  @Bean
  @ConditionalOnProperty("redis.connectionPrevious")
  open fun previousQueueJedisPool(
    @Value("\${redis.connection:redis://localhost:6379}") mainConnection: String,
    @Value("\${redis.connectionPrevious:#{null}}") previousConnection: String?,
    @Value("\${redis.timeout:2000}") timeout: Int,
    redisPoolConfig: GenericObjectPoolConfig,
    registry: Registry): Pool<Jedis> {
    if (mainConnection == previousConnection) {
      throw BeanInitializationException("previous Redis connection must not be the same as current connection")
    }

    return RedisConfiguration.createPool(redisPoolConfig, previousConnection, timeout, registry, "previousQueueJedisPool")
  }

  @Bean(name = arrayOf("previousQueueImpl"))
  @ConditionalOnBean(name = arrayOf("previousQueueJedisPool")) open fun previousRedisQueue(
    @Qualifier("previousQueueJedisPool") redisPool: Pool<Jedis>,
    redisQueueProperties: RedisQueueProperties,
    clock: Clock,
    deadMessageHandler: RedisDeadMessageHandler,
    publisher: ApplicationEventPublisher
  ) =
    RedisQueue(
      queueName = redisQueueProperties.queueName,
      pool = redisPool,
      clock = clock,
      deadMessageHandler = deadMessageHandler::handle,
      publisher = publisher,
      ackTimeout = Duration.ofSeconds(redisQueueProperties.ackTimeoutSeconds.toLong())
    )


  @Bean
  @ConditionalOnBean(name = arrayOf("previousQueueJedisPool")) open fun redisQueueShovel(
    @Qualifier("queueImpl") queueImpl: RedisQueue,
    @Qualifier("previousQueueImpl") previousQueueImpl: RedisQueue,
    registry: Registry
  ) =
    QueueShovel(
      queue = queueImpl,
      previousQueue = previousQueueImpl,
      registry = registry
    )
}
