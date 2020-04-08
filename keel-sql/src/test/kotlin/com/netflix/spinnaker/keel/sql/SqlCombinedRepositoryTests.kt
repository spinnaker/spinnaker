package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.CombinedRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import java.time.Clock
import org.junit.jupiter.api.AfterAll

internal object SqlCombinedRepositoryTests : CombinedRepositoryTests<SqlDeliveryConfigRepository, SqlResourceRepository, SqlArtifactRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val objectMapper = configuredObjectMapper()
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override fun createDeliveryConfigRepository(resourceSpecIdentifier: ResourceSpecIdentifier): SqlDeliveryConfigRepository =
    SqlDeliveryConfigRepository(jooq, Clock.systemUTC(), resourceSpecIdentifier, objectMapper, sqlRetry)

  override fun createResourceRepository(resourceSpecIdentifier: ResourceSpecIdentifier): SqlResourceRepository =
    SqlResourceRepository(jooq, Clock.systemUTC(), resourceSpecIdentifier, emptyList(), objectMapper, sqlRetry)

  override fun createArtifactRepository(): SqlArtifactRepository =
    SqlArtifactRepository(jooq, Clock.systemUTC(), objectMapper, sqlRetry)

  override fun flush() {
    SqlTestUtil.cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }
}
