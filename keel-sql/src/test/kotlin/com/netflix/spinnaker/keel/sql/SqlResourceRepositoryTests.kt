package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.ResourceRepositoryTests
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil.cleanupDb
import java.time.Clock
import org.junit.jupiter.api.AfterAll

internal object SqlResourceRepositoryTests : ResourceRepositoryTests<SqlResourceRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override fun factory(clock: Clock): SqlResourceRepository {
    return SqlResourceRepository(
      jooq,
      clock,
      DummyResourceTypeIdentifier,
      configuredObjectMapper(),
      sqlRetry
    )
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
