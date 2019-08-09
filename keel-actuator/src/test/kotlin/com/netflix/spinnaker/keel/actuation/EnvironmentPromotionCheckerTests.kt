package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DependsOnConstraint
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.PromotionRepository
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking

internal class EnvironmentPromotionCheckerTests : JUnit5Minutests {

  data class Fixture(
    val environment: Environment = Environment(
      name = "test"
    )
  ) {
    val artifactRepository = mockk<ArtifactRepository>()
    val promotionRepository = mockk<PromotionRepository>(relaxUnitFun = true)
    val constraintEvaluator = mockk<ConstraintEvaluator<*>>() {
      every { constraintType } returns DependsOnConstraint::class.java
    }
    val subject = EnvironmentPromotionChecker(
      artifactRepository,
      promotionRepository,
      listOf(constraintEvaluator)
    )

    val artifact = DeliveryArtifact(
      name = "fnord",
      type = DEB
    )
    val deliveryConfig = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      environments = setOf(environment),
      artifacts = setOf(artifact)
    )
  }

  fun tests() = rootContext<Fixture> {
    context("multiple versions of an artifact exist") {
      fixture {
        Fixture()
      }

      before {
        every {
          artifactRepository.versions(artifact)
        } returns listOf("2.0", "1.2", "1.1", "1.0")
      }

      context("there are no constraints on the environment") {
        before {
          runBlocking {
            subject.checkEnvironments(deliveryConfig)
          }
        }

        test("the environment is assigned the latest version of an artifact") {
          verify {
            promotionRepository.approveVersionFor(deliveryConfig, artifact, "2.0", environment.name)
          }
        }
      }

      context("the environment has constraints") {
        deriveFixture {
          copy(environment = Environment(
            name = "staging",
            constraints = setOf(DependsOnConstraint("test"))
          ))
        }

        before {
          // TODO: sucks that this is necessary but when using deriveFixture you get a different mockk
          every {
            artifactRepository.versions(artifact)
          } returns listOf("2.0", "1.2", "1.1", "1.0")

          every { constraintEvaluator.canPromote(artifact, "2.0", deliveryConfig, environment.name) } returns false
          every { constraintEvaluator.canPromote(artifact, "1.2", deliveryConfig, environment.name) } returns true
          every { constraintEvaluator.canPromote(artifact, "1.1", deliveryConfig, environment.name) } returns true
          every { constraintEvaluator.canPromote(artifact, "1.0", deliveryConfig, environment.name) } returns true

          runBlocking {
            subject.checkEnvironments(deliveryConfig)
          }
        }

        test("the environment is assigned the latest version of an artifact that passes the constraint") {
          verify {
            promotionRepository.approveVersionFor(deliveryConfig, artifact, "1.2", environment.name)
          }
        }
      }
    }
  }
}
