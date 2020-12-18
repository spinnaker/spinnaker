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
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.time.Clock

internal object SqlDeliveryConfigRepositoryTests : DeliveryConfigRepositoryTests<SqlDeliveryConfigRepository, SqlResourceRepository, SqlArtifactRepository, SqlPausedRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val objectMapper = configuredTestObjectMapper()
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override fun createDeliveryConfigRepository(resourceSpecIdentifier: ResourceSpecIdentifier): SqlDeliveryConfigRepository =
    SqlDeliveryConfigRepository(jooq, Clock.systemUTC(), resourceSpecIdentifier, objectMapper, sqlRetry, defaultArtifactSuppliers())

  override fun createResourceRepository(resourceSpecIdentifier: ResourceSpecIdentifier): SqlResourceRepository =
    SqlResourceRepository(jooq, Clock.systemUTC(), resourceSpecIdentifier, emptyList(), objectMapper, sqlRetry)

  override fun createArtifactRepository(): SqlArtifactRepository =
    SqlArtifactRepository(jooq, Clock.systemUTC(), objectMapper, sqlRetry, defaultArtifactSuppliers())

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

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }
}
