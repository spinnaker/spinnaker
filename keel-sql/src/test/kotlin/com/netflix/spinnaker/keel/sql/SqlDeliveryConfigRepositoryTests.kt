package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryTests
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.initTcMysqlDatabase
import org.junit.jupiter.api.AfterAll

internal object SqlDeliveryConfigRepositoryTests : DeliveryConfigRepositoryTests<SqlDeliveryConfigRepository>() {
  private val testDatabase = initTcMysqlDatabase()
  private val jooq = testDatabase.context

  override fun factory(resourceTypeIdentifier: (String) -> Class<*>): SqlDeliveryConfigRepository {
    return SqlDeliveryConfigRepository(jooq, resourceTypeIdentifier)
  }

  override fun flush() {
    cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }
}
