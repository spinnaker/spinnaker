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

package com.netflix.spinnaker.orca.q.sql

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.config.ObjectMapperSubtypeProperties
import com.netflix.spinnaker.config.OrcaSqlProperties
import com.netflix.spinnaker.config.SpringObjectMapperConfigurer
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.kork.jedis.RedisClientDelegate
import com.netflix.spinnaker.kork.jedis.RedisClientSelector
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import com.netflix.spinnaker.orca.TaskResolver
import com.netflix.spinnaker.orca.config.JedisConfiguration
import com.netflix.spinnaker.orca.config.RedisConfiguration
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.QueueIntegrationTest
import com.netflix.spinnaker.orca.q.TestConfig
import com.netflix.spinnaker.orca.q.migration.ExecutionTypeDeserializer
import com.netflix.spinnaker.orca.q.migration.TaskTypeDeserializer
import com.netflix.spinnaker.orca.q.sql.pending.SqlPendingExecutionService
import com.netflix.spinnaker.orca.sql.pipeline.persistence.SqlExecutionRepository
import com.netflix.spinnaker.orca.test.redis.EmbeddedRedisConfiguration
import com.netflix.spinnaker.q.Queue
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.metrics.MonitorableQueue
import com.netflix.spinnaker.q.sql.SqlQueue
import de.huxhorn.sulky.ulid.ULID
import org.jooq.DSLContext
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.test.context.junit4.SpringRunner
import java.time.Clock
import java.time.Duration
import java.util.Optional

@Configuration
class SqlTestConfig {
  @Bean
  fun jooq(): DSLContext {
    val testDatabase = SqlTestUtil.initTcMysqlDatabase()
    return testDatabase.context
  }

  @Autowired
  fun sqlQueueObjectMapper(
    mapper: ObjectMapper,
    objectMapperSubtypeProperties: ObjectMapperSubtypeProperties,
    taskResolver: TaskResolver
  ) {
    mapper.apply {
      registerModule(KotlinModule())
      registerModule(
        SimpleModule()
          .addDeserializer(Execution.ExecutionType::class.java, ExecutionTypeDeserializer())
          .addDeserializer(Class::class.java, TaskTypeDeserializer(taskResolver))
      )
      disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

      SpringObjectMapperConfigurer(objectMapperSubtypeProperties.apply {
        messagePackages = messagePackages + listOf("com.netflix.spinnaker.orca.q")
        attributePackages = attributePackages + listOf("com.netflix.spinnaker.orca.q")
      }).registerSubtypes(this)
    }
  }

  @Bean
  fun queue(
    jooq: DSLContext,
    clock: Clock,
    mapper: ObjectMapper,
    publisher: EventPublisher
  ): MonitorableQueue =
    SqlQueue(
      "test",
      1,
      jooq,
      clock,
      1,
      mapper,
      Optional.empty(),
      Duration.ofSeconds(1),
      emptyList(),
      true,
      publisher,
      SqlRetryProperties(),
      ULID()
    )

  @Bean
  fun sqlExecutionRepository(
    dsl: DSLContext,
    mapper: ObjectMapper,
    registry: Registry,
    properties: SqlProperties,
    orcaSqlProperties: OrcaSqlProperties
  ) = SqlExecutionRepository(
    orcaSqlProperties.partitionName,
    dsl,
    mapper,
    properties.retries.transactions,
    orcaSqlProperties.batchReadSize,
    orcaSqlProperties.stageReadSize,
    interlink = null
  )

  @Bean
  fun pendingExecutionService(
    jooq: DSLContext,
    queue: Queue,
    repository: ExecutionRepository,
    mapper: ObjectMapper,
    clock: Clock,
    registry: Registry
  ) =
    SqlPendingExecutionService("test",
      jooq,
      queue,
      repository,
      mapper,
      clock,
      registry,
      RetryProperties(),
      5
    )

  @Bean
  fun orcaSqlProperties(): OrcaSqlProperties {
    return OrcaSqlProperties()
  }

  // TODO: remove this once Redis is no longer needed for distributed locking
  @Bean
  fun redisClientSelector(redisClientDelegates: List<RedisClientDelegate>) =
    RedisClientSelector(redisClientDelegates)
}

@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [
    SqlTestConfig::class,
    SqlProperties::class,
    TestConfig::class,
    DynamicConfigService.NoopDynamicConfig::class,
    EmbeddedRedisConfiguration::class,
    JedisConfiguration::class,
    RedisConfiguration::class
  ],
  properties = [
    "queue.retry.delay.ms=10",
    "logging.level.root=ERROR",
    "logging.level.org.springframework.test=ERROR",
    "logging.level.com.netflix.spinnaker=FATAL",
    "execution-repository.sql.enabled=true",
    "execution-repository.redis.enabled=false",
    "keiko.queue.redis.enabled=false",
    "keiko.queue.sql.enabled=true",
    "sql.enabled=true",
    "spring.application.name=orcaTest"
  ])

class SqlQueueIntegrationTest : QueueIntegrationTest()
