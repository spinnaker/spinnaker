package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.CombinedRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.defaultArtifactSuppliers
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import org.junit.jupiter.api.AfterAll
import java.time.Clock

internal object SqlCombinedRepositoryTests :
  CombinedRepositoryTests<SqlDeliveryConfigRepository, SqlResourceRepository, SqlArtifactRepository, SqlVerificationRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val objectMapper = configuredTestObjectMapper()
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
      defaultArtifactSuppliers()
    )

  override fun createResourceRepository(resourceSpecIdentifier: ResourceSpecIdentifier): SqlResourceRepository =
    SqlResourceRepository(jooq, clock, resourceSpecIdentifier, emptyList(), objectMapper, sqlRetry)

  override fun createArtifactRepository(): SqlArtifactRepository =
    SqlArtifactRepository(jooq, clock, objectMapper, sqlRetry, defaultArtifactSuppliers())

  override fun createVerificationRepository(resourceSpecIdentifier: ResourceSpecIdentifier): SqlVerificationRepository =
    SqlVerificationRepository(jooq, clock, resourceSpecIdentifier, objectMapper, sqlRetry)

  override fun flush() {
    SqlTestUtil.cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }
}
