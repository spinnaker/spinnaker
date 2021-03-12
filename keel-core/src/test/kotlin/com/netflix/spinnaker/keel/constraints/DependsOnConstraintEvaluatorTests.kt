package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.verification.VerificationRepository
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
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
    val clock = MutableClock()

    val subject = DependsOnConstraintEvaluator(artifactRepository, verificationRepository, mockk(), clock)
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

    context("generating constraint state") {
      test("can get state") {
        val state = subject.generateConstraintStateSnapshot(artifact = artifact, version = "1.1", deliveryConfig = manifest, targetEnvironment = constrainedEnvironment)
        expectThat(state)
          .and { get { type }.isEqualTo("depends-on") }
          .and { get { status }.isEqualTo(ConstraintStatus.PASS) }
          .and { get { judgedAt }.isNotNull() }
          .and { get { judgedBy }.isNotNull() }
          .and { get { attributes }.isNotNull() }
      }
    }
  }
}
