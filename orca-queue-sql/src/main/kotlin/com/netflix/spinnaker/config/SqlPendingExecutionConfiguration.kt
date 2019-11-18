package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import com.netflix.spinnaker.orca.pipeline.persistence.ExecutionRepository
import com.netflix.spinnaker.orca.q.sql.pending.SqlPendingExecutionService
import com.netflix.spinnaker.q.Queue
import org.jooq.DSLContext
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
    mapper: ObjectMapper,
    clock: Clock,
    registry: Registry,
    sqlProperties: SqlProperties,
    orcaSqlProperties: OrcaSqlProperties,
    sqlPendingProperties: SqlPendingExecutionProperties
  ) =
    SqlPendingExecutionService(
      orcaSqlProperties.partitionName,
      jooq,
      queue,
      repository,
      mapper,
      clock,
      registry,
      sqlProperties.retries.transactions,
      sqlPendingProperties.maxDepth
    )
}
