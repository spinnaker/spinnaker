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

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.orca.TaskResolver
import com.netflix.spinnaker.orca.pipeline.model.Execution.ExecutionType
import com.netflix.spinnaker.orca.q.migration.ExecutionTypeDeserializer
import com.netflix.spinnaker.orca.q.migration.OrcaToKeikoSerializationMigrator
import com.netflix.spinnaker.orca.q.migration.TaskTypeDeserializer
import com.netflix.spinnaker.orca.q.redis.pending.RedisPendingExecutionService
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.migration.SerializationMigrator
import com.netflix.spinnaker.q.redis.RedisClusterDeadMessageHandler
import com.netflix.spinnaker.q.redis.RedisClusterQueue
import com.netflix.spinnaker.q.redis.RedisDeadMessageHandler
import com.netflix.spinnaker.q.redis.RedisQueue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisCluster
import redis.clients.jedis.util.Pool
import java.time.Clock
import java.util.Optional

@Configuration
@EnableConfigurationProperties(ObjectMapperSubtypeProperties::class)
@ConditionalOnProperty(
  value = ["keiko.queue.redis.enabled"],
  havingValue = "true",
  matchIfMissing = true)
class RedisOrcaQueueConfiguration : RedisQueueConfiguration() {

  @Autowired
  fun redisQueueObjectMapper(
    mapper: ObjectMapper,
    objectMapperSubtypeProperties: ObjectMapperSubtypeProperties,
    taskResolver: TaskResolver
  ) {
    mapper.apply {
      registerModule(KotlinModule())
      registerModule(
        SimpleModule()
          .addDeserializer(ExecutionType::class.java, ExecutionTypeDeserializer())
          .addDeserializer(Class::class.java, TaskTypeDeserializer(taskResolver))
      )
      disable(FAIL_ON_UNKNOWN_PROPERTIES)

      SpringObjectMapperConfigurer(objectMapperSubtypeProperties.apply {
        messagePackages += listOf("com.netflix.spinnaker.orca.q")
        attributePackages += listOf("com.netflix.spinnaker.orca.q")
      }).registerSubtypes(this)
    }
  }

  @Bean
  fun orcaToKeikoSerializationMigrator(objectMapper: ObjectMapper) = OrcaToKeikoSerializationMigrator(objectMapper)

  @Bean
  @ConditionalOnProperty(value = ["redis.cluster-enabled"], havingValue = "false", matchIfMissing = true)
  @Primary
  override fun queue(
    @Qualifier("queueRedisPool") redisPool: Pool<Jedis>,
    redisQueueProperties: RedisQueueProperties,
    clock: Clock,
    deadMessageHandler: RedisDeadMessageHandler,
    publisher: EventPublisher,
    mapper: ObjectMapper,
    serializationMigrator: Optional<SerializationMigrator>
  ): RedisQueue {
    return super.queue(redisPool, redisQueueProperties, clock, deadMessageHandler, publisher, mapper, serializationMigrator)
  }

  @Bean
  @ConditionalOnProperty(value = ["redis.cluster-enabled"])
  @Primary
  override fun clusterQueue(
    @Qualifier("queueRedisCluster") cluster: JedisCluster,
    redisQueueProperties: RedisQueueProperties,
    clock: Clock,
    deadMessageHandler: RedisClusterDeadMessageHandler,
    publisher: EventPublisher,
    redisQueueObjectMapper: ObjectMapper,
    serializationMigrator: Optional<SerializationMigrator>
  ): RedisClusterQueue {
    return super.clusterQueue(
      cluster,
      redisQueueProperties,
      clock,
      deadMessageHandler,
      publisher,
      redisQueueObjectMapper,
      serializationMigrator
    )
  }

  @Bean
  @ConditionalOnProperty(value = ["queue.pending-execution-service.redis.enabled"], matchIfMissing = true)
  fun pendingExecutionService(
    @Qualifier("queueRedisPool") jedisPool: Pool<Jedis>,
    mapper: ObjectMapper
  ) =
    RedisPendingExecutionService(jedisPool, mapper)
}
