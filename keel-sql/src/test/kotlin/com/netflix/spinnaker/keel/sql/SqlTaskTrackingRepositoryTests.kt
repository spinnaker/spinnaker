package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.TaskTrackingRepositoryTests
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import java.time.Clock
import org.junit.jupiter.api.AfterAll

internal object SqlTaskTrackingRepositoryTests : TaskTrackingRepositoryTests<SqlTaskTrackingRepository>() {

  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override fun factory(clock: Clock): SqlTaskTrackingRepository {
    return SqlTaskTrackingRepository(
      jooq,
      Clock.systemDefaultZone(),
      sqlRetry
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
