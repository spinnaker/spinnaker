package com.netflix.spinnaker.keel.sql

import com.fasterxml.jackson.databind.jsontype.NamedType
import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.keel.core.api.MANUAL_JUDGEMENT_CONSTRAINT_TYPE
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.persistence.CombinedRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceFactory
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.defaultArtifactSuppliers
import com.netflix.spinnaker.keel.test.mockEnvironment
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
    registerSubtypes(NamedType(DummyVerification::class.java, DummyVerification.TYPE))
  }
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))
  private val clock = Clock.systemUTC()

  override fun createDeliveryConfigRepository(resourceFactory: ResourceFactory): SqlDeliveryConfigRepository =
    SqlDeliveryConfigRepository(
      jooq,
      clock,
      objectMapper,
      resourceFactory,
      sqlRetry,
      defaultArtifactSuppliers(),
      publisher = mockk(relaxed = true))

  override fun createResourceRepository(resourceFactory: ResourceFactory): SqlResourceRepository =
    SqlResourceRepository(jooq, clock, objectMapper, resourceFactory, sqlRetry, publisher = mockk(relaxed = true), spectator = NoopRegistry(), springEnv = mockEnvironment())

  override fun createArtifactRepository(): SqlArtifactRepository =
    SqlArtifactRepository(jooq, clock, objectMapper, sqlRetry, defaultArtifactSuppliers(), publisher = mockk(relaxed = true))

  override fun createVerificationRepository(resourceFactory: ResourceFactory): SqlActionRepository =
    SqlActionRepository(jooq, clock, objectMapper, resourceFactory, sqlRetry, environment = mockk())

  override fun flush() {
    SqlTestUtil.cleanupDb(jooq)
  }
}
