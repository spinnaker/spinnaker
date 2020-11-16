package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.persistence.VerificationRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import java.time.Clock

internal class SqlVerificationRepositoryTests :
  VerificationRepositoryTests<SqlVerificationRepository>() {

  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))

  private val deliveryConfigRepository = SqlDeliveryConfigRepository(jooq, Clock.systemUTC(), ResourceSpecIdentifier(), configuredObjectMapper(), sqlRetry)
  private val artifactRepository = SqlArtifactRepository(jooq, Clock.systemUTC(), configuredObjectMapper(), sqlRetry)

  override fun createSubject() = SqlVerificationRepository(jooq, Clock.systemUTC())

  override fun VerificationContext.setup() {
    deliveryConfigRepository.store(deliveryConfig)
    artifactRepository.register(artifact)
  }

  @AfterEach
  fun flush() {
    SqlTestUtil.cleanupDb(jooq)
  }

  companion object {
    private val testDatabase = initTestDatabase()

    @JvmStatic
    @AfterAll
    fun shutdown() {
      testDatabase.dataSource.close()
    }
  }
}
