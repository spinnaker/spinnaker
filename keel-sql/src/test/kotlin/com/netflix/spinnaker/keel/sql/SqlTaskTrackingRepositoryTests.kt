package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.TaskTrackingRepositoryTests
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import java.time.Clock
import org.junit.jupiter.api.AfterAll

private val testDatabase = initTestDatabase()
private val jooq = testDatabase.context

internal object SqlTaskTrackingRepositoryTests : TaskTrackingRepositoryTests<SqlTaskTrackingRepository>() {

  override fun factory(clock: Clock): SqlTaskTrackingRepository {
    return SqlTaskTrackingRepository(
      jooq,
      clock
    )
  }

  override fun SqlTaskTrackingRepository.flush() {
    SqlTestUtil.cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }
}
