package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.jsontype.NamedType
import com.netflix.spinnaker.keel.core.api.MANUAL_JUDGEMENT_CONSTRAINT_TYPE
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.persistence.CombinedRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.defaultArtifactSuppliers
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import io.mockk.mockk
import java.time.Clock

internal object SqlCombinedRepositoryTests :
  CombinedRepositoryTests<SqlDeliveryConfigRepository, SqlResourceRepository, SqlArtifactRepository, SqlActionRepository>() {
  private val jooq = testDatabase.context
  private val objectMapper = configuredTestObjectMapper().apply {
    registerSubtypes(NamedType(ManualJudgementConstraint::class.java, MANUAL_JUDGEMENT_CONSTRAINT_TYPE))
  }
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))
  private val clock = Clock.systemUTC()

  override fun createDeliveryConfigRepository(resourceSpecIdentifier: ResourceSpecIdentifier): SqlDeliveryConfigRepository =
    SqlDeliveryConfigRepository(
      jooq,
      clock,
      resourceSpecIdentifier,
      objectMapper,
      sqlRetry,
      defaultArtifactSuppliers(),
      publisher = mockk(relaxed = true)
    )

  override fun createResourceRepository(resourceSpecIdentifier: ResourceSpecIdentifier): SqlResourceRepository =
    SqlResourceRepository(jooq, clock, resourceSpecIdentifier, emptyList(), objectMapper, sqlRetry, publisher = mockk(relaxed = true))

  override fun createArtifactRepository(): SqlArtifactRepository =
    SqlArtifactRepository(jooq, clock, objectMapper, sqlRetry, defaultArtifactSuppliers(), publisher = mockk(relaxed = true))

  override fun createVerificationRepository(resourceSpecIdentifier: ResourceSpecIdentifier): SqlActionRepository =
    SqlActionRepository(jooq, clock, resourceSpecIdentifier, objectMapper, sqlRetry, environment = mockk())

  override fun flush() {
    SqlTestUtil.cleanupDb(jooq)
  }
}
