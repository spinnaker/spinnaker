package com.netflix.spinnaker.keel.sql

import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.defaultArtifactSuppliers
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.resourceFactory
import com.netflix.spinnaker.kork.sql.config.RetryProperties
import com.netflix.spinnaker.kork.sql.config.SqlRetryProperties
import com.netflix.spinnaker.time.MutableClock
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expect
import strikt.assertions.isEqualTo
import java.time.Duration
import java.time.Instant.EPOCH
import java.time.Instant.now
import java.time.temporal.ChronoUnit.DAYS

class ArtifactVersionApprovalCountTests {
  private val jooq = testDatabase.context
  private val sqlRetry = RetryProperties(1, 0).let {
    SqlRetry(SqlRetryProperties(it, it))
  }
  private val objectMapper = configuredTestObjectMapper()
  private val clock = MutableClock()
  private val publisher = mockk<ApplicationEventPublisher>(relaxed = true)
  private val resourceFactory = resourceFactory()
  private val deliveryConfig = deliveryConfig()
  private val deliveryConfigRepository = SqlDeliveryConfigRepository(
    jooq,
    clock,
    objectMapper,
    resourceFactory,
    sqlRetry,
    defaultArtifactSuppliers(),
    publisher = publisher
  )
  private val artifactRepository = SqlArtifactRepository(
    jooq,
    clock,
    objectMapper,
    sqlRetry,
    publisher = publisher
  )

  @Test
  fun `we can determine how many times we have approved an artifact version for an environment within a time window`() {
    artifactRepository.register(deliveryConfig.artifacts.single())
    deliveryConfigRepository.store(deliveryConfig)

    // stabilize time to something we know won't make this test fail at certain times of day
    clock.instant(now().truncatedTo(DAYS).plus(Duration.ofHours(10)))

    // add an artifact version and mark it as deployed
    artifactRepository.storeArtifactVersion(
      deliveryConfig.artifacts.single()
        .toArtifactVersion("fnord-0.1055.0-h1521.ecf8531")
    )
    artifactRepository.approveVersionFor(
      deliveryConfig,
      deliveryConfig.artifacts.single(),
      "fnord-0.1055.0-h1521.ecf8531",
      deliveryConfig.environments.single().name
    )

    // a day has passed and another artifact version is deployed
    clock.incrementBy(Duration.ofDays(1))
    artifactRepository.storeArtifactVersion(
      deliveryConfig.artifacts.single()
        .toArtifactVersion("fnord-0.1056.0-h1522.ecf8531")
    )
    artifactRepository.approveVersionFor(
      deliveryConfig,
      deliveryConfig.artifacts.single(),
      "fnord-0.1056.0-h1522.ecf8531",
      deliveryConfig.environments.single().name
    )

    clock.incrementBy(Duration.ofHours(1))

    expect {
      that(versionsEver()) isEqualTo 2
      that(versionsToday()) isEqualTo 1
    }
  }

  private fun versionsEver() =
    artifactRepository.versionsApprovedBetween(
      deliveryConfig,
      deliveryConfig.environments.first().name,
      EPOCH,
      clock.instant()
    )

  private fun versionsToday() =
    artifactRepository.versionsApprovedBetween(
      deliveryConfig,
      deliveryConfig.environments.first().name,
      clock.instant().truncatedTo(DAYS),
      clock.instant()
    )
}
