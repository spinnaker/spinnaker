package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.exceptions.ActiveLeaseExists
import com.netflix.spinnaker.time.MutableClock
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.assertions.isA
import strikt.assertions.isFailure
import strikt.assertions.isSuccess
import java.time.Duration

abstract class EnvironmentLeaseRepositoryTests<IMPLEMENTATION : EnvironmentLeaseRepository> {
  abstract fun createSubject(): IMPLEMENTATION

  protected val leaseDuration : Duration = Duration.ofSeconds(60)

  private val testEnv = Environment(name="test")
  private val prodEnv = Environment(name="prod")
  protected val deliveryConfig = DeliveryConfig(
    application = "fnord",
    name = "fnord-manifest",
    serviceAccount = "jamm@illuminati.org",
    artifacts = setOf(
      DockerArtifact(
        name = "fnord",
        deliveryConfigName = "fnord-manifest",
        reference = "fnord-docker-stable",
        branch = "main"
      )
    ),
    environments = setOf( testEnv, prodEnv )
  )

  val clock = MutableClock()
  val subject: IMPLEMENTATION by lazy { createSubject() }

  @Test
  fun `can get a lease when there are no leases yet`() {
    expectCatching { subject.tryAcquireLease(deliveryConfig, testEnv, "unit test") }
      .isSuccess()
  }

  @Test
  fun `cannot get a lease when there is an active lease`() {
    // Add a lease
    subject.tryAcquireLease(deliveryConfig, testEnv, "unit test")

    // Try again 1 second later
    clock.tickSeconds(1)
    expectCatching { subject.tryAcquireLease(deliveryConfig, testEnv, "unit test") }
      .isFailure()
      .isA<ActiveLeaseExists>()
  }

  @Test
  fun `can get a lease when the previous lease has expired`() {
    // Add a lease
    subject.tryAcquireLease(deliveryConfig, testEnv, "unit test")

    // Try again 5 minutes later
    clock.tickMinutes(5)
    expectCatching { subject.tryAcquireLease(deliveryConfig, testEnv, "unit test") }
      .isSuccess()
  }


  @Test
  fun `can get a lease for a different environment`() {
    // Add a lease
    subject.tryAcquireLease(deliveryConfig, testEnv, "unit test")

    // Try a different environment one second later
    clock.tickSeconds(1)
    expectCatching { subject.tryAcquireLease(deliveryConfig, prodEnv, "unit test") }
      .isSuccess()
  }


  @Test
  fun `can get a new lease after returning the old one`() {
    // add a lease
    val lease = subject.tryAcquireLease(deliveryConfig, testEnv, "unit test")

    clock.tickSeconds(1)

    // return the lease
    lease.close()

    clock.tickSeconds(1)

    // try to get another lease
    expectCatching { subject.tryAcquireLease(deliveryConfig, testEnv, "unit test") }
      .isSuccess()
  }
}
