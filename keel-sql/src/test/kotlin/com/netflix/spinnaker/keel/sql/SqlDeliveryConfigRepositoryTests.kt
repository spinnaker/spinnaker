package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import java.time.Clock
import org.junit.jupiter.api.AfterAll

internal object SqlDeliveryConfigRepositoryTests : DeliveryConfigRepositoryTests<SqlDeliveryConfigRepository, SqlResourceRepository, SqlArtifactRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val objectmapper = configuredObjectMapper()
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override fun createDeliveryConfigRepository(resourceTypeIdentifier: ResourceTypeIdentifier): SqlDeliveryConfigRepository =
    SqlDeliveryConfigRepository(jooq, Clock.systemDefaultZone(), DummyResourceTypeIdentifier, sqlRetry)

  override fun createResourceRepository(): SqlResourceRepository =
    SqlResourceRepository(jooq, Clock.systemDefaultZone(), DummyResourceTypeIdentifier, configuredObjectMapper(), sqlRetry)

  override fun createArtifactRepository(): SqlArtifactRepository =
    SqlArtifactRepository(jooq, Clock.systemDefaultZone(), objectmapper, sqlRetry)

  override fun flush() {
    cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }
}
