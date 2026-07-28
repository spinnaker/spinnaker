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
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.springframework.context.ApplicationEventPublisher

class ConstraintStateChangedRelayTests {
  private val constraint = FakeConstraint()

  private val event = ConstraintStateChanged(
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

  private val pluginNotification = PluginNotificationConfig(
    title = "title",
    message = "message",
    status = PluginNotificationsStatus.FAILED,
    buttonText = null,
    buttonLink = null,
    notificationLevel = NotificationFrequency.normal,
    provenance = "plugin",
  )

  private val matchingEvaluator = mockk<StatefulConstraintEvaluator<FakeConstraint, DefaultConstraintAttributes>>() {
    every { supportedType } returns SupportedConstraintType("fake", FakeConstraint::class.java)
    every { onConstraintStateChanged(event) } just runs
  }

  private val nonMatchingEvaluator = mockk<StatefulConstraintEvaluator<FakeConstraint, DefaultConstraintAttributes>>() {
    every { supportedType } returns SupportedConstraintType("the-wrong-fake", FakeConstraint::class.java)
  }

  private val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)

  private val subject = ConstraintStateChangedRelay(listOf(matchingEvaluator, nonMatchingEvaluator), publisher)

  @Nested
  inner class StatefulConstraintStateChangedEventIsEmitted {
    @BeforeEach
    fun setup() {
      every { matchingEvaluator.onConstraintStateChangedWithNotification(event) } returns null
      every { nonMatchingEvaluator.onConstraintStateChangedWithNotification(event) } returns null
      subject.onConstraintStateChanged(event)
    }

    @Test
    fun `the event is relayed to the correct constraint evaluator`() {
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

  @Nested
  inner class EvaluatorReturnsNotification {
    @BeforeEach
    fun setup() {
      every { matchingEvaluator.onConstraintStateChangedWithNotification(event) } returns pluginNotification
      every { nonMatchingEvaluator.onConstraintStateChangedWithNotification(event) } returns null
      subject.onConstraintStateChanged(event)
    }

    @Test
    fun `the notification is propagated`() {
      verify(exactly = 1) {
        publisher.publishEvent(PluginNotification(pluginNotification, event))
      }
    }
  }
}
