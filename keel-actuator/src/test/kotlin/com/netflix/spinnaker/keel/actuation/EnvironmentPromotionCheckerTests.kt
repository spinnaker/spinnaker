package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DependsOnConstraint
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionApproved
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectCatching
import strikt.assertions.succeeded

internal class EnvironmentPromotionCheckerTests : JUnit5Minutests {

  data class Fixture(
    val environment: Environment = Environment(
      name = "test"
    )
  ) {
    val artifactRepository = mockk<ArtifactRepository>(relaxUnitFun = true)
    val constraintEvaluator = mockk<ConstraintEvaluator<*>>() {
      every { constraintType } returns DependsOnConstraint::class.java
    }
    val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
    val subject = EnvironmentPromotionChecker(
      artifactRepository,
      listOf(constraintEvaluator),
      publisher
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
    fixture {
      Fixture()
    }

    context("no versions of an artifact exist") {
      before {
        every {
          artifactRepository.versions(artifact)
        } returns emptyList()
      }

      test("the check does not throw an exception") {
        expectCatching {
          subject.checkEnvironments(deliveryConfig)
        }
          .succeeded()
      }
    }

    context("multiple versions of an artifact exist") {
      before {
        every {
          artifactRepository.versions(artifact)
        } returns listOf("2.0", "1.2", "1.1", "1.0")

        every {
          artifactRepository.approveVersionFor(deliveryConfig, artifact, "2.0", environment.name)
        } returns true
      }

      context("there are no constraints on the environment") {
        before {
          runBlocking {
            subject.checkEnvironments(deliveryConfig)
          }
        }

        test("the environment is assigned the latest version of an artifact") {
          verify {
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "2.0", environment.name)
          }
        }

        test("a telemetry event is fired") {
          verify {
            publisher.publishEvent(ArtifactVersionApproved(
              deliveryConfig.application,
              deliveryConfig.name,
              environment.name,
              artifact.name,
              artifact.type,
              "2.0"
            ))
          }
        }
      }

      context("the latest version of the artifact was already approved for this environment") {
        before {
          every {
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "2.0", environment.name)
          } returns false

          runBlocking {
            subject.checkEnvironments(deliveryConfig)
          }
        }

        test("no telemetry event is fired") {
          verify(exactly = 0) {
            publisher.publishEvent(ofType<ArtifactVersionApproved>())
          }
        }
      }

      context("the environment has constraints and a version can be found") {
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

          every {
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "1.2", environment.name)
          } returns true

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
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "1.2", environment.name)
          }
        }

        test("a telemetry event is fired") {
          verify {
            publisher.publishEvent(ArtifactVersionApproved(
              deliveryConfig.application,
              deliveryConfig.name,
              environment.name,
              artifact.name,
              artifact.type,
              "1.2"
            ))
          }
        }
      }

      context("the environment has constraints and a version cannot be found") {
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
          } returns listOf("1.0")

          every { constraintEvaluator.canPromote(artifact, "1.0", deliveryConfig, environment.name) } returns false
        }

        test("no exception is thrown") {
          expectCatching {
            subject.checkEnvironments(deliveryConfig)
          }
            .succeeded()
        }

        test("no artifact is registered") {
          verify(exactly = 0) {
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "1.0", environment.name)
          }
        }
      }
    }
  }
}
