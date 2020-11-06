package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.SEMVER_TAG
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionApproved
import com.netflix.spinnaker.keel.test.DummyArtifactReferenceResourceSpec
import com.netflix.spinnaker.keel.test.resource
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

    val dockerArtifact = DockerArtifact(
      name = "docker",
      deliveryConfigName = "my-manifest",
      tagVersionStrategy = SEMVER_TAG,
      reference = "docker-artifact"
    )

    val debianArtifact = DebianArtifact(
      name = "debian",
      deliveryConfigName = "my-manifest",
      reference = "debian-artifact",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2"))
    )

    val environment: Environment = Environment(
      name = "test",
      resources = setOf(
        resource(
          spec = DummyArtifactReferenceResourceSpec(
            artifactReference = dockerArtifact.reference
          )
        )
      )
    )
    val deliveryConfig = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      environments = setOf(environment),
      artifacts = setOf(dockerArtifact)
    )

    val multiArtifactEnvironment = environment.copy(
      resources = environment.resources + resource(
        spec = DummyArtifactReferenceResourceSpec(
          artifactReference = debianArtifact.reference
        )
      )
    )
    val deliveryConfigWith2ArtifactTypes = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      environments = setOf(multiArtifactEnvironment),
      artifacts = setOf(dockerArtifact, debianArtifact)
    )

    val env1 = environment
    val env2 = env1.copy(name = "staging", constraints = setOf(DependsOnConstraint("test")))
    val multiEnvConfig = deliveryConfig.copy(environments = setOf(env1, env2))

    val artifactNotUsedEnvironment = environment.copy(resources = emptySet())
    val artifactNotUsedConfig = deliveryConfig.copy(environments = setOf(artifactNotUsedEnvironment))
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("no versions of an artifact exist") {
      before {
        every {
          repository.artifactVersions(dockerArtifact)
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
          repository.artifactVersions(dockerArtifact)
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
            repository.getQueuedConstraintApprovals(deliveryConfig.name, environment.name, any())
          } returns setOf("2.0")
        }

        context("the version is not already approved for the environment") {
          before {
            every {
              environmentConstraintRunner.checkStatelessConstraints(dockerArtifact, deliveryConfig, "2.0", environment)
            } returns true

            every {
              repository.approveVersionFor(deliveryConfig, dockerArtifact, "2.0", environment.name)
            } returns true

            runBlocking {
              subject.checkEnvironments(deliveryConfig)
            }
          }

          test("the environment is assigned the latest version of an artifact") {
            verify {
              repository.approveVersionFor(deliveryConfig, dockerArtifact, "2.0", environment.name)
            }
          }

          test("a telemetry event is fired") {
            verify {
              publisher.publishEvent(
                ArtifactVersionApproved(
                  deliveryConfig.application,
                  deliveryConfig.name,
                  environment.name,
                  dockerArtifact.name,
                  dockerArtifact.type,
                  "2.0"
                )
              )
            }
          }
        }

        context("the version is already approved for the environment") {
          before {
            every {
              environmentConstraintRunner.checkStatelessConstraints(dockerArtifact, deliveryConfig, "2.0", environment)
            } returns true

            every {
              repository.approveVersionFor(deliveryConfig, dockerArtifact, "2.0", environment.name)
            } returns false

            runBlocking {
              subject.checkEnvironments(deliveryConfig)
            }
          }

          test("an event is not sent") {
            verify(exactly = 0) {
              publisher.publishEvent(ofType<ArtifactVersionApproved>())
            }
          }
        }

        context("the stateless constraints no longer pass") {
          before {
            every {
              environmentConstraintRunner.checkStatelessConstraints(dockerArtifact, deliveryConfig, "2.0", environment)
            } returns false

            runBlocking {
              subject.checkEnvironments(deliveryConfig)
            }
          }

          test("nothing is approved") {
            verify(exactly = 0) {
              repository.approveVersionFor(deliveryConfig, dockerArtifact, "2.0", environment.name)
              publisher.publishEvent(ofType<ArtifactVersionApproved>())
            }
          }
        }

        context("the environment is pinned") {
          before {
            every {
              repository.pinnedEnvironments(any())
            } returns listOf(
              PinnedEnvironment(
                deliveryConfigName = deliveryConfig.name,
                targetEnvironment = environment.name,
                artifact = dockerArtifact,
                version = "1.0",
                pinnedBy = null,
                pinnedAt = null,
                comment = null
              )
            )

            every {
              repository.approveVersionFor(deliveryConfig, dockerArtifact, any(), environment.name)
            } returns true

            runBlocking {
              subject.checkEnvironments(deliveryConfig)
            }
          }

          test("constraint evaluation happens") {
            verify(exactly = 1) {
              environmentConstraintRunner.checkEnvironment(any())
            }
          }

          test("the pinned artifact is approved") {
            verify(exactly = 1) {
              repository.approveVersionFor(deliveryConfig, dockerArtifact, "1.0", environment.name)
            }
          }

          test("queued constraints aren't looked at") {
            verify(exactly = 0) {
              environmentConstraintRunner.checkStatelessConstraints(any(), any(), any(), any())
            }
          }

          test("stateless constraints for queued versions aren't rechecked") {
            verify(exactly = 0) {
              repository.getQueuedConstraintApprovals(any(), any(), any())
            }
          }
        }
      }

      context("there are several versions queued for approval") {
        before {
          every {
            repository.getQueuedConstraintApprovals(deliveryConfig.name, environment.name, dockerArtifact.reference)
          } returns setOf("2.0", "1.2", "1.1")
        }

        context("all versions still pass stateless constraints") {
          before {
            every {
              environmentConstraintRunner.checkStatelessConstraints(dockerArtifact, deliveryConfig, any(), environment)
            } returns true

            every {
              repository.approveVersionFor(deliveryConfig, dockerArtifact, any(), environment.name)
            } returns true

            runBlocking {
              subject.checkEnvironments(deliveryConfig)
            }
          }

          test("all versions get approved") {
            verify {
              repository.approveVersionFor(deliveryConfig, dockerArtifact, "2.0", environment.name)
              repository.approveVersionFor(deliveryConfig, dockerArtifact, "1.2", environment.name)
              repository.approveVersionFor(deliveryConfig, dockerArtifact, "1.1", environment.name)
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
          repository.artifactVersions(dockerArtifact)
        } returns listOf("2.0", "1.2", "1.1", "1.0")

        every {
          environmentConstraintRunner.checkStatelessConstraints(dockerArtifact, multiEnvConfig, "2.0", any())
        } returns true

        every {
          repository.approveVersionFor(multiEnvConfig, dockerArtifact, "2.0", any())
        } returns true

        every {
          repository.getQueuedConstraintApprovals(any(), any(), any())
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
            repository.approveVersionFor(multiEnvConfig, dockerArtifact, "2.0", any())
          }
        }
      }

      context("one environment is pinned") {
        before {
          every {
            repository.pinnedEnvironments(any())
          } returns listOf(
            PinnedEnvironment(
              deliveryConfigName = multiEnvConfig.name,
              targetEnvironment = env1.name,
              artifact = dockerArtifact,
              version = "2.0",
              pinnedBy = null,
              pinnedAt = null,
              comment = null
            )
          )

          runBlocking {
            subject.checkEnvironments(multiEnvConfig)
          }
        }

        test("all environments have the version approved") {
          verify(exactly = 2) {
            repository.approveVersionFor(multiEnvConfig, dockerArtifact, "2.0", any())
          }
        }

        test("all environments have constraints checked") {
          verify(exactly = 2) {
            environmentConstraintRunner.checkEnvironment(any())
          }
        }
      }
    }

    context("config contains multiple types of artifacts") {
      before {
        every {
          repository.artifactVersions(dockerArtifact)
        } returns listOf("2.0")

        every {
          repository.artifactVersions(debianArtifact)
        } returns listOf("3.0")

        every {
          repository.getQueuedConstraintApprovals(deliveryConfigWith2ArtifactTypes.name, multiArtifactEnvironment.name, dockerArtifact.reference)
        } returns setOf("2.0")

        every {
          repository.getQueuedConstraintApprovals(deliveryConfigWith2ArtifactTypes.name, multiArtifactEnvironment.name, debianArtifact.reference)
        } returns setOf("3.0")

        every {
          environmentConstraintRunner.checkStatelessConstraints(dockerArtifact, deliveryConfigWith2ArtifactTypes, "2.0", multiArtifactEnvironment)
        } returns true

        every {
          environmentConstraintRunner.checkStatelessConstraints(debianArtifact, deliveryConfigWith2ArtifactTypes, "3.0", multiArtifactEnvironment)
        } returns true

        every {
          repository.approveVersionFor(deliveryConfigWith2ArtifactTypes, dockerArtifact, "2.0", multiArtifactEnvironment.name)
        } returns true

        every {
          repository.approveVersionFor(deliveryConfigWith2ArtifactTypes, debianArtifact, "3.0", multiArtifactEnvironment.name)
        } returns true

        every {
          repository.pinnedEnvironments(any())
        } returns emptyList()

        every {
          repository.vetoedEnvironmentVersions(any())
        } returns emptyList()

        runBlocking {
          subject.checkEnvironments(deliveryConfigWith2ArtifactTypes)
        }
      }

      test("verify the right artifact type and version approved") {
        verify {
          repository.approveVersionFor(deliveryConfigWith2ArtifactTypes, dockerArtifact, "2.0", multiArtifactEnvironment.name)
          repository.approveVersionFor(deliveryConfigWith2ArtifactTypes, debianArtifact, "3.0", multiArtifactEnvironment.name)
        }
      }

      test("approved versions of other artifact types are not getting mixed up and are not checked") {
        verify(exactly = 0) {
          environmentConstraintRunner.checkStatelessConstraints(debianArtifact, deliveryConfigWith2ArtifactTypes, "2.0", multiArtifactEnvironment)
          environmentConstraintRunner.checkStatelessConstraints(dockerArtifact, deliveryConfigWith2ArtifactTypes, "3.0", multiArtifactEnvironment)
        }
      }

      context("docker artifact have more than one approved version") {
        before {
          every {
            repository.artifactVersions(dockerArtifact)
          } returns listOf("2.0", "1.1")
          every {
            repository.getQueuedConstraintApprovals(deliveryConfigWith2ArtifactTypes.name, multiArtifactEnvironment.name, dockerArtifact.reference)
          } returns setOf("2.0", "1.1")
          every {
            repository.approveVersionFor(deliveryConfigWith2ArtifactTypes, dockerArtifact, "1.1", multiArtifactEnvironment.name)
          } returns true

          every {
            environmentConstraintRunner.checkStatelessConstraints(dockerArtifact, deliveryConfigWith2ArtifactTypes, "1.1", multiArtifactEnvironment)
          } returns true

          runBlocking {
            subject.checkEnvironments(deliveryConfigWith2ArtifactTypes)
          }
        }

        test("2 docker versions are approved and 1 debian") {
          verify {
            repository.approveVersionFor(deliveryConfigWith2ArtifactTypes, dockerArtifact, "2.0", multiArtifactEnvironment.name)
            repository.approveVersionFor(deliveryConfigWith2ArtifactTypes, dockerArtifact, "1.1", multiArtifactEnvironment.name)
            repository.approveVersionFor(deliveryConfigWith2ArtifactTypes, debianArtifact, "3.0", multiArtifactEnvironment.name)
          }
        }

        test("verify no mixup of artifact type and version") {
          verify(exactly = 0) {
            environmentConstraintRunner.checkStatelessConstraints(dockerArtifact, deliveryConfigWith2ArtifactTypes, "3.0", multiArtifactEnvironment)
          }
        }
      }
    }

    context("an artifact is not used in an environment") {
      before {
        every {
          repository.artifactVersions(dockerArtifact)
        } returns listOf("2.0", "1.2", "1.1", "1.0")

        every {
          repository.pinnedEnvironments(any())
        } returns emptyList()

        every {
          repository.vetoedEnvironmentVersions(any())
        } returns emptyList()

        runBlocking {
          subject.checkEnvironments(artifactNotUsedConfig)
        }
      }

      test("constraint checks are skipped for that environment") {
        verify(exactly = 0) {
          environmentConstraintRunner.checkEnvironment(any())
        }
      }
    }
  }
}
