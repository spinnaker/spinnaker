package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.api.verification.VerificationState
import com.netflix.spinnaker.keel.api.verification.VerificationStatus
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isA
import strikt.assertions.isFailure
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class DependsOnConstraintEvaluatorTests : JUnit5Minutests {

  object Fixture {
    val artifact = DebianArtifact("fnord", vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")))
    val constrainedEnvironment = Environment(
      name = "staging",
      constraints = setOf(
        DependsOnConstraint(environment = "test")
      )
    )
    val previousEnvironment = Environment(
      name = "test"
    )
    val manifest = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = setOf(previousEnvironment, constrainedEnvironment)
    )

    val artifactRepository: ArtifactRepository = mockk(relaxUnitFun = true)

    val verificationRepository : VerificationRepository = mockk()

    val subject = DependsOnConstraintEvaluator(artifactRepository, verificationRepository, mockk())
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    before {
      every {
        verificationRepository.getStates(any())
      } returns emptyMap()
    }

    test("an invalid environment name causes an exception") {
      expectCatching {
        subject.canPromote(artifact, "1.1", manifest, Environment(name = "foo"))
      }
        .isFailure()
        .isA<IllegalArgumentException>()
    }

    test("an environment without the constraint throws an exception (don't pass it to this method)") {
      expectCatching { subject.canPromote(artifact, "1.1", manifest, previousEnvironment) }
        .isFailure()
    }

    test("constraint serializes with type information") {
      val mapper = configuredObjectMapper()
      val serialized = mapper.writeValueAsString(fixture.constrainedEnvironment.constraints)
      expectThat(serialized)
        .contains("depends-on")
    }

    context("the requested version is not in the required environment") {
      before {
        every {
          artifactRepository.wasSuccessfullyDeployedTo(manifest, artifact, "1.1", previousEnvironment.name)
        } returns false
      }

      test("promotion is vetoed") {
        expectThat(subject.canPromote(artifact, "1.1", manifest, constrainedEnvironment))
          .isFalse()
      }
    }

    context("the requested version is in the required environment") {
      before {
        every {
          artifactRepository.wasSuccessfullyDeployedTo(manifest, artifact, "1.1", previousEnvironment.name)
        } returns true
      }

      test("promotion is allowed") {
        expectThat(subject.canPromote(artifact, "1.1", manifest, constrainedEnvironment))
          .isTrue()
      }
    }
  }
}
