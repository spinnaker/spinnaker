package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.PromotionRepositoryTests
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import java.time.Clock

class SqlPromotionRepositoryTests : PromotionRepositoryTests<SqlPromotionRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context

  private val artifactRepository = SqlArtifactRepository(jooq)
  private val deliveryConfigRepository = SqlDeliveryConfigRepository(
    jooq,
    Clock.systemDefaultZone(),
    DummyResourceTypeIdentifier
  )

  override fun factory(): SqlPromotionRepository =
    SqlPromotionRepository(jooq, Clock.systemDefaultZone())

  override fun SqlPromotionRepository.flush() {
    cleanupDb(jooq)
  }

  override fun Fixture<SqlPromotionRepository>.persist() {
    with(artifactRepository) {
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
