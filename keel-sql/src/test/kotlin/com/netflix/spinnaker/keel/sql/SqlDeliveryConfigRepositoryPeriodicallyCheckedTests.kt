package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryPeriodicallyCheckedTests
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import org.junit.jupiter.api.AfterAll
import java.time.Clock

internal object SqlDeliveryConfigRepositoryPeriodicallyCheckedTests :
  DeliveryConfigRepositoryPeriodicallyCheckedTests<SqlDeliveryConfigRepository>() {

  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context

  override val factory: (Clock) -> SqlDeliveryConfigRepository = { clock ->
    SqlDeliveryConfigRepository(jooq, clock, DummyResourceTypeIdentifier)
  }

  override fun flush() {
    SqlTestUtil.cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }
}
