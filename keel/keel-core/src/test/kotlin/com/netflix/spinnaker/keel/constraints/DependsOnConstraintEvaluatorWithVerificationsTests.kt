package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Verification
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.ArtifactInEnvironmentContext
import com.netflix.spinnaker.keel.api.action.ActionRepository
import com.netflix.spinnaker.keel.api.action.ActionType.POST_DEPLOY
import com.netflix.spinnaker.keel.api.action.ActionType.VERIFICATION
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

data class FakeVerification(
  override val id: String ,
  override val type: String = "fake") : Verification

/**
 * Tests DependsOnConstraintEvaluator when there are verifications associated with the previous environment
 */
class DependsOnConstraintEvaluatorWithVerificationsTests : JUnit5Minutests {
  object Fixture {
    val artifact =
      DebianArtifact("fnord", vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")))
    val verification = FakeVerification("fake-id")
    val constrainedEnvironment = Environment(
      name = "staging",
      constraints = setOf(
        DependsOnConstraint(environment = "test")
      )
    )
    val previousEnvironment = Environment(
      name = "test",
      verifyWith = listOf(verification)
    )

    val manifest = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = setOf(previousEnvironment, constrainedEnvironment)
    )

    val verContext = ArtifactInEnvironmentContext( manifest, previousEnvironment.name, artifact.reference, "1.1")
  }

  val artifactRepository: ArtifactRepository = mockk(relaxUnitFun = true)
  val actionRepository: ActionRepository = mockk() {
    every { allStarted(any(), POST_DEPLOY) } returns true
  }
  val clock = MutableClock()

  val subject = DependsOnConstraintEvaluator(artifactRepository, actionRepository, mockk(), clock)


  fun tests() = rootContext<Fixture> {
    fixture { Fixture }


    before {
      every {
        artifactRepository.wasSuccessfullyDeployedTo(
          manifest,
          artifact,
          "1.1",
          previousEnvironment.name
        )
      } returns true
    }


    context("verification succeeded") {
      before {
          every { actionRepository.allPassed(verContext, VERIFICATION) } returns true
      }

      test("promotion is allowed") {
        expectThat(subject.canPromote(artifact, "1.1", manifest, constrainedEnvironment))
          .isTrue()
      }
    }

    context("verification did not succeed") {
      before {
        every { actionRepository.allPassed(verContext, VERIFICATION) } returns false
      }

      test("promotion is not allowed") {
        expectThat(subject.canPromote(artifact, "1.1", manifest, constrainedEnvironment))
          .isFalse()
      }
    }
  }
}
