package com.netflix.spinnaker.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.keel.api.plugins.ArtifactSupplier
import com.netflix.spinnaker.keel.events.PersistentEvent.Companion.clock
import com.netflix.spinnaker.keel.resources.ResourceFactory
import com.netflix.spinnaker.keel.scheduled.ScheduledAgent
import com.netflix.spinnaker.keel.sql.SqlActionRepository
import com.netflix.spinnaker.keel.sql.SqlAgentLockRepository
import com.netflix.spinnaker.keel.sql.SqlArtifactRepository
import com.netflix.spinnaker.keel.sql.SqlBakedImageRepository
import com.netflix.spinnaker.keel.sql.SqlDeliveryConfigRepository
import com.netflix.spinnaker.keel.sql.SqlDiffFingerprintRepository
import com.netflix.spinnaker.keel.sql.SqlDismissibleNotificationRepository
import com.netflix.spinnaker.keel.sql.SqlEnvironmentDeletionRepository
import com.netflix.spinnaker.keel.sql.SqlEnvironmentLeaseRepository
import com.netflix.spinnaker.keel.sql.SqlFeatureRolloutRepository
import com.netflix.spinnaker.keel.sql.SqlLifecycleEventRepository
import com.netflix.spinnaker.keel.sql.SqlLifecycleMonitorRepository
import com.netflix.spinnaker.keel.sql.SqlNotificationRepository
import com.netflix.spinnaker.keel.sql.SqlPausedRepository
import com.netflix.spinnaker.keel.sql.SqlResourceRepository
import com.netflix.spinnaker.keel.sql.SqlRetry
import com.netflix.spinnaker.keel.sql.SqlTaskTrackingRepository
import com.netflix.spinnaker.keel.sql.SqlUnhappyVetoRepository
import com.netflix.spinnaker.keel.sql.SqlUnhealthyRepository
import com.netflix.spinnaker.keel.sql.SqlWorkQueueRepository
import com.netflix.spinnaker.kork.sql.config.DefaultSqlConfiguration
import com.netflix.spinnaker.kork.sql.config.SqlProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import org.jooq.DSLContext
import org.jooq.impl.DefaultConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Import
import org.springframework.core.env.Environment
import java.time.Clock
import javax.annotation.PostConstruct

@Configuration
@ConditionalOnProperty("sql.enabled")
@EnableConfigurationProperties(RetentionProperties::class)
@Import(DefaultSqlConfiguration::class, SqlRetryProperties::class, EnvironmentExclusionConfig::class)
class SqlConfiguration
{

  @Autowired
  lateinit var jooqConfiguration: DefaultConfiguration

  @Autowired
  lateinit var sqlRetryProperties: SqlRetryProperties

  @Autowired
  lateinit var environmentExclusionConfig: EnvironmentExclusionConfig

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
    resourceFactory: ResourceFactory,
    objectMapper: ObjectMapper,
    publisher: ApplicationEventPublisher,
    registry: Registry,
    springEnv: Environment
  ) =
    SqlResourceRepository(
      jooq,
      clock,
      objectMapper,
      resourceFactory,
      SqlRetry(sqlRetryProperties),
      publisher,
      registry,
      springEnv
    )

  @Bean
  fun artifactRepository(
    jooq: DSLContext,
    clock: Clock,
    objectMapper: ObjectMapper,
    artifactSuppliers: List<ArtifactSupplier<*, *>>,
    publisher: ApplicationEventPublisher
  ) =
    SqlArtifactRepository(
      jooq,
      clock,
      objectMapper,
      SqlRetry(sqlRetryProperties),
      artifactSuppliers,
      publisher
    )

  @Bean
  fun deliveryConfigRepository(
    jooq: DSLContext,
    clock: Clock,
    resourceFactory: ResourceFactory,
    objectMapper: ObjectMapper,
    artifactSuppliers: List<ArtifactSupplier<*, *>>,
    publisher: ApplicationEventPublisher
  ) =
    SqlDeliveryConfigRepository(
      jooq = jooq,
      clock = clock,
      resourceFactory = resourceFactory,
      objectMapper = objectMapper,
      sqlRetry = SqlRetry(sqlRetryProperties),
      artifactSuppliers = artifactSuppliers,
      publisher = publisher
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
    clock: Clock,
    retentionProperties: RetentionProperties
  ) = SqlTaskTrackingRepository(jooq, clock, SqlRetry(sqlRetryProperties), retentionProperties)

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
  fun actionRepository(
    jooq: DSLContext,
    clock: Clock,
    resourceFactory: ResourceFactory,
    objectMapper: ObjectMapper,
    artifactSuppliers: List<ArtifactSupplier<*, *>>,
    environment: Environment
  ) = SqlActionRepository(
    jooq,
    clock,
    objectMapper,
    resourceFactory,
    SqlRetry(sqlRetryProperties),
    artifactSuppliers,
    environment
  )

  @Bean
  fun lifecycleEventRepository(
    jooq: DSLContext,
    clock: Clock,
    properties: SqlProperties,
    objectMapper: ObjectMapper,
    spectator: Registry,
    publisher: ApplicationEventPublisher
  ) =
    SqlLifecycleEventRepository(clock, jooq, SqlRetry(sqlRetryProperties), spectator, publisher)

  @Bean
  fun lifecycleMonitorRepository(
    jooq: DSLContext,
    clock: Clock,
    properties: SqlProperties,
    objectMapper: ObjectMapper
  ) = SqlLifecycleMonitorRepository(jooq, clock, objectMapper, SqlRetry(sqlRetryProperties))

  @Bean
  fun bakedImageRepository(
    jooq: DSLContext,
    clock: Clock,
    properties: SqlProperties,
    objectMapper: ObjectMapper
  ) = SqlBakedImageRepository(jooq, clock, objectMapper, SqlRetry(sqlRetryProperties))

  @Bean
  fun environmentLeaseRepository(
    jooq: DSLContext,
    clock: Clock,
    registry: Registry
  ) = SqlEnvironmentLeaseRepository(jooq, clock, registry, environmentExclusionConfig.leaseDuration)

  @Bean
  fun dismissibleNotificationRepository(
    jooq: DSLContext,
    clock: Clock,
    objectMapper: ObjectMapper
  ) = SqlDismissibleNotificationRepository(jooq, SqlRetry(sqlRetryProperties), objectMapper, clock)

  @Bean
  fun environmentDeletionRepository(
    jooq: DSLContext,
    clock: Clock,
    resourceFactory: ResourceFactory,
    objectMapper: ObjectMapper,
    artifactSuppliers: List<ArtifactSupplier<*, *>>
  ) = SqlEnvironmentDeletionRepository(
    jooq,
    clock,
    objectMapper,
    SqlRetry(sqlRetryProperties),
    resourceFactory,
    artifactSuppliers
  )

  @Bean
  fun artifactProcessingRepository(
    jooq: DSLContext,
    clock: Clock,
    objectMapper: ObjectMapper
  ) = SqlWorkQueueRepository(jooq, clock, objectMapper, SqlRetry(sqlRetryProperties))

  @Bean
  fun featureRolloutRepository(
    jooq: DSLContext,
    clock: Clock
  ) = SqlFeatureRolloutRepository(jooq, SqlRetry(sqlRetryProperties), clock)
}
