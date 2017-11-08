/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
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
import com.netflix.spinnaker.orca.q.redis.migration.QueueDataMigrator
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.DependsOn
import redis.clients.jedis.Jedis
import redis.clients.util.Pool
import java.time.Clock
import java.time.Duration

@Configuration
@ConditionalOnExpression("\${queue.redis.enabled:true}")
@EnableConfigurationProperties(RedisQueueProperties::class)
open class RedisQueueConfiguration {
  @Bean(name = arrayOf("queueJedisPool")) open fun queueJedisPool(
      @Value("\${redis.connection:redis://localhost:6379}") connection: String,
      @Value("\${redis.timeout:2000}") timeout: Int,
      redisPoolConfig: GenericObjectPoolConfig,
      registry: Registry
  ) = RedisConfiguration.createPool(redisPoolConfig, connection, timeout, registry, "queueJedisPool")

  @Bean() open fun redisDeadMessageHandler(
    @Qualifier("queueJedisPool") redisPool: Pool<Jedis>,
    redisQueueProperties: RedisQueueProperties,
    clock: Clock
  ) =
    RedisDeadMessageHandler(
      deadLetterQueueName = redisQueueProperties.deadLetterQueueName,
      pool = redisPool,
      clock = clock
    )

  @Bean
  open fun redisDataMigrator(
    @Qualifier("queueJedisPool") redisPool: Pool<Jedis>,
    redisQueueProperties: RedisQueueProperties
  ) = QueueDataMigrator(redisPool, redisQueueProperties)

  @Bean(name = arrayOf("queueImpl"))
  @DependsOn("redisDataMigrator")
  open fun redisQueue(
    @Qualifier("queueJedisPool") redisPool: Pool<Jedis>,
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

}
