/*
 * Copyright 2020 Netflix, Inc.
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
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.netflix.spinnaker.orca.q.QueueShovel
import com.netflix.spinnaker.q.Activator
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.migration.SerializationMigrator
import com.netflix.spinnaker.q.sql.SqlDeadMessageHandler
import com.netflix.spinnaker.q.sql.SqlQueue
import java.time.Clock
import java.util.Optional
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

const val SOURCE_POOL_NAME_PROPERTY = "queue.shovel.source-pool-name"

@Configuration
@EnableConfigurationProperties(SqlQueueProperties::class)
@ConditionalOnProperty(value = ["queue.shovel.enabled"])
class SqlQueueShovelConfiguration {

  @Bean(name = ["previousSqlQueue"])
  @ConditionalOnProperty(value = ["queue.shovel.kind"], havingValue = "sql-to-sql")
  fun previousSqlQueue(
    jooq: DSLContext,
    clock: Clock,
    mapper: ObjectMapper,
    deadMessageHandler: SqlDeadMessageHandler,
    publisher: EventPublisher,
    serializationMigrator: Optional<SerializationMigrator>,
    properties: SqlQueueProperties,
    @Value("\${$SOURCE_POOL_NAME_PROPERTY}") poolName: String
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
      sqlRetryProperties = properties.retries,
      poolName = poolName
    )

  @Bean
  @ConditionalOnProperty(value = ["queue.shovel.kind"], havingValue = "sql-to-sql")
  fun sqlToSqlQueueShovel(
    queue: SqlQueue,
    @Qualifier("previousSqlQueue") previousQueue: SqlQueue,
    registry: Registry,
    @Qualifier("discoveryActivator") activator: Activator,
    config: DynamicConfigService
  ): QueueShovel {
    return QueueShovel(
      queue = queue,
      previousQueue = previousQueue,
      registry = registry,
      activator = activator,
      config = config)
  }
}
