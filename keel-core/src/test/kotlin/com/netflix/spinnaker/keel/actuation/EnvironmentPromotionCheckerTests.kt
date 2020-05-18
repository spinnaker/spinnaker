package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.constraints.ArtifactUsedConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.ConstraintState
import com.netflix.spinnaker.keel.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.core.api.ArtifactUsedConstraint
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.PinnedEnvironment
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
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

internal class EnvironmentPromotionCheckerTests : JUnit5Minutests {

  data class Fixture(
    val environment: Environment = Environment(
      name = "test"
    )
  ) {
    val repository: KeelRepository = mockk(relaxUnitFun = true)

    // TODO: add stateful constraint specific tests
    val deliveryConfigRepository = mockk<DeliveryConfigRepository>(relaxUnitFun = true)
    val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
    val statelessEvaluator = mockk<ConstraintEvaluator<*>>() {
      every { supportedType } returns SupportedConstraintType<DependsOnConstraint>("depends-on")
      every { isImplicit() } returns false
    }
    val statefulEvaluator = mockk<StatefulConstraintEvaluator<*>>() {
      every { supportedType } returns SupportedConstraintType<ManualJudgementConstraint>("manual-judegment")
      every { isImplicit() } returns false
    }
    val implicitStatelessEvaluator = mockk<ArtifactUsedConstraintEvaluator>() {
      every { supportedType } returns SupportedConstraintType<ArtifactUsedConstraint>("artifact-type")
      every { isImplicit() } returns true
      every { canPromote(any(), any(), any(), any()) } returns true
    }
    val subject = EnvironmentPromotionChecker(
      repository,
      listOf(statelessEvaluator, statefulEvaluator, implicitStatelessEvaluator),
      publisher
    )

    val artifact = DebianArtifact(
      name = "fnord",
      deliveryConfigName = "my-manifest",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2"))
    )
    val deliveryConfig = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      environments = setOf(environment),
      artifacts = setOf(artifact)
    )

    val pendingManualJudgement = ConstraintState(
      deliveryConfig.name,
      environment.name,
      "2.0",
      "manual-judgement",
      ConstraintStatus.PENDING
    )

    val passedManualJudgement = ConstraintState(
      deliveryConfig.name,
      environment.name,
      "1.2",
      "manual-judgement",
      ConstraintStatus.OVERRIDE_PASS
    )
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
          repository.approveVersionFor(deliveryConfig, artifact, "2.0", environment.name)
        } returns true

        every {
          repository.pinnedEnvironments(any())
        } returns emptyList()

        every {
          repository.vetoedEnvironmentVersions(any())
        } returns emptyList()

        every {
          repository.pendingConstraintVersionsFor(any(), any())
        } returns emptyList()
      }

      context("there are no constraints on the environment") {
        before {
          runBlocking {
            subject.checkEnvironments(deliveryConfig)
          }

          every {
            repository.vetoedEnvironmentVersions(any())
          } returns emptyList()
        }

        test("the implicit constraint is checked") {
          verify {
            implicitStatelessEvaluator.canPromote(artifact, "2.0", deliveryConfig, environment)
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

      context("the latest version of the artifact was already approved for this environment") {
        before {
          every {
            repository.approveVersionFor(deliveryConfig, artifact, "2.0", environment.name)
          } returns false

          every {
            repository.vetoedEnvironmentVersions(any())
          } returns emptyList()

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
            repository.artifactVersions(artifact)
          } returns listOf("2.0", "1.2", "1.1", "1.0")

          every {
            repository.approveVersionFor(deliveryConfig, artifact, "1.2", environment.name)
          } returns true

          every {
            repository.approveVersionFor(deliveryConfig, artifact, "1.1", environment.name)
          } returns true

          every {
            repository.vetoedEnvironmentVersions(any())
          } returns emptyList()

          every {
            repository.pinnedEnvironments(any())
          } returns emptyList()

          every {
            deliveryConfigRepository.getConstraintState(any(), any(), "2.0", "manual-judgement")
          } returns pendingManualJudgement

          every {
            deliveryConfigRepository.getConstraintState(any(), any(), "1.2", "manual-judgement")
          } returns passedManualJudgement

          every {
            repository.getQueuedConstraintApprovals(any(), any())
          } returns setOf("1.2")

          every {
            repository.pendingConstraintVersionsFor(any(), any())
          } returns listOf("2.0")

          every {
            repository.constraintStateFor("my-manifest", "staging", "1.2")
          } returns listOf(passedManualJudgement)

          every {
            repository.constraintStateFor("my-manifest", "staging", "2.0")
          } returns listOf(pendingManualJudgement)

          every { statelessEvaluator.canPromote(artifact, "2.0", deliveryConfig, environment) } returns false
          every { statelessEvaluator.canPromote(artifact, "1.2", deliveryConfig, environment) } returns true
          every { statefulEvaluator.canPromote(artifact, "2.0", deliveryConfig, environment) } returns false
          every { statefulEvaluator.canPromote(artifact, "1.2", deliveryConfig, environment) } returns true

          every { repository.latestVersionApprovedIn(deliveryConfig, artifact, environment.name) } returns "1.2"

          runBlocking {
            subject.checkEnvironments(deliveryConfig)
          }
        }

        test("the implicit constraint is checked") {
          verify {
            implicitStatelessEvaluator.canPromote(artifact, "2.0", deliveryConfig, environment)
          }
        }

        test("the environment is assigned the latest version of an artifact that passes the constraint") {
          verify {
            repository.approveVersionFor(deliveryConfig, artifact, "1.2", environment.name)
          }

          /**
           * Verify that stateful constraints are not checked if a stateless constraint blocks promotion
           */
          verify(exactly = 1) {
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
            repository.artifactVersions(artifact)
          } returns listOf("2.0", "1.2", "1.1", "1.0")

          every {
            repository.approveVersionFor(deliveryConfig, artifact, "1.1", environment.name)
          } returns true

          every {
            repository.vetoedEnvironmentVersions(any())
          } returns emptyList()

          every {
            repository.pinnedEnvironments(any())
          } returns listOf(PinnedEnvironment(deliveryConfig.name, environment.name, artifact, "1.1", null, null, null))

          every {
            repository.pendingConstraintVersionsFor(any(), any())
          } returns emptyList()

          every {
            repository.getQueuedConstraintApprovals(any(), any())
          } returns emptySet()

          runBlocking {
            subject.checkEnvironments(deliveryConfig)
          }
        }

        test("the pinned version is picked up and constraint evaluation bypassed") {
          verify(exactly = 1) {
            // 1.1 == the older but pinned version
            repository.approveVersionFor(deliveryConfig, artifact, "1.1", environment.name)
          }
          verify(inverse = true) {
            repository.approveVersionFor(deliveryConfig, artifact, "1.2", environment.name)
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
            repository.artifactVersions(artifact)
          } returns listOf("1.0")

          every {
            repository.vetoedEnvironmentVersions(any())
          } returns emptyList()

          every { repository.pinnedEnvironments(any()) } returns emptyList()

          every {
            repository.getQueuedConstraintApprovals(any(), any())
          } returns emptySet()

          every { statelessEvaluator.canPromote(artifact, "1.0", deliveryConfig, environment) } returns false

          runBlocking {
            subject.checkEnvironments(deliveryConfig)
          }
        }

        test("no exception is thrown") {
          expectCatching {
            subject.checkEnvironments(deliveryConfig)
          }
            .isSuccess()
        }

        test("no artifact is registered") {
          verify(exactly = 0) {
            repository.approveVersionFor(deliveryConfig, artifact, any(), environment.name)
          }
        }
      }

      context("the environment has a stateful constraint and a version cannot be found") {
        deriveFixture {
          copy(environment = Environment(
            name = "staging",
            constraints = setOf(ManualJudgementConstraint())
          ))
        }

        before {
          // TODO: sucks that this is necessary but when using deriveFixture you get a different mockk
          every { repository.artifactVersions(artifact) } returns listOf("2.0", "1.2", "1.1")

          every { repository.vetoedEnvironmentVersions(any()) } returns emptyList()

          every { repository.pinnedEnvironments(any()) } returns emptyList()

          every { repository.vetoedEnvironmentVersions(any()) } returns emptyList()

          every { repository.pendingConstraintVersionsFor(any(), any()) } returns emptyList()

          every { repository.getQueuedConstraintApprovals(any(), any()) } returns emptySet()

          every { statefulEvaluator.canPromote(artifact, "2.0", deliveryConfig, environment) } returns false

          every {
            repository.constraintStateFor("my-manifest", "staging", "2.0")
          } returns listOf(pendingManualJudgement)

          every { repository.latestVersionApprovedIn(any(), any(), any()) } returns null

          runBlocking { subject.checkEnvironments(deliveryConfig) }
        }

        test("stateful constraints are only evaluated for the most recent version") {
          verify(exactly = 1) {
            statefulEvaluator.canPromote(artifact, "2.0", deliveryConfig, environment)
          }
          verify(exactly = 0) {
            statefulEvaluator.canPromote(artifact, "1.2", deliveryConfig, environment)
            statefulEvaluator.canPromote(artifact, "1.1", deliveryConfig, environment)
          }
        }

        test("no artifact is approved") {
          verify(exactly = 0) {
            repository.approveVersionFor(deliveryConfig, artifact, any(), environment.name)
          }
        }

        test("no exception is thrown") {
          expectCatching {
            subject.checkEnvironments(deliveryConfig)
          }
            .isSuccess()
        }
      }

      context("a new artifact passes stateful constraints while older versions are pending") {
        deriveFixture {
          copy(environment = Environment(
            name = "staging",
            constraints = setOf(DependsOnConstraint("test"), ManualJudgementConstraint())
          ))
        }

        before {
          // TODO: sucks that this is necessary but when using deriveFixture you get a different mockk
          every { repository.artifactVersions(artifact) } returns listOf("2.0", "1.2", "1.1", "1.0", "0.9")

          every { repository.vetoedEnvironmentVersions(any()) } returns emptyList()

          every { repository.pinnedEnvironments(any()) } returns emptyList()

          every { repository.vetoedEnvironmentVersions(any()) } returns emptyList()

          every { repository.pendingConstraintVersionsFor(any(), any()) } returns listOf("1.2", "1.1")

          every { repository.getQueuedConstraintApprovals(any(), any()) } returns setOf("1.0")

          every { statefulEvaluator.canPromote(any(), "2.0", any(), any()) } returns false
          every { statefulEvaluator.canPromote(any(), "1.2", any(), any()) } returns true
          every { statefulEvaluator.canPromote(any(), "1.1", any(), any()) } returns false
          every { statelessEvaluator.canPromote(any(), "2.0", any(), any()) } returns true
          every { statelessEvaluator.canPromote(any(), "1.2", any(), any()) } returns true
          every { statelessEvaluator.canPromote(any(), "1.1", any(), any()) } returns true
          every { statelessEvaluator.canPromote(any(), "1.0", any(), any()) } returns true

          every { repository.approveVersionFor(deliveryConfig, artifact, "1.2", environment.name) } returns true
          every { repository.approveVersionFor(deliveryConfig, artifact, "1.0", environment.name) } returns true

          every {
            repository.constraintStateFor("my-manifest", "staging", "2.0")
          } returns listOf(pendingManualJudgement)

          runBlocking { subject.checkEnvironments(deliveryConfig) }
        }

        test("pending versions are checked and approved if passed") {
          verify(exactly = 1) {
            statefulEvaluator.canPromote(artifact, "1.2", deliveryConfig, environment)
            statefulEvaluator.canPromote(artifact, "1.1", deliveryConfig, environment)
          }

          verify(exactly = 1) {
            repository.approveVersionFor(deliveryConfig, artifact, "1.2", environment.name)
          }

          verify(exactly = 0) {
            repository.approveVersionFor(deliveryConfig, artifact, "2.0", environment.name)
            repository.approveVersionFor(deliveryConfig, artifact, "1.1", environment.name)
          }
        }

        test("versions with queued approvals are approved without invoking stateful constraint handlers") {
          verify(exactly = 1) {
            repository.approveVersionFor(deliveryConfig, artifact, "1.0", environment.name)
            statelessEvaluator.canPromote(artifact, "1.0", deliveryConfig, environment)
          }
          verify(exactly = 0) {
            statefulEvaluator.canPromote(artifact, "1.0", deliveryConfig, environment)
          }
        }
      }
    }
  }
}
