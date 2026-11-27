package com.netflix.spinnaker.keel.sql

import com.netflix.spectator.micrometer.MicrometerRegistry
import com.netflix.spinnaker.keel.artifacts.DockerArtifactSupplier
import com.netflix.spinnaker.keel.jackson.registerKeelApiModule
import com.netflix.spinnaker.keel.persistence.EnvironmentLeaseRepositoryTests
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.test.resourceFactory
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.kork.sql.test.SqlTestUtil
import io.micrometer.core.instrument.Tags
import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import java.time.Duration

internal class SqlEnvironmentLeaseRepositoryTests :
  EnvironmentLeaseRepositoryTests<SqlEnvironmentLeaseRepository>() {
  private val jooq = testDatabase.context
  private val retryProperties = RetryProperties(1, 0)
  private val sqlRetry = SqlRetry(SqlRetryProperties(retryProperties, retryProperties))
  private val artifactSuppliers = listOf(DockerArtifactSupplier(mockk(), mockk(), mockk()))
  private val mapper = configuredObjectMapper()
    .registerKeelApiModule()
  private val resourceFactory = resourceFactory()
  private val meters = SimpleMeterRegistry()

  private val deliveryConfigRepository = SqlDeliveryConfigRepository(
    jooq = jooq,
    clock = clock,
    resourceFactory = resourceFactory,
    objectMapper = mapper,
    sqlRetry = sqlRetry,
    artifactSuppliers = artifactSuppliers,
    publisher = mockk(relaxed = true)
  )
  override fun createSubject() =
    SqlEnvironmentLeaseRepository(
      jooq = jooq,
      clock = clock,
      spectator = MicrometerRegistry(meters),
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

  @Test
  fun `records how long leases are held and how long denied attempts took`() {
    val environment = deliveryConfig.environments.first { it.name == "test" }

    val lease = subject.tryAcquireLease(deliveryConfig, environment, "test-action")
    clock.tickSeconds(1)
    expectCatching { subject.tryAcquireLease(deliveryConfig, environment, "test-action") }
      .isFailure()
    clock.tickSeconds(1)
    lease.close()

    expectThat(leaseTimerCount("granted")).isEqualTo(1L)
    expectThat(leaseTimerCount("denied")).isEqualTo(1L)
  }

  private fun leaseTimerCount(outcome: String): Long {
    val expected = Tags.of("action", "test-action", "outcome", outcome)
    return meters.find("lease.env.duration").timers()
      .single { Tags.of(it.id.tags) == expected }
      .count()
  }
}
