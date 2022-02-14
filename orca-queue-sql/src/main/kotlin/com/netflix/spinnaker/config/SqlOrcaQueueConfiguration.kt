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

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.netflix.spinnaker.orca.TaskResolver
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionType
import com.netflix.spinnaker.orca.q.migration.ExecutionTypeDeserializer
import com.netflix.spinnaker.orca.q.migration.TaskTypeDeserializer
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.migration.SerializationMigrator
import com.netflix.spinnaker.q.sql.SqlDeadMessageHandler
import com.netflix.spinnaker.q.sql.SqlQueue
import java.time.Clock
import java.util.Optional
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(ObjectMapperSubtypeProperties::class)
@ConditionalOnProperty(
  value = ["keiko.queue.sql.enabled"],
  havingValue = "true",
  matchIfMissing = false
)
class SqlOrcaQueueConfiguration : SqlQueueConfiguration() {

  @Autowired
  fun sqlQueueObjectMapper(
    mapper: ObjectMapper,
    objectMapperSubtypeProperties: ObjectMapperSubtypeProperties,
    taskResolver: TaskResolver
  ) {
    mapper.apply {
      registerModule(KotlinModule.Builder().build())
      registerModule(
        SimpleModule()
          .addDeserializer(ExecutionType::class.java, ExecutionTypeDeserializer())
          .addDeserializer(Class::class.java, TaskTypeDeserializer(taskResolver))
      )
      disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)

      SpringObjectMapperConfigurer(
        objectMapperSubtypeProperties.apply {
          messagePackages = messagePackages + listOf("com.netflix.spinnaker.orca.q")
          attributePackages = attributePackages + listOf("com.netflix.spinnaker.orca.q")
        }
      ).registerSubtypes(this)
    }
  }

  @Bean
  override fun queue(
    jooq: DSLContext,
    clock: Clock,
    mapper: ObjectMapper,
    deadMessageHandler: SqlDeadMessageHandler,
    publisher: EventPublisher,
    serializationMigrator: Optional<SerializationMigrator>,
    properties: SqlQueueProperties
  ) =
    SqlQueue(
      queueName = properties.queueName,
      schemaVersion = 1,
      jooq = jooq,
      clock = clock,
      lockTtlSeconds = properties.lockTtlSeconds,
      mapper = mapper,
      serializationMigrator = serializationMigrator,
      ackTimeout = properties.ackTimeout,
      deadMessageHandlers = listOf(deadMessageHandler),
      publisher = publisher,
      sqlRetryProperties = properties.retries
    )
}
