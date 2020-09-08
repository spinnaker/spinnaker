package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.q.metrics.EventPublisher
import com.netflix.spinnaker.q.migration.SerializationMigrator
import com.netflix.spinnaker.q.sql.SqlDeadMessageHandler
import com.netflix.spinnaker.q.sql.SqlQueue
import java.time.Clock
import java.util.Optional
import org.jooq.DSLContext
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(SqlQueueProperties::class)
@ConditionalOnProperty(
  value = ["keiko.queue.sql.enabled"],
  havingValue = "true",
  matchIfMissing = false
)
class SqlQueueConfiguration {

  companion object {
    const val SCHEMA_VERSION = 1
  }

  @Bean
  @ConditionalOnMissingBean(name = ["queue"])
  fun queue(
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
      schemaVersion = SCHEMA_VERSION,
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

  @Bean
  @ConditionalOnMissingBean(name = ["sqlDeadMessageHandler"])
  fun sqlDeadMessageHandler(
    jooq: DSLContext,
    clock: Clock,
    properties: SqlQueueProperties
  ) =
    SqlDeadMessageHandler(
      deadLetterQueueName = properties.deadLetterQueueName,
      schemaVersion = SCHEMA_VERSION,
      jooq = jooq,
      clock = clock,
      sqlRetryProperties = properties.retries
    )
}
