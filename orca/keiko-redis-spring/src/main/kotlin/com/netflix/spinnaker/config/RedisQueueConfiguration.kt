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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.migration.SerializationMigrator
import com.netflix.spinnaker.q.redis.RedisClusterDeadMessageHandler
import com.netflix.spinnaker.q.redis.RedisClusterQueue
import com.netflix.spinnaker.q.redis.RedisDeadMessageHandler
import com.netflix.spinnaker.q.redis.RedisQueue
import java.net.URI
import java.time.Clock
import java.time.Duration
import java.util.Optional
import org.apache.commons.pool2.impl.GenericObjectPoolConfig
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import redis.clients.jedis.Connection
import redis.clients.jedis.HostAndPort
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.JedisPool
import redis.clients.jedis.Protocol
import redis.clients.jedis.util.Pool

@Configuration
@EnableConfigurationProperties(RedisQueueProperties::class)
@ConditionalOnProperty(
  value = ["keiko.queue.redis.enabled"],
  havingValue = "true",
  matchIfMissing = true
)
class RedisQueueConfiguration {

  @Bean
  @ConditionalOnMissingBean(GenericObjectPoolConfig::class)
  fun redisPoolConfig() = GenericObjectPoolConfig<Jedis>().apply {
    blockWhenExhausted = false
    maxWaitMillis = 2000
  }

  @Bean
  @ConditionalOnMissingBean(name = ["queueRedisPool"])
  @ConditionalOnProperty(
    value = ["redis.cluster-enabled"],
    havingValue = "false",
    matchIfMissing = true
  )
  fun queueRedisPool(
    @Value("\${redis.connection:redis://localhost:6379}") connection: String,
    @Value("\${redis.timeout:2000}") timeout: Int,
    redisPoolConfig: GenericObjectPoolConfig<Jedis>
  ) =
    URI.create(connection).let { cx ->
      val port = if (cx.port == -1) Protocol.DEFAULT_PORT else cx.port
      val db = if (cx.path.isNullOrEmpty()) {
        Protocol.DEFAULT_DATABASE
      } else {
        cx.path.substringAfter("/").toInt()
      }
      val password = cx.userInfo?.substringAfter(":")

      val isSSL = cx.scheme == "rediss"

      JedisPool(redisPoolConfig, cx.host, port, timeout, password, db, isSSL)
    }

  @Bean
  @ConditionalOnMissingBean(name = ["queue"])
  @ConditionalOnProperty(
    value = ["redis.cluster-enabled"],
    havingValue = "false",
    matchIfMissing = true
  )
  fun queue(
    @Qualifier("queueRedisPool") redisPool: Pool<Jedis>,
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
      mapper = redisQueueObjectMapper,
      deadMessageHandlers = listOf(deadMessageHandler),
      publisher = publisher,
      ackTimeout = Duration.ofSeconds(redisQueueProperties.ackTimeoutSeconds.toLong()),
      serializationMigrator = serializationMigrator
    )

  @Bean
  @ConditionalOnMissingBean(name = ["redisDeadMessageHandler"])
  @ConditionalOnProperty(
    value = ["redis.cluster-enabled"],
    havingValue = "false",
    matchIfMissing = true
  )
  fun redisDeadMessageHandler(
    @Qualifier("queueRedisPool") redisPool: Pool<Jedis>,
    redisQueueProperties: RedisQueueProperties,
    clock: Clock
  ) =
    RedisDeadMessageHandler(
      deadLetterQueueName = redisQueueProperties.deadLetterQueueName,
      pool = redisPool,
      clock = clock
    )

  @Bean
  @ConditionalOnMissingBean(name = ["queueRedisCluster"])
  @ConditionalOnProperty(value = ["redis.cluster-enabled"])
  fun queueRedisCluster(
    @Value("\${redis.connection:redis://localhost:6379}") connection: String,
    @Value("\${redis.timeout:2000}") timeout: Int,
    @Value("\${redis.maxattempts:4}") maxAttempts: Int,
    redisPoolConfig: GenericObjectPoolConfig<Connection>
  ): JedisCluster {
    URI.create(connection).let { cx ->
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

  @Bean
  @ConditionalOnMissingBean(name = ["queue", "clusterQueue"])
  @ConditionalOnProperty(value = ["redis.cluster-enabled"])
  fun clusterQueue(
    @Qualifier("queueRedisCluster") cluster: JedisCluster,
    redisQueueProperties: RedisQueueProperties,
    clock: Clock,
    deadMessageHandler: RedisClusterDeadMessageHandler,
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
  @ConditionalOnMissingBean(name = ["redisClusterDeadMessageHandler"])
  @ConditionalOnProperty(value = ["redis.cluster-enabled"])
  fun redisClusterDeadMessageHandler(
    @Qualifier("queueRedisCluster") cluster: JedisCluster,
    redisQueueProperties: RedisQueueProperties,
    clock: Clock
  ) = RedisClusterDeadMessageHandler(
    deadLetterQueueName = redisQueueProperties.deadLetterQueueName,
    jedisCluster = cluster,
    clock = clock
  )

  @Bean
  @ConditionalOnMissingBean
  fun redisQueueObjectMapper(properties: Optional<ObjectMapperSubtypeProperties>): ObjectMapper =
    ObjectMapper().apply {
      registerModule(KotlinModule.Builder().build())
      disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

      SpringObjectMapperConfigurer(
        properties.orElse(ObjectMapperSubtypeProperties())
      ).registerSubtypes(this)
    }
}
