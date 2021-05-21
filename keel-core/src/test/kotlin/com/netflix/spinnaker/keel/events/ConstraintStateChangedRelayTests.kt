package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationFrequency
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.api.constraints.DefaultConstraintAttributes
import com.netflix.spinnaker.keel.api.plugins.PluginNotificationConfig
import com.netflix.spinnaker.keel.api.plugins.PluginNotificationsStatus
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.api.events.ConstraintStateChanged
import com.netflix.spinnaker.keel.constraints.StatefulConstraintEvaluatorTests.Fixture.FakeConstraint
import com.netflix.spinnaker.keel.test.deliveryConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

class ConstraintStateChangedRelayTests : JUnit5Minutests {
  object Fixture {
    private val constraint = FakeConstraint()

    val event = ConstraintStateChanged(
      environment = Environment(
        name = "test",
        constraints = setOf(constraint)
      ),
      constraint = constraint,
      previousState = null,
      currentState = ConstraintState(
        deliveryConfigName = "myconfig",
        environmentName = "test",
        artifactReference = "whatever",
        artifactVersion = "whatever",
        status = OVERRIDE_PASS,
        type = constraint.type
      ),
      deliveryConfig = deliveryConfig()
    )

    val pluginNotification = PluginNotificationConfig(
      title = "title",
      message = "message",
      status = PluginNotificationsStatus.FAILED,
      buttonText = null,
      buttonLink = null,
      notificationLevel = NotificationFrequency.normal,
      provenance = "plugin",
    )

    internal val matchingEvaluator = mockk<StatefulConstraintEvaluator<FakeConstraint, DefaultConstraintAttributes>>() {
      every { supportedType } returns SupportedConstraintType("fake", FakeConstraint::class.java)
      every { onConstraintStateChanged(event) } just runs
    }

    internal val nonMatchingEvaluator = mockk<StatefulConstraintEvaluator<FakeConstraint, DefaultConstraintAttributes>>() {
      every { supportedType } returns SupportedConstraintType("the-wrong-fake", FakeConstraint::class.java)
    }

    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)

    val subject = ConstraintStateChangedRelay(listOf(matchingEvaluator, nonMatchingEvaluator), publisher)
  }

  fun tests() = rootContext<Fixture> {
    context("a stateful constraint state changed event is emitted") {
      fixture { Fixture }

      before {
        every { matchingEvaluator.onConstraintStateChangedWithNotification(event) } returns null
        every { nonMatchingEvaluator.onConstraintStateChangedWithNotification(event) } returns null
        subject.onConstraintStateChanged(event)
      }

      test("the event is relayed to the correct constraint evaluator") {
        verify(exactly = 1) {
          matchingEvaluator.onConstraintStateChanged(event)
          matchingEvaluator.onConstraintStateChangedWithNotification(event)
        }
        verify(exactly = 0) {
          nonMatchingEvaluator.onConstraintStateChanged(event)
          nonMatchingEvaluator.onConstraintStateChangedWithNotification(event)
        }
      }
    }

    context("the evaluator returns a notification") {
      fixture { Fixture }

      before {
        every { matchingEvaluator.onConstraintStateChangedWithNotification(event) } returns pluginNotification
        every { nonMatchingEvaluator.onConstraintStateChangedWithNotification(event) } returns null
        subject.onConstraintStateChanged(event)
      }

      test("the notification is propagated") {
        verify(exactly = 1) {
          publisher.publishEvent(PluginNotification(pluginNotification, event))
        }
      }
    }
  }
}
