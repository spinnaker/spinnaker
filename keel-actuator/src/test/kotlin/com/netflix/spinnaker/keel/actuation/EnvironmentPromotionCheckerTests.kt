package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.ConstraintStatus
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DependsOnConstraint
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.api.PinnedEnvironment
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
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
    // TODO: add stateful constraint specific tests
    val deliveryConfigRepository = mockk<DeliveryConfigRepository>(relaxUnitFun = true)
    val statelessEvaluator = mockk<ConstraintEvaluator<*>>() {
      every { supportedType } returns SupportedConstraintType<DependsOnConstraint>("depends-on")
    }
    val statefulEvaluator = mockk<ConstraintEvaluator<*>>() {
      every { supportedType } returns SupportedConstraintType<ManualJudgementConstraint>("manual-judgment")
    }
    val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
    val subject = EnvironmentPromotionChecker(
      artifactRepository,
      listOf(statelessEvaluator, statefulEvaluator),
      publisher
    )

    val artifact = DebianArtifact(name = "fnord")
    val deliveryConfig = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
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
        every {
          artifactRepository.pinnedEnvironments(any())
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

        every {
          artifactRepository.pinnedEnvironments(any())
        } returns emptyList()
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
            constraints = setOf(DependsOnConstraint("test"), ManualJudgementConstraint())
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

          every {
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "1.1", environment.name)
          } returns true

          every {
            artifactRepository.pinnedEnvironments(any())
          } returns emptyList()

          every {
            deliveryConfigRepository.getConstraintState(any(), any(), "2.0", "manual-judgement")
          } returns ConstraintState(
            deliveryConfig.name,
            environment.name,
            "2.0",
            "manual-judgement",
            ConstraintStatus.PENDING
          )

          every {
            deliveryConfigRepository.getConstraintState(any(), any(), "1.2", "manual-judgement")
          } returns ConstraintState(
            deliveryConfig.name,
            environment.name,
            "1.2",
            "manual-judgement",
            ConstraintStatus.PENDING
          )

          every { statelessEvaluator.canPromote(artifact, "2.0", deliveryConfig, environment) } returns false
          every { statelessEvaluator.canPromote(artifact, "1.2", deliveryConfig, environment) } returns true
          every { statelessEvaluator.canPromote(artifact, "1.1", deliveryConfig, environment) } returns true
          every { statelessEvaluator.canPromote(artifact, "1.0", deliveryConfig, environment) } returns true
          every { statefulEvaluator.canPromote(artifact, "2.0", deliveryConfig, environment) } returns false
          every { statefulEvaluator.canPromote(artifact, "1.2", deliveryConfig, environment) } returns true

          runBlocking {
            subject.checkEnvironments(deliveryConfig)
          }
        }

        test("the environment is assigned the latest version of an artifact that passes the constraint") {
          verify {
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "1.2", environment.name)
            statefulEvaluator.canPromote(artifact, "1.2", deliveryConfig, environment)
          }

          /**
           * Verify that stateful constraints are not checked if a stateless constraint blocks promotion
           */
          verify(inverse = true) {
            statefulEvaluator.canPromote(artifact, "2.0", deliveryConfig, environment)
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

      context("the environment has a pinned artifact version") {
        deriveFixture {
          copy(environment = Environment(
            name = "staging",
            constraints = setOf(DependsOnConstraint("test"), ManualJudgementConstraint())
          ))
        }
        before {
          every {
            artifactRepository.versions(artifact)
          } returns listOf("2.0", "1.2", "1.1", "1.0")

          every {
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "1.1", environment.name)
          } returns true

          every {
            artifactRepository.pinnedEnvironments(any())
          } returns listOf(PinnedEnvironment(deliveryConfig.name, environment.name, artifact, "1.1"))

          runBlocking {
            subject.checkEnvironments(deliveryConfig)
          }
        }

        test("the pinned version is picked up and constraint evaluation bypassed") {
          verify(exactly = 1) {
            // 1.1 == the older but pinned version
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "1.1", environment.name)
          }
          verify(inverse = true) {
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "1.2", environment.name)
            statelessEvaluator.canPromote(any(), any(), any(), any())
            statefulEvaluator.canPromote(any(), any(), any(), any())
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

          every { artifactRepository.pinnedEnvironments(any()) } returns emptyList()

          every { statelessEvaluator.canPromote(artifact, "1.0", deliveryConfig, environment) } returns false
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
