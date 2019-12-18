package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.persistence.ArtifactRepositoryTests
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import java.time.Clock

class SqlArtifactRepositoryTests : ArtifactRepositoryTests<SqlArtifactRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val objectmapper = configuredObjectMapper()

  private val deliveryConfigRepository = SqlDeliveryConfigRepository(
    jooq,
    Clock.systemDefaultZone(),
    DummyResourceTypeIdentifier
  )

  override fun factory(clock: Clock): SqlArtifactRepository =
    SqlArtifactRepository(jooq, clock, objectmapper)

  override fun SqlArtifactRepository.flush() {
    cleanupDb(jooq)
  }

  override fun persist(manifest: DeliveryConfig) {
    deliveryConfigRepository.store(manifest)
  }
}
