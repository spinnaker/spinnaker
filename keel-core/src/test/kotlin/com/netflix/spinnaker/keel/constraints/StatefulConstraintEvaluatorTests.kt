package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.ConstraintStatus
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency
import com.netflix.spinnaker.keel.api.NotificationType
import com.netflix.spinnaker.keel.api.StatefulConstraint
import com.netflix.spinnaker.keel.events.ConstraintStateChanged
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Instant
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

internal class StatefulConstraintEvaluatorTests : JUnit5Minutests {

  class Fixture {
    val deliveryConfigRepository: DeliveryConfigRepository = mockk(relaxed = true)
    val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    val fakeStatefulConstraintEvaluatorDelegate: StatefulConstraintEvaluator<FakeConstraint> = mockk(relaxed = true)

    class FakeConstraint : StatefulConstraint("fake")

    class FakeStatefulConstraintEvaluator(
      override val deliveryConfigRepository: DeliveryConfigRepository,
      override val eventPublisher: ApplicationEventPublisher,
      val delegate: StatefulConstraintEvaluator<FakeConstraint>
    ) : StatefulConstraintEvaluator<FakeConstraint>() {
      override fun canPromote(
        artifact: DeliveryArtifact,
        version: String,
        deliveryConfig: DeliveryConfig,
        targetEnvironment: Environment,
        constraint: FakeConstraint,
        state: ConstraintState
      ) =
        delegate.canPromote(artifact, version, deliveryConfig, targetEnvironment, constraint, state)

      override val supportedType = SupportedConstraintType<FakeConstraint>("fake")
    }

    val artifact = DebianArtifact("fnord")

    val constraint = FakeConstraint()

    val environment = Environment(
      name = "test",
      notifications = setOf(
        NotificationConfig(
          type = NotificationType.slack,
          address = "#test",
          frequency = NotificationFrequency.normal
        )
      ),
      resources = setOf(resource()),
      constraints = setOf(constraint)
    )

    val manifest = DeliveryConfig(
      name = "test",
      application = "fnord",
      artifacts = setOf(artifact),
      environments = setOf(environment),
      serviceAccount = "keel@spinnaker"
    )

    val pendingConstraintState = ConstraintState(
      deliveryConfigName = "test",
      environmentName = "test",
      artifactVersion = "v1.0.0",
      type = constraint.type,
      status = ConstraintStatus.PENDING
    )

    val subject = FakeStatefulConstraintEvaluator(
      deliveryConfigRepository, eventPublisher, fakeStatefulConstraintEvaluatorDelegate)
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    before {
      every {
        eventPublisher.publishEvent(any())
      } just Runs

      every {
        deliveryConfigRepository.getConstraintState("test", "test", "v1.0.0", "fake")
      } returns null

      every {
        deliveryConfigRepository.getConstraintState("test", "test", "v1.0.1", "fake")
      } returns pendingConstraintState

      every {
        fakeStatefulConstraintEvaluatorDelegate.canPromote(artifact, "v1.0.0", manifest, environment, constraint, any())
      } returns true
    }

    test("abstract canPromote delegates to concrete sub-class") {
      // The method defined in StatefulConstraintEvaluator...
      subject.canPromote(artifact, "v1.0.0", manifest, environment)

      val state = slot<ConstraintState>()
      verify {
        // ...in turns calls this method on the sub-class
        subject.canPromote(artifact, "v1.0.0", manifest, environment, constraint, capture(state))
      }
      // We ignore the timestamp because it's generated dynamically
      expectThat(state.captured).isEqualTo(pendingConstraintState.createdAt(state.captured.createdAt))
    }

    test("canPromote generates an event when first storing state") {
      val expectedEvent = ConstraintStateChanged(
        environment = environment,
        constraint = constraint,
        previousState = null,
        currentState = pendingConstraintState
      )

      expectThat(subject.canPromote(artifact, "v1.0.0", manifest, environment))
        .isTrue()

      val event = slot<ConstraintStateChanged>()
      verify(exactly = 1) {
        eventPublisher.publishEvent(capture(event))
      }

      expectThat(event.captured).isEqualTo(
        // We ignore the timestamp as it's dynamically generated
        expectedEvent.copy(
          currentState = expectedEvent.currentState.createdAt(event.captured.currentState.createdAt)
        )
      )
    }

    test("canPromote does NOT generate an event after the first state change") {
      subject.canPromote(artifact, "v1.0.0", manifest, environment)
      subject.canPromote(artifact, "v1.0.1", manifest, environment)

      verify(exactly = 2) {
        deliveryConfigRepository.getConstraintState("test", "test", any(), "fake")
      }

      val event = slot<ConstraintStateChanged>()
      verify(exactly = 1) {
        eventPublisher.publishEvent(capture(event))
      }
    }
  }

  private fun ConstraintState.createdAt(time: Instant) =
    copy(
      createdAt = time
    )
}
