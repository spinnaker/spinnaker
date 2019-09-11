/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.q.QueueShovel
import com.netflix.spinnaker.q.Activator
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.migration.SerializationMigrator
import com.netflix.spinnaker.q.redis.AbstractRedisQueue
import com.netflix.spinnaker.q.redis.RedisClusterQueue
import com.netflix.spinnaker.q.redis.RedisQueue
import com.netflix.spinnaker.q.sql.SqlQueue
import org.jooq.DSLContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCluster
import redis.clients.util.Pool
import java.time.Clock
import java.util.Optional

@Configuration
@EnableConfigurationProperties(RedisQueueProperties::class, SqlQueueProperties::class)
@ConditionalOnProperty(value = ["queue.shovel.enabled"])
class SqlRedisQueueShovelConfiguration {

  @Bean
  @ConditionalOnBean(SqlQueue::class)
  @ConditionalOnProperty(value = ["redis.cluster-enabled"], havingValue = "false", matchIfMissing = true)
  fun redisToSqlQueueShovel(
    queue: SqlQueue,
    jedisPool: Pool<Jedis>,
    clock: Clock,
    publisher: EventPublisher,
    mapper: ObjectMapper,
    serializationMigrator: Optional<SerializationMigrator>,
    redisQueueProperties: RedisQueueProperties,
    registry: Registry,
    discoveryActivator: Activator
  ): QueueShovel {
    val previousQueue = RedisQueue(
      queueName = redisQueueProperties.queueName,
      pool = jedisPool,
      clock = clock,
      deadMessageHandlers = emptyList(),
      publisher = publisher,
      mapper = mapper,
      serializationMigrator = serializationMigrator)

    return QueueShovel(
      queue = queue,
      previousQueue = previousQueue,
      registry = registry,
      activator = discoveryActivator)
  }

  @Bean
  @ConditionalOnBean(SqlQueue::class)
  @ConditionalOnProperty(value = ["redis.cluster-enabled"], havingValue = "true", matchIfMissing = false)
  fun redisClusterToSqlQueueShovel(
    queue: SqlQueue,
    cluster: JedisCluster,
    clock: Clock,
    publisher: EventPublisher,
    mapper: ObjectMapper,
    serializationMigrator: Optional<SerializationMigrator>,
    redisQueueProperties: RedisQueueProperties,
    registry: Registry,
    discoveryActivator: Activator
  ): QueueShovel {
    val previousQueue = RedisClusterQueue(
      queueName = redisQueueProperties.queueName,
      jedisCluster = cluster,
      clock = clock,
      mapper = mapper,
      serializationMigrator = serializationMigrator,
      deadMessageHandlers = emptyList(),
      publisher = publisher
    )

    return QueueShovel(
      queue = queue,
      previousQueue = previousQueue,
      registry = registry,
      activator = discoveryActivator)
  }

  /**
   * [sqlToRedisQueueShovel] only needs to construct a SqlQueue, so works with both
   * [RedisQueue] or [RedisClusterQueue] primary queues.
   */
  @Bean
  @ConditionalOnBean(AbstractRedisQueue::class)
  fun sqlToRedisQueueShovel(
    queue: AbstractRedisQueue,
    jooq: DSLContext,
    clock: Clock,
    publisher: EventPublisher,
    mapper: ObjectMapper,
    serializationMigrator: Optional<SerializationMigrator>,
    sqlQueueProperties: SqlQueueProperties,
    registry: Registry,
    discoveryActivator: Activator
  ): QueueShovel {
    val previousQueue = SqlQueue(
      queueName = sqlQueueProperties.queueName,
      schemaVersion = 1,
      jooq = jooq,
      clock = clock,
      lockTtlSeconds = sqlQueueProperties.lockTtlSeconds,
      mapper = mapper,
      serializationMigrator = serializationMigrator,
      deadMessageHandlers = emptyList(),
      publisher = publisher,
      sqlRetryProperties = sqlQueueProperties.retries)

    return QueueShovel(
      queue = queue,
      previousQueue = previousQueue,
      registry = registry,
      activator = discoveryActivator)
  }
}
