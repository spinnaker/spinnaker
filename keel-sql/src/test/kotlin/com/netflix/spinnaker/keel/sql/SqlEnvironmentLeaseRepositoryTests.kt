package com.netflix.spinnaker.keel.sql

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.keel.artifacts.DockerArtifactSupplier
import com.netflix.spinnaker.keel.jackson.registerKeelApiModule
import com.netflix.spinnaker.keel.persistence.EnvironmentLeaseRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import java.time.Duration

internal class SqlEnvironmentLeaseRepositoryTests :
  EnvironmentLeaseRepositoryTests<SqlEnvironmentLeaseRepository>() {
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))
  private val artifactSuppliers = listOf(DockerArtifactSupplier(mockk(), mockk(), mockk()))
  private val mapper = configuredObjectMapper()
    .registerKeelApiModule()

  private val deliveryConfigRepository = SqlDeliveryConfigRepository(
    jooq = jooq,
    clock = clock,
    resourceSpecIdentifier = ResourceSpecIdentifier(),
    objectMapper = mapper,
    sqlRetry = sqlRetry,
    artifactSuppliers = artifactSuppliers
  )
  override fun createSubject() =
    SqlEnvironmentLeaseRepository(
      jooq = jooq,
      clock = clock,
      spectator = NoopRegistry(),
      leaseDuration = leaseDuration
    )

  @BeforeEach
  fun setup() {
    deliveryConfigRepository.store(deliveryConfig)
  }

  @AfterEach
  fun flush() {
    SqlTestUtil.cleanupDb(jooq)
  }

}
