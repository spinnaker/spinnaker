package com.netflix.spinnaker.keel.sql

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.keel.persistence.LifecycleEventRepositoryTests
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import org.junit.jupiter.api.AfterAll
import java.time.Clock

internal object SqlLifecycleEventRepositoryTests : LifecycleEventRepositoryTests<SqlLifecycleEventRepository>() {
  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override fun factory(clock: Clock): SqlLifecycleEventRepository {
    return SqlLifecycleEventRepository(
      clock,
      jooq,
      sqlRetry,
      configuredTestObjectMapper(),
      NoopRegistry()
    )
  }

  override fun SqlLifecycleEventRepository.flush() {
    SqlTestUtil.cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }
}
