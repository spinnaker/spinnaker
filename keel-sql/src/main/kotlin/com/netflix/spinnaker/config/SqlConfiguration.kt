package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.events.PersistentEvent.Companion.clock
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.resources.SpecMigrator
import com.netflix.spinnaker.keel.scheduled.ScheduledAgent
import com.netflix.spinnaker.keel.sql.SqlAgentLockRepository
import com.netflix.spinnaker.keel.sql.SqlArtifactRepository
import com.netflix.spinnaker.keel.sql.SqlDeliveryConfigRepository
import com.netflix.spinnaker.keel.sql.SqlDiffFingerprintRepository
import com.netflix.spinnaker.keel.sql.SqlLifecycleEventRepository
import com.netflix.spinnaker.keel.sql.SqlLifecycleMonitorRepository
import com.netflix.spinnaker.keel.sql.SqlNotificationRepository
import com.netflix.spinnaker.keel.sql.SqlPausedRepository
import com.netflix.spinnaker.keel.sql.SqlResourceRepository
import com.netflix.spinnaker.keel.sql.SqlRetry
import com.netflix.spinnaker.keel.sql.SqlTaskTrackingRepository
import com.netflix.spinnaker.keel.sql.SqlUnhappyVetoRepository
import com.netflix.spinnaker.keel.sql.SqlUnhealthyRepository
import com.netflix.spinnaker.keel.sql.SqlVerificationRepository
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import org.jooq.DSLContext
import org.jooq.impl.DefaultConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import java.time.Clock
import javax.annotation.PostConstruct

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
    resourceSpecIdentifier: ResourceSpecIdentifier,
    specMigrators: List<SpecMigrator<*, *>>,
    objectMapper: ObjectMapper
  ) =
    SqlResourceRepository(
      jooq,
      clock,
      resourceSpecIdentifier,
      specMigrators,
      objectMapper,
      SqlRetry(sqlRetryProperties)
    )

  @Bean
  fun artifactRepository(
    jooq: DSLContext,
    clock: Clock,
    objectMapper: ObjectMapper,
    artifactSuppliers: List<ArtifactSupplier<*, *>>
  ) =
    SqlArtifactRepository(
      jooq,
      clock,
      objectMapper,
      SqlRetry(sqlRetryProperties),
      artifactSuppliers
    )

  @Bean
  fun deliveryConfigRepository(
    jooq: DSLContext,
    clock: Clock,
    resourceSpecIdentifier: ResourceSpecIdentifier,
    objectMapper: ObjectMapper,
    artifactSuppliers: List<ArtifactSupplier<*, *>>,
    specMigrators: List<SpecMigrator<*, *>>
  ) =
    SqlDeliveryConfigRepository(
      jooq = jooq,
      clock = clock,
      resourceSpecIdentifier = resourceSpecIdentifier,
      objectMapper = objectMapper,
      sqlRetry = SqlRetry(sqlRetryProperties),
      artifactSuppliers = artifactSuppliers,
      specMigrators = specMigrators
    )

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
  ) = SqlPausedRepository(jooq, SqlRetry(sqlRetryProperties), clock)

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

  @Bean
  fun notificationRepository(
    jooq: DSLContext,
    clock: Clock,
    properties: SqlProperties
  ) = SqlNotificationRepository(clock, jooq, SqlRetry(sqlRetryProperties))

  @Bean
  fun unhealthyRepository(
    jooq: DSLContext,
    clock: Clock,
    properties: SqlProperties
  ) = SqlUnhealthyRepository(clock, jooq, SqlRetry(sqlRetryProperties))

  @Bean
  fun verificationRepository(
    jooq: DSLContext,
    clock: Clock,
    resourceSpecIdentifier: ResourceSpecIdentifier,
    objectMapper: ObjectMapper,
    artifactSuppliers: List<ArtifactSupplier<*, *>>,
    specMigrators: List<SpecMigrator<*, *>>
  ) = SqlVerificationRepository(
    jooq,
    clock,
    resourceSpecIdentifier,
    objectMapper,
    SqlRetry(sqlRetryProperties),
    artifactSuppliers,
    specMigrators
  )

  @Bean
  fun lifecycleEventRepository(
    jooq: DSLContext,
    clock: Clock,
    properties: SqlProperties,
    objectMapper: ObjectMapper,
    spectator: Registry
  ) =
    SqlLifecycleEventRepository(clock, jooq, SqlRetry(sqlRetryProperties), objectMapper, spectator)

  @Bean
  fun lifecycleMonitorRepository(
    jooq: DSLContext,
    clock: Clock,
    properties: SqlProperties,
    objectMapper: ObjectMapper
  ) = SqlLifecycleMonitorRepository(jooq, clock, objectMapper, SqlRetry(sqlRetryProperties))
}
