package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.events.ResourceEvent.Companion.clock
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier
import com.netflix.spinnaker.keel.scheduled.ScheduledAgent
import com.netflix.spinnaker.keel.sql.SqlAgentLockRepository
import com.netflix.spinnaker.keel.sql.SqlArtifactRepository
import com.netflix.spinnaker.keel.sql.SqlDeliveryConfigRepository
import com.netflix.spinnaker.keel.sql.SqlDiffFingerprintRepository
import com.netflix.spinnaker.keel.sql.SqlPausedRepository
import com.netflix.spinnaker.keel.sql.SqlResourceRepository
import com.netflix.spinnaker.keel.sql.SqlRetry
import com.netflix.spinnaker.keel.sql.SqlTaskTrackingRepository
import com.netflix.spinnaker.keel.sql.SqlUnhappyVetoRepository
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import java.time.Clock
import javax.annotation.PostConstruct
import org.jooq.DSLContext
import org.jooq.impl.DefaultConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import

@Configuration
@ConditionalOnProperty("sql.enabled")
@Import(DefaultSqlConfiguration::class, SqlRetryProperties::class)
class SqlConfiguration {

  @Autowired
  lateinit var jooqConfiguration: DefaultConfiguration

  @Autowired
  lateinit var sqlRetryProperties: SqlRetryProperties

  // This allows us to run tests with a testcontainers database that has a different schema name to
  // the real one used by the JOOQ code generator. It _is_ possible to change the schema used by
  // testcontainers but not when initializing the database with just the JDBC connection string
  // which is super convenient, especially for Spring integration tests.
  @PostConstruct
  fun tweakJooqConfiguration() {
    jooqConfiguration.settings().isRenderSchema = false
  }

  @Bean
  fun resourceRepository(
    jooq: DSLContext,
    clock: Clock,
    resourceTypeIdentifier: ResourceTypeIdentifier,
    objectMapper: ObjectMapper
  ) =
    SqlResourceRepository(jooq, clock, resourceTypeIdentifier, objectMapper, SqlRetry(sqlRetryProperties))

  @Bean
  fun artifactRepository(jooq: DSLContext, clock: Clock, objectMapper: ObjectMapper) =
    SqlArtifactRepository(jooq, clock, objectMapper, SqlRetry(sqlRetryProperties))

  @Bean
  fun deliveryConfigRepository(
    jooq: DSLContext,
    clock: Clock,
    resourceTypeIdentifier: ResourceTypeIdentifier,
    objectMapper: ObjectMapper
  ) =
    SqlDeliveryConfigRepository(jooq, clock, resourceTypeIdentifier, objectMapper, SqlRetry(sqlRetryProperties))

  @Bean
  fun diffFingerprintRepository(
    jooq: DSLContext,
    clock: Clock
  ) = SqlDiffFingerprintRepository(jooq, clock, SqlRetry(sqlRetryProperties))

  @Bean
  fun unhappyVetoRepository(
    jooq: DSLContext
  ) =
    SqlUnhappyVetoRepository(clock, jooq, SqlRetry(sqlRetryProperties))

  @Bean
  fun pausedRepository(
    jooq: DSLContext
  ) = SqlPausedRepository(jooq, SqlRetry(sqlRetryProperties))

  @Bean
  fun taskTrackingRepository(
    jooq: DSLContext,
    clock: Clock
  ) = SqlTaskTrackingRepository(jooq, clock, SqlRetry(sqlRetryProperties))

  @Bean
  fun agentLockRepository(
    jooq: DSLContext,
    clock: Clock,
    properties: SqlProperties,
    agents: List<ScheduledAgent>
  ) = SqlAgentLockRepository(jooq, clock, agents, SqlRetry(sqlRetryProperties))
}
