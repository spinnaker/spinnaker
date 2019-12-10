package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import org.junit.jupiter.api.AfterAll
import java.time.Clock

internal object SqlDeliveryConfigRepositoryTests : DeliveryConfigRepositoryTests<SqlDeliveryConfigRepository, SqlResourceRepository, SqlArtifactRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val objectmapper = configuredObjectMapper()

  override fun createDeliveryConfigRepository(resourceTypeIdentifier: ResourceTypeIdentifier): SqlDeliveryConfigRepository =
    SqlDeliveryConfigRepository(jooq, Clock.systemDefaultZone(), DummyResourceTypeIdentifier)

  override fun createResourceRepository(): SqlResourceRepository =
    SqlResourceRepository(jooq, Clock.systemDefaultZone(), DummyResourceTypeIdentifier, configuredObjectMapper())

  override fun createArtifactRepository(): SqlArtifactRepository =
    SqlArtifactRepository(jooq, Clock.systemDefaultZone(), objectmapper)

  override fun flush() {
    cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }
}
