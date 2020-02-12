package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.AgentLockRepositoryTests
import com.netflix.spinnaker.keel.scheduled.ScheduledAgent
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import java.time.Clock
import org.junit.jupiter.api.AfterAll

internal object SqlAgentLockRepositoryTests : AgentLockRepositoryTests<SqlAgentLockRepository>() {

  override fun factory(clock: Clock): SqlAgentLockRepository {
    return SqlAgentLockRepository(jooq, clock, listOf(DummyScheduledAgent(1)), sqlRetry)
  }

  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override fun SqlAgentLockRepository.flush() {
    SqlTestUtil.cleanupDb(jooq)
  }

  @JvmStatic
  @AfterAll
  fun shutdown() {
    testDatabase.dataSource.close()
  }
}

internal class DummyScheduledAgent(override val lockTimeoutSeconds: Long) : ScheduledAgent {
  override suspend fun invokeAgent() {
  }
}
