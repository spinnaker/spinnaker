package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryTests
import org.junit.jupiter.api.AfterAll

internal object SqlDeliveryConfigRepositoryTests : DeliveryConfigRepositoryTests<SqlDeliveryConfigRepository>() {
  private val jooq = testDatabase()

  override fun factory(resourceTypeIdentifier: (String) -> Class<*>): SqlDeliveryConfigRepository {
    return SqlDeliveryConfigRepository(jooq, resourceTypeIdentifier)
  }

  override fun flush() {
    jooq.flushAll()
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    jooq.close()
  }
}
