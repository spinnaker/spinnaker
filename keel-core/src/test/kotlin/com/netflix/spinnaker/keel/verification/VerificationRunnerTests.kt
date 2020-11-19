package com.netflix.spinnaker.keel.verification

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.plugins.VerificationEvaluator
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.api.verification.VerificationState
import com.netflix.spinnaker.keel.api.verification.VerificationStatus
import com.netflix.spinnaker.keel.api.verification.VerificationStatus.PASSED
import com.netflix.spinnaker.keel.api.verification.VerificationStatus.RUNNING
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.telemetry.VerificationCompleted
import com.netflix.spinnaker.keel.telemetry.VerificationStarted
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import org.springframework.context.ApplicationEventPublisher
import java.time.Instant.now

internal class VerificationRunnerTests {

  private data class DummyVerification(override val id: String) : Verification {
    override val type = "dummy"
  }

  private val repository = mockk<VerificationRepository>(relaxUnitFun = true)
  private val evaluator = mockk<VerificationEvaluator<DummyVerification>>(relaxUnitFun = true) {
    every { supportedVerification } returns ("dummy" to DummyVerification::class.java)
  }
  private val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)

  private val subject = VerificationRunner(
    repository,
    listOf(evaluator),
    publisher
  )

  @Test
  fun `no-ops for an environment with no verifications`() {
    val context = VerificationContext(
      deliveryConfig = DeliveryConfig(
        application = "fnord",
        name = "fnord-manifest",
        serviceAccount = "jamm@illuminati.org",
        artifacts = setOf(
          DockerArtifact("fnord")
        ),
        environments = setOf(
          Environment(name = "test")
        )
      ),
      environmentName = "test",
      version = "fnord-0.190.0-h378.eacb135"
    )

    subject.runVerificationsFor(context)

    verify(exactly = 0) { evaluator.start(any()) }
    verify(exactly = 0) { publisher.publishEvent(any()) }
  }

  @Test
  fun `starts the first verification if none have been run yet`() {
    val context = VerificationContext(
      deliveryConfig = DeliveryConfig(
        application = "fnord",
        name = "fnord-manifest",
        serviceAccount = "jamm@illuminati.org",
        artifacts = setOf(
          DockerArtifact("fnord")
        ),
        environments = setOf(
          Environment(
            name = "test",
            verifyWith = setOf(DummyVerification("1"), DummyVerification("2"))
          )
        )
      ),
      environmentName = "test",
      version = "fnord-0.190.0-h378.eacb135"
    )

    every { repository.getState(any(), any()) } returns null

    subject.runVerificationsFor(context)

    verify { evaluator.start(DummyVerification("1")) }
    verify { repository.updateState(any(), DummyVerification("1"), RUNNING) }
    verify { publisher.publishEvent(ofType<VerificationStarted>()) }
  }

  @Test
  fun `no-ops if any verification was already running and has yet to complete`() {
    val context = VerificationContext(
      deliveryConfig = DeliveryConfig(
        application = "fnord",
        name = "fnord-manifest",
        serviceAccount = "jamm@illuminati.org",
        artifacts = setOf(
          DockerArtifact("fnord")
        ),
        environments = setOf(
          Environment(
            name = "test",
            verifyWith = setOf(DummyVerification("1"), DummyVerification("2"))
          )
        )
      ),
      environmentName = "test",
      version = "fnord-0.190.0-h378.eacb135"
    )

    every { repository.getState(any(), DummyVerification("1")) } returns RUNNING.toState()
    every { repository.getState(any(), DummyVerification("2")) } returns null

    every { evaluator.evaluate() } returns RUNNING

    subject.runVerificationsFor(context)

    verify { evaluator.evaluate() }
    verify(exactly = 0) { evaluator.start(any()) }
    verify(exactly = 0) { publisher.publishEvent(any()) }
  }

  @ParameterizedTest(
    name = "continues to the next if any verification was already running and has now completed with {0}"
  )
  @EnumSource(
    value = VerificationStatus::class,
    names = ["PASSED", "FAILED"]
  )
  fun `continues to the next if any verification was already running and has now completed`(status: VerificationStatus) {
    val context = VerificationContext(
      deliveryConfig = DeliveryConfig(
        application = "fnord",
        name = "fnord-manifest",
        serviceAccount = "jamm@illuminati.org",
        artifacts = setOf(
          DockerArtifact("fnord")
        ),
        environments = setOf(
          Environment(
            name = "test",
            verifyWith = setOf(DummyVerification("1"), DummyVerification("2"))
          )
        )
      ),
      environmentName = "test",
      version = "fnord-0.190.0-h378.eacb135"
    )

    every { repository.getState(any(), DummyVerification("1")) } returns RUNNING.toState()
    every { repository.getState(any(), DummyVerification("2")) } returns null

    every { evaluator.evaluate() } returns status

    subject.runVerificationsFor(context)

    verify { repository.updateState(any(), DummyVerification("1"), status) }
    verify { publisher.publishEvent(ofType<VerificationCompleted>()) }

    verify { evaluator.start(DummyVerification("2")) }
    verify { repository.updateState(any(), DummyVerification("2"), RUNNING) }
    verify { publisher.publishEvent(ofType<VerificationStarted>()) }
  }

  @ParameterizedTest(
    name = "no-ops if all verifications are already complete and the final one is {0}"
  )
  @EnumSource(
    value = VerificationStatus::class,
    names = ["PASSED", "FAILED"]
  )
  fun `no-ops if all verifications are already complete`(status: VerificationStatus) {
    val context = VerificationContext(
      deliveryConfig = DeliveryConfig(
        application = "fnord",
        name = "fnord-manifest",
        serviceAccount = "jamm@illuminati.org",
        artifacts = setOf(
          DockerArtifact("fnord")
        ),
        environments = setOf(
          Environment(
            name = "test",
            verifyWith = setOf(DummyVerification("1"), DummyVerification("2"))
          )
        )
      ),
      environmentName = "test",
      version = "fnord-0.190.0-h378.eacb135"
    )

    every {
      repository.getState(any(), DummyVerification("1"))
    } returns PASSED.toState()
    every {
      repository.getState(any(), DummyVerification("2"))
    } returns status.toState()

    subject.runVerificationsFor(context)

    verify(exactly = 0) {
      evaluator.start(any())
    }
  }

  private fun VerificationStatus.toState() =
    VerificationState(
      status = this,
      startedAt = now(),
      endedAt = if (this.complete) now() else null
    )
}
