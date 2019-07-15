package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.sql.pending.SqlPendingExecutionService
import com.netflix.spinnaker.q.Queue
import org.jooq.DSLContext
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
@EnableConfigurationProperties(SqlProperties::class)
class SqlPendingExecutionConfiguration {

  @Bean
  @ConditionalOnProperty(value = ["queue.pending-execution-service.sql.enabled"])
  fun sqlPendingExecutionService(
    jooq: DSLContext,
    queue: Queue,
    repository: ExecutionRepository,
    @Qualifier("redisQueueObjectMapper") mapper: ObjectMapper,
    clock: Clock,
    registry: Registry,
    properties: SqlProperties,
    @Value("\${queue.pending.max-depth:50}") maxDepth: Int
  ) =
    SqlPendingExecutionService(
      properties.partitionName,
      jooq,
      queue,
      repository,
      mapper,
      clock,
      registry,
      properties.transactionRetry,
      maxDepth
    )
}
