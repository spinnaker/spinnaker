package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.UnhealthyRepositoryTests
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import org.junit.jupiter.api.AfterAll
import java.time.Clock

internal object SqlUnhealthyRepositoryTests : UnhealthyRepositoryTests<SqlUnhealthyRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override fun factory(clock: Clock): SqlUnhealthyRepository {
    return SqlUnhealthyRepository(
      clock,
      jooq,
      sqlRetry
    )
  }

  override fun SqlUnhealthyRepository.flush() {
    SqlTestUtil.cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }

}