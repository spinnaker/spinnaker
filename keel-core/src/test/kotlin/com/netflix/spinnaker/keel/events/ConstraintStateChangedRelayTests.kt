package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.api.constraints.DefaultConstraintAttributes
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.api.events.ConstraintStateChanged
import com.netflix.spinnaker.keel.constraints.StatefulConstraintEvaluatorTests.Fixture.FakeConstraint
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify

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
      )
    )

    internal val matchingEvaluator = mockk<StatefulConstraintEvaluator<FakeConstraint, DefaultConstraintAttributes>>() {
      every { supportedType } returns SupportedConstraintType("fake", FakeConstraint::class.java)
      every { onConstraintStateChanged(event) } just runs
    }

    internal val nonMatchingEvaluator = mockk<StatefulConstraintEvaluator<FakeConstraint, DefaultConstraintAttributes>>() {
      every { supportedType } returns SupportedConstraintType("the-wrong-fake", FakeConstraint::class.java)
    }

    val subject = ConstraintStateChangedRelay(listOf(matchingEvaluator, nonMatchingEvaluator))
  }

  fun tests() = rootContext<Fixture> {
    context("a stateful constraint state changed event is emitted") {
      fixture { Fixture }

      before {
        subject.onConstraintStateChanged(event)
      }

      test("the event is relayed to the correct constraint evaluator") {
        verify(exactly = 1) {
          matchingEvaluator.onConstraintStateChanged(event)
        }
        verify(exactly = 0) {
          nonMatchingEvaluator.onConstraintStateChanged(event)
        }
      }
    }
  }
}
