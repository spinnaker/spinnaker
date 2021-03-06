package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.api.verification.PendingVerification
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.api.verification.VerificationState
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import strikt.api.expectCatching
import strikt.assertions.isFalse
import strikt.assertions.isSuccess
import strikt.assertions.isTrue
import java.time.Instant.now

internal class PendingVerificationsConstraintEvaluatorTests {

  private val artifact = DockerArtifact(
    name = "fnord",
    reference = "fnord",
    branch = "main"
  )

  private val environmentWithNoVerifications = Environment(
    name = "test"
  )
  private val verification = DummyVerification("1")
  private val environmentWithVerification =
    environmentWithNoVerifications.copy(verifyWith = listOf(verification))

  private val deliveryConfig = DeliveryConfig(
    application = "fnord",
    name = "fnord-manifest",
    serviceAccount = "hagbard@illuminati.org",
    artifacts = setOf(artifact),
    environments = setOf(environmentWithNoVerifications)
  )

  private val verificationRepository = mockk<VerificationRepository>()
  private val eventPublisher = mockk<EventPublisher>()

  private val subject = PendingVerificationsConstraintEvaluator(verificationRepository, eventPublisher)

  @Test
  fun `can promote if the environment has no verifications`() {
    expectCatching {
      subject.canPromote(artifact, "v1", deliveryConfig, environmentWithNoVerifications)
    }
      .isSuccess()
      .isTrue()
  }

  @Test
  fun `can promote if there are no verifications currently running`() {
    with(deliveryConfig.copy(environments = setOf(environmentWithVerification))) {
      every { verificationRepository.pendingInEnvironment(any(), any()) } returns emptyList()

      expectCatching {
        subject.canPromote(artifact, "v1", this, environmentWithVerification)
      }
        .isSuccess()
        .isTrue()
    }
  }

  @Test
  fun `cannot promote if there are any verifications currently running`() {
    with(deliveryConfig.copy(environments = setOf(environmentWithVerification))) {
      every { verificationRepository.pendingInEnvironment(any(), any()) } returns listOf(
        PendingVerification(
          VerificationContext(this, environmentWithVerification.name, artifact.reference, "v1"),
          verification,
          VerificationState(PENDING, now(), null)
        )
      )

      expectCatching {
        subject.canPromote(artifact, "v1", this, environmentWithVerification)
      }
        .isSuccess()
        .isFalse()
    }
  }

  private data class DummyVerification(override val id: String) : Verification {
    override val type: String = "dummy"
  }
}
