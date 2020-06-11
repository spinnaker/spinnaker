package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionApproved
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectCatching
import strikt.assertions.isSuccess

internal class NewEnvironmentPromotionCheckerTests : JUnit5Minutests {
  class Fixture {
    val repository: KeelRepository = mockk(relaxUnitFun = true)

    val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)

    val environmentConstraintRunner: EnvironmentConstraintRunner = mockk(relaxed = true)
    val subject = EnvironmentPromotionChecker(
      repository,
      environmentConstraintRunner,
      publisher
    )

    val artifact = DockerArtifact(
      name = "fnord",
      deliveryConfigName = "my-manifest",
      tagVersionStrategy = SEMVER_TAG
    )
    val environment: Environment = Environment(
      name = "test"
    )
    val deliveryConfig = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      environments = setOf(environment),
      artifacts = setOf(artifact)
    )

    val env1 = environment
    val env2 = env1.copy(name = "staging", constraints = setOf(DependsOnConstraint("test")))
    val multiEnvConfig = deliveryConfig.copy(environments = setOf(env1, env2))
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("no versions of an artifact exist") {
      before {
        every {
          repository.artifactVersions(artifact)
        } returns emptyList()

        every {
          repository.pinnedEnvironments(any())
        } returns emptyList()

        every {
          repository.vetoedEnvironmentVersions(any())
        } returns emptyList()
      }

      test("the check does not throw an exception") {
        expectCatching {
          subject.checkEnvironments(deliveryConfig)
        }
          .isSuccess()
      }
    }

    context("multiple versions of an artifact exist") {
      before {
        every {
          repository.artifactVersions(artifact)
        } returns listOf("2.0", "1.2", "1.1", "1.0")

        every {
          repository.pinnedEnvironments(any())
        } returns emptyList()

        every {
          repository.vetoedEnvironmentVersions(any())
        } returns emptyList()
      }

      context("a single new version is queued for approval") {
        before {
          every {
            repository.getQueuedConstraintApprovals(deliveryConfig.name, environment.name)
          } returns setOf("2.0")
        }

        context("the version is not already approved for the environment") {
          before {
            every {
              environmentConstraintRunner.checkStatelessConstraints(artifact, deliveryConfig, "2.0", environment)
            } returns true

            every {
              repository.approveVersionFor(deliveryConfig, artifact, "2.0", environment.name)
            } returns true

            runBlocking {
              subject.checkEnvironments(deliveryConfig)
            }
          }

          test("the environment is assigned the latest version of an artifact") {
            verify {
              repository.approveVersionFor(deliveryConfig, artifact, "2.0", environment.name)
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

        context("the version is already approved for the environment") {
          before {
            every {
              environmentConstraintRunner.checkStatelessConstraints(artifact, deliveryConfig, "2.0", environment)
            } returns true

            every {
              repository.approveVersionFor(deliveryConfig, artifact, "2.0", environment.name)
            } returns false

            runBlocking {
              subject.checkEnvironments(deliveryConfig)
            }
          }

          test("an event is not sent") {
            verify(exactly = 0) {
              publisher.publishEvent(any<ArtifactVersionApproved>())
            }
          }
        }

        context("the stateless constraints no longer pass") {
          before {
            every {
              environmentConstraintRunner.checkStatelessConstraints(artifact, deliveryConfig, "2.0", environment)
            } returns false

            runBlocking {
              subject.checkEnvironments(deliveryConfig)
            }
          }

          test("nothing is approved") {
            verify(exactly = 0) {
              repository.approveVersionFor(deliveryConfig, artifact, "2.0", environment.name)
              publisher.publishEvent(any<ArtifactVersionApproved>())
            }
          }
        }

        context("the environment is pinned") {
          before {
            every {
              repository.pinnedEnvironments(any())
            } returns listOf(PinnedEnvironment(
              deliveryConfigName = deliveryConfig.name,
              targetEnvironment = environment.name,
              artifact = artifact,
              version = "1.0",
              pinnedBy = null,
              pinnedAt = null,
              comment = null
            ))

            every {
              repository.approveVersionFor(deliveryConfig, artifact, any(), environment.name)
            } returns true

            runBlocking {
              subject.checkEnvironments(deliveryConfig)
            }
          }

          test("no constraint evaluation happens") {
            verify(exactly = 0) {
              environmentConstraintRunner.checkEnvironment(any())
              environmentConstraintRunner.checkStatelessConstraints(any(), any(), any(), any())
              repository.getQueuedConstraintApprovals(any(), any())
            }
          }

          test("the artifact is approved") {
            verify(exactly = 1) {
              repository.approveVersionFor(deliveryConfig, artifact, "1.0", environment.name)
            }
          }
        }
      }

      context("there are several versions queued for approval") {
        before {
          every {
            repository.getQueuedConstraintApprovals(deliveryConfig.name, environment.name)
          } returns setOf("2.0", "1.2", "1.1")
        }

        context("all versions still pass stateless constraints") {
          before {
            every {
              environmentConstraintRunner.checkStatelessConstraints(artifact, deliveryConfig, any(), environment)
            } returns true

            every {
              repository.approveVersionFor(deliveryConfig, artifact, any(), environment.name)
            } returns true

            runBlocking {
              subject.checkEnvironments(deliveryConfig)
            }
          }

          test("all versions get approved") {
            verify {
              repository.approveVersionFor(deliveryConfig, artifact, "2.0", environment.name)
              repository.approveVersionFor(deliveryConfig, artifact, "1.2", environment.name)
              repository.approveVersionFor(deliveryConfig, artifact, "1.1", environment.name)
            }
          }
        }
      }
    }

    context("multiple environments exist") {
      before {
        every {
          repository.vetoedEnvironmentVersions(any())
        } returns emptyList()

        every {
          repository.artifactVersions(artifact)
        } returns listOf("2.0", "1.2", "1.1", "1.0")

        every {
          environmentConstraintRunner.checkStatelessConstraints(artifact, multiEnvConfig, "2.0", any())
        } returns true

        every {
          repository.approveVersionFor(multiEnvConfig, artifact, "2.0", any())
        } returns true

        every {
          repository.getQueuedConstraintApprovals(any(), any())
        } returns setOf("2.0")
      }

      context("there are no pins") {
        before {
          every {
            repository.pinnedEnvironments(any())
          } returns emptyList()

          runBlocking {
            subject.checkEnvironments(multiEnvConfig)
          }
        }

        test("all environments have the version approved") {
          verify(exactly = 2) {
            repository.approveVersionFor(multiEnvConfig, artifact, "2.0", any())
          }
        }
      }

      context("one environment is pinned") {
        before {
          every {
            repository.pinnedEnvironments(any())
          } returns listOf(PinnedEnvironment(
            deliveryConfigName = multiEnvConfig.name,
            targetEnvironment = env1.name,
            artifact = artifact,
            version = "2.0",
            pinnedBy = null,
            pinnedAt = null,
            comment = null
          ))

          runBlocking {
            subject.checkEnvironments(multiEnvConfig)
          }
        }

        test("all environments have the version approved") {
          verify(exactly = 2) {
            repository.approveVersionFor(multiEnvConfig, artifact, "2.0", any())
          }
        }
      }
    }
  }
}
