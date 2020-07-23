package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryPeriodicallyCheckedTests
import com.netflix.spinnaker.keel.persistence.DummyResourceSpecIdentifier
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.defaultArtifactSuppliers
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import java.time.Clock
import org.junit.jupiter.api.AfterAll

internal object SqlDeliveryConfigRepositoryPeriodicallyCheckedTests :
  DeliveryConfigRepositoryPeriodicallyCheckedTests<SqlDeliveryConfigRepository>() {

  private val testDatabase = initTestDatabase()
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val objectMapper = configuredTestObjectMapper()
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  override val factory: (Clock) -> SqlDeliveryConfigRepository = { clock ->
    SqlDeliveryConfigRepository(jooq, clock, DummyResourceSpecIdentifier, objectMapper, sqlRetry, defaultArtifactSuppliers())
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
