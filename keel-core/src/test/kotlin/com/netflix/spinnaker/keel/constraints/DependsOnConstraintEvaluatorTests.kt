package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DependsOnConstraint
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.persistence.PromotionRepository
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.failed
import strikt.assertions.isA
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class DependsOnConstraintEvaluatorTests : JUnit5Minutests {

  object Fixture {
    val artifact = DeliveryArtifact("fnord", DEB)
    val constrainedEnvironment = Environment(
      name = "staging",
      constraints = setOf(
        DependsOnConstraint("test")
      )
    )
    val previousEnvironment = Environment(
      name = "test"
    )
    val manifest = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      artifacts = setOf(artifact),
      environments = setOf(previousEnvironment, constrainedEnvironment)
    )

    val promotionRepository: PromotionRepository = mockk(relaxUnitFun = true)

    val subject = DependsOnConstraintEvaluator(promotionRepository)
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    test("an invalid environment name causes an exception") {
      expectCatching {
        subject.canPromote(artifact, "1.1", manifest, "foo")
      }
        .failed()
        .isA<IllegalArgumentException>()
    }

    test("an environment without the constraint throws an exception (don't pass it to this method)") {
      expectCatching { subject.canPromote(artifact, "1.1", manifest, previousEnvironment.name) }
        .failed()
    }

    context("the requested version is not in the required environment") {
      before {
        every {
          promotionRepository.wasSuccessfullyDeployedTo(manifest, artifact, "1.1", previousEnvironment.name)
        } returns false
      }

      test("promotion is vetoed") {
        expectThat(subject.canPromote(artifact, "1.1", manifest, constrainedEnvironment.name))
          .isFalse()
      }
    }

    context("the requested version is in the required environment") {
      before {
        every {
          promotionRepository.wasSuccessfullyDeployedTo(manifest, artifact, "1.1", previousEnvironment.name)
        } returns true
      }

      test("promotion is allowed") {
        expectThat(subject.canPromote(artifact, "1.1", manifest, constrainedEnvironment.name))
          .isTrue()
      }
    }
  }
}
