package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.ArtifactRepositoryTests
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import java.time.Clock

class SqlArtifactRepositoryTests : ArtifactRepositoryTests<SqlArtifactRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context

  private val deliveryConfigRepository = SqlDeliveryConfigRepository(
    jooq,
    Clock.systemDefaultZone(),
    DummyResourceTypeIdentifier
  )

  override fun factory(): SqlArtifactRepository =
    SqlArtifactRepository(jooq, Clock.systemDefaultZone())

  override fun SqlArtifactRepository.flush() {
    cleanupDb(jooq)
  }

  override fun Fixture<SqlArtifactRepository>.persist() {
    with(subject) {
      register(artifact1)
      setOf(version1_0, version1_1, version1_2).forEach {
        store(artifact1, it)
      }
      register(artifact2)
      setOf(version1_0, version1_1, version1_2).forEach {
        store(artifact2, it)
      }
    }
    deliveryConfigRepository.store(manifest)
  }
}
