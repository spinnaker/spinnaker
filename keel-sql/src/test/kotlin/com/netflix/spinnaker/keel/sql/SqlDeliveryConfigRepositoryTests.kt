package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.jsontype.NamedType
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.defaultArtifactSuppliers
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import org.junit.jupiter.api.BeforeAll
import org.springframework.context.ApplicationEventPublisher
import java.time.Clock

internal object SqlDeliveryConfigRepositoryTests : DeliveryConfigRepositoryTests<SqlDeliveryConfigRepository, SqlResourceRepository, SqlArtifactRepository, SqlPausedRepository>() {
  private val jooq = testDatabase.context
  private val objectMapper = configuredTestObjectMapper()
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override fun createDeliveryConfigRepository(resourceSpecIdentifier: ResourceSpecIdentifier, publisher: ApplicationEventPublisher): SqlDeliveryConfigRepository =
    SqlDeliveryConfigRepository(jooq, Clock.systemUTC(), resourceSpecIdentifier, objectMapper, sqlRetry, defaultArtifactSuppliers(), publisher = publisher)

  override fun createResourceRepository(resourceSpecIdentifier: ResourceSpecIdentifier, publisher: ApplicationEventPublisher): SqlResourceRepository =
    SqlResourceRepository(jooq, Clock.systemUTC(), resourceSpecIdentifier, emptyList(), objectMapper, sqlRetry, publisher)

  override fun createArtifactRepository(publisher: ApplicationEventPublisher): SqlArtifactRepository =
    SqlArtifactRepository(jooq, Clock.systemUTC(), objectMapper, sqlRetry, defaultArtifactSuppliers(), publisher = publisher)

  override fun createPausedRepository(): SqlPausedRepository =
    SqlPausedRepository(jooq, sqlRetry, Clock.systemUTC())

  override fun flush() {
    cleanupDb(jooq)
  }

  @JvmStatic
  @BeforeAll
  fun registerConstraintSubtypes() {
    with(objectMapper) {
      registerSubtypes(NamedType(DependsOnConstraint::class.java, "depends-on"))
      registerSubtypes(NamedType(ManualJudgementConstraint::class.java, "manual-judgement"))
      registerSubtypes(NamedType(DummyVerification::class.java, "verification"))
    }
  }
}
