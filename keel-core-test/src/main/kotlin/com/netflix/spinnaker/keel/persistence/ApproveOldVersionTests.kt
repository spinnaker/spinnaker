package com.netflix.spinnaker.keel.persistence

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.jsontype.NamedType
import com.netflix.spinnaker.keel.actuation.EnvironmentPromotionChecker
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.plugins.kind
import com.netflix.spinnaker.keel.constraints.ArtifactUsedConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.ConstraintState
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.constraints.ManualJudgementConstraintEvaluator
import com.netflix.spinnaker.keel.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.core.api.ArtifactUsedConstraint
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.isEqualTo

abstract class ApproveOldVersionTests<T : KeelRepository> : JUnit5Minutests {

  abstract fun createKeelRepository(resourceSpecIdentifier: ResourceSpecIdentifier, mapper: ObjectMapper): T

  open fun flush() {}

  class Fixture<T : KeelRepository>(
    val repositoryProvider: (ResourceSpecIdentifier, ObjectMapper) -> T
  ) {

    val mapper: ObjectMapper = configuredObjectMapper().apply {
      registerSubtypes(NamedType(ManualJudgementConstraint::class.java, "manual-judgement"))
    }

    private val resourceSpecIdentifier: ResourceSpecIdentifier =
      ResourceSpecIdentifier(
        kind<DummyResourceSpec>("ec2/security-group@v1"),
        kind<DummyResourceSpec>("ec2/cluster@v1")
      )

    internal val repository = repositoryProvider(resourceSpecIdentifier, mapper)

    val environment: Environment = Environment(
      name = "test",
      constraints = setOf(ManualJudgementConstraint())
    )

    val publisher = mockk<ApplicationEventPublisher>(relaxUnitFun = true)
    val statelessEvaluator = mockk<ConstraintEvaluator<*>>() {
      every { supportedType } returns SupportedConstraintType<DependsOnConstraint>("depends-on")
      every { isImplicit() } returns false
    }
    val statefulEvaluator = ManualJudgementConstraintEvaluator(repository, MutableClock(), publisher)

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

    val version1 = "fnord-0.0.1~dev.93-h93.3333333"
    val version2 = "fnord-0.0.1~dev.94-h94.4444444"

    val pendingManualJudgement1 = ConstraintState(
      deliveryConfig.name,
      environment.name,
      version1,
      "manual-judgement",
      PENDING
    )

    val pendingManualJudgement2 = ConstraintState(
      deliveryConfig.name,
      environment.name,
      version2,
      "manual-judgement",
      PENDING
    )

    val passedManualJudgement1 = ConstraintState(
      deliveryConfig.name,
      environment.name,
      version1,
      "manual-judgement",
      OVERRIDE_PASS
    )
  }

  fun tests() = rootContext<Fixture<T>> {
    fixture {
      Fixture(
        repositoryProvider = this@ApproveOldVersionTests::createKeelRepository
      )
    }

    context("two versions of an artifact exist") {
      before {
        repository.register(artifact)
        repository.storeDeliveryConfig(deliveryConfig)
        repository.storeArtifact(artifact, version1, ArtifactStatus.RELEASE)
        repository.storeArtifact(artifact, version2, ArtifactStatus.RELEASE)
        repository.storeConstraintState(pendingManualJudgement1)
        repository.storeConstraintState(pendingManualJudgement2)

        every { statelessEvaluator.canPromote(artifact, version2, deliveryConfig, environment) } returns true
        every { statelessEvaluator.canPromote(artifact, version1, deliveryConfig, environment) } returns true
      }

      test("no version is approved, so the latest approved version is null") {
        runBlocking {
          subject.checkEnvironments(deliveryConfig)
        }

        expectThat(repository.latestVersionApprovedIn(deliveryConfig, artifact, environment.name)).isEqualTo(null)
      }

      context("old version is approved") {
        before {
          repository.storeConstraintState(passedManualJudgement1)
        }

        test("so it is the latest approved version") {
          runBlocking {
            subject.checkEnvironments(deliveryConfig)
          }

          expectThat(repository.getConstraintState(deliveryConfig.name, environment.name, version1, "manual-judgement")).get {
            this?.status
          }.isEqualTo(
            passedManualJudgement1.status
          )
          expectThat(repository.latestVersionApprovedIn(deliveryConfig, artifact, environment.name)).isEqualTo(version1)
        }
      }
    }
  }
}
