package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.CanaryConstraint
import com.netflix.spinnaker.keel.api.CanaryConstraintAttributes
import com.netflix.spinnaker.keel.api.CanarySource
import com.netflix.spinnaker.keel.api.ConstraintStatus
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.RegionalExecutionId
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaExecutionStages
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Runs
import io.mockk.clearMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.contains
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isTrue

internal class CanaryConstraintEvaluatorTests : JUnit5Minutests {

  companion object {
    val clock: Clock = Clock.systemUTC()
    val deliveryConfigRepository = InMemoryDeliveryConfigRepository(clock)
    val orcaService: OrcaService = mockk(relaxed = true)
    val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
    val type: String = "canary"
  }

  private val defaultConstraint: CanaryConstraint = CanaryConstraint(
    canaryConfigId = randomUID().toString(),
    lifetime = Duration.ofMinutes(60),
    marginalScore = 75,
    passScore = 90,
    source = CanarySource(
      account = "test",
      cloudProvider = "aws",
      cluster = "fnord-prod"
    ),
    regions = setOf("us-west-1", "us-west-2"),
    capacity = 1
  )

  private val defaultTargetEnvironment: Environment = Environment(
    name = "prod",
    resources = setOf(resource(spec = DummyResourceSpec())),
    constraints = setOf(defaultConstraint)
  )

  data class Fixture(
    val constraint: CanaryConstraint,
    val artifact: DeliveryArtifact = DebianArtifact(name = "fnord"),
    val version: String = "fnord-1.42.0",
    val targetEnvironment: Environment,
    val handlers: List<CanaryConstraintDeployHandler> = listOf(DummyCanaryConstraintDeployHandler())
  ) {
    val deliveryConfig: DeliveryConfig = DeliveryConfig(
      name = "fnord-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      environments = setOf(
        Environment(
          name = "test",
          resources = setOf(resource(spec = DummyResourceSpec()))),
        targetEnvironment
      )
    )

    val judge = "canary:${deliveryConfig.application}:${targetEnvironment.name}:${constraint.canaryConfigId}"

    val subject = CanaryConstraintEvaluator(
      handlers = handlers,
      orcaService = orcaService,
      deliveryConfigRepository = deliveryConfigRepository,
      clock = clock,
      eventPublisher = eventPublisher
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture(constraint = defaultConstraint, targetEnvironment = defaultTargetEnvironment)
    }

    context("promotion is gated on launching a canary and its status") {
      before {
        deliveryConfigRepository.store(deliveryConfig)
        every {
          eventPublisher.publishEvent(any())
        } just Runs
      }

      after {
        clearMocks(orcaService)
      }

      test("first pass persists state and launches canaries") {
        coEvery {
          orcaService.getCorrelatedExecutions(any())
        } returns emptyList()

        expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
          .isFalse()

        coVerify(exactly = 2) {
          orcaService.getCorrelatedExecutions(any())
        }

        val state = deliveryConfigRepository.getConstraintState(
          deliveryConfig.name,
          targetEnvironment.name,
          version,
          type)

        expectThat(state)
          .isNotNull()
          .and {
            get { status }.isEqualTo(ConstraintStatus.PENDING)
            get { attributes }
              .isA<CanaryConstraintAttributes>()
              .and {
                get { executions }.hasSize(2)
                get { startAttempt }.isEqualTo(1)
                // Canary status is only checked on post-launch constraint evals
                get { status }.hasSize(0)
              }
          }
      }

      test("the next pass checks canary status") {
        before {
          clearMocks(orcaService)
        }

        coEvery {
          orcaService.getOrchestrationExecution(any())
        } returns executionDetailResponse()

        coEvery {
          orcaService.getCorrelatedExecutions(any())
        } returns listOf(randomUID().toString())

        expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
          .isFalse()

        coVerify(exactly = 2) {
          orcaService.getOrchestrationExecution(any())
          orcaService.getCorrelatedExecutions(any())
        }

        val state = deliveryConfigRepository.getConstraintState(
          deliveryConfig.name,
          targetEnvironment.name,
          version,
          type)

        expectThat(state)
          .isNotNull()
          .and {
            get { status }.isEqualTo(ConstraintStatus.PENDING)
            get { attributes }
              .isA<CanaryConstraintAttributes>()
              .and {
                get { executions }.hasSize(2)
                get { startAttempt }.isEqualTo(1)
                get { status }
                  .hasSize(2)
                  .all {
                    get { executionStatus }
                      .isEqualTo(OrcaExecutionStatus.RUNNING.toString())
                  }
              }
          }
      }

      test("promotion is allowed when all canaries pass") {
        coEvery {
          orcaService.getCorrelatedExecutions(any())
        } returns emptyList()

        coEvery {
          orcaService.getOrchestrationExecution(any())
        } returns ExecutionDetailResponse(
          id = randomUID().toString(),
          name = "fnord",
          application = "fnord",
          buildTime = clock.instant(),
          startTime = clock.instant(),
          endTime = clock.instant(),
          status = OrcaExecutionStatus.SUCCEEDED,
          execution = OrcaExecutionStages(
            stages = listOf(
              mapOf(
                "refId" to "canary",
                "context" to mapOf(
                  "canaryScoreMessage" to "Final canary score 100.0 met or exceeded the pass score threshold."
                )))))

        expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
          .isTrue()

        coVerify(exactly = 2) {
          orcaService.getOrchestrationExecution(any())
        }

        coVerify(exactly = 0) {
          orcaService.getCorrelatedExecutions(any())
        }

        val state = deliveryConfigRepository.getConstraintState(
          deliveryConfig.name,
          targetEnvironment.name,
          version,
          type)

        expectThat(state!!.comment)
          .isNotNull()
          .contains("canary score 100.0")
      }
    }

    context("the canary fails early in one region but is still running in another") {
      val west1Id = randomUID().toString()
      val west2Id = randomUID().toString()

      before {
        deliveryConfigRepository.dropAll()
        deliveryConfigRepository.store(deliveryConfig)

        coEvery {
          orcaService.getCorrelatedExecutions(any())
        } returns emptyList()

        expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
          .isFalse()

        coVerify(exactly = 2) {
          orcaService.getCorrelatedExecutions(any())
        }

        val state = deliveryConfigRepository.getConstraintState(
          deliveryConfig.name,
          targetEnvironment.name,
          version,
          type)

        var attributes = state!!.attributes!! as CanaryConstraintAttributes

        attributes = attributes.copy(
          executions = setOf(
            RegionalExecutionId(region = "us-west-1", executionId = west1Id),
            RegionalExecutionId(region = "us-west-2", executionId = west2Id)))

        deliveryConfigRepository.storeConstraintState(state.copy(attributes = attributes))
      }

      after { clearMocks(orcaService) }

      test("one region failed, another is still running and is cancelled") {
        coEvery {
          orcaService.getCorrelatedExecutions("$judge:us-west-1")
        } returns listOf(west1Id)

        coEvery {
          orcaService.getCorrelatedExecutions("$judge:us-west-2")
        } returns emptyList()

        coEvery {
          orcaService.getOrchestrationExecution(west1Id)
        } returns executionDetailResponse(west1Id)

        coEvery {
          orcaService.getOrchestrationExecution(west2Id)
        } returns executionDetailResponse(west2Id, OrcaExecutionStatus.TERMINAL)

        coEvery {
          orcaService.cancelOrchestration(any())
        } just Runs

        expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
          .isFalse()

        coVerify(exactly = 1) {
          orcaService.cancelOrchestration(west1Id)
        }
      }
    }

    context("the canary fails early in one region but passIfSucceedsInNRegions allows it") {
      deriveFixture {
        val newConstraint = defaultConstraint.copy(minSuccessfulRegions = 1)
        copy(
          constraint = newConstraint,
          targetEnvironment = targetEnvironment.copy(
            constraints = setOf(newConstraint)))
      }

      val west1Id = randomUID().toString()
      val west2Id = randomUID().toString()

      before {
        deliveryConfigRepository.dropAll()
        deliveryConfigRepository.store(deliveryConfig)

        coEvery {
          orcaService.getCorrelatedExecutions(any())
        } returns emptyList()

        expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
          .isFalse()

        coVerify(exactly = 2) {
          orcaService.getCorrelatedExecutions(any())
        }

        val state = deliveryConfigRepository.getConstraintState(
          deliveryConfig.name,
          targetEnvironment.name,
          version,
          type)

        var attributes = state!!.attributes!! as CanaryConstraintAttributes

        attributes = attributes.copy(
          executions = setOf(
            RegionalExecutionId(region = "us-west-1", executionId = west1Id),
            RegionalExecutionId(region = "us-west-2", executionId = west2Id)))

        deliveryConfigRepository.storeConstraintState(state.copy(attributes = attributes))
      }

      after { clearMocks(orcaService) }

      test("one region failed, another is still running and it continues") {
        coEvery {
          orcaService.getCorrelatedExecutions("$judge:us-west-1")
        } returns listOf(west1Id)

        coEvery {
          orcaService.getCorrelatedExecutions("$judge:us-west-2")
        } returns emptyList()

        coEvery {
          orcaService.getOrchestrationExecution(west1Id)
        } returns executionDetailResponse(west1Id)

        coEvery {
          orcaService.getOrchestrationExecution(west2Id)
        } returns executionDetailResponse(west2Id, OrcaExecutionStatus.TERMINAL)

        expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
          .isFalse()

        coVerify(exactly = 0) {
          orcaService.cancelOrchestration(west1Id)
        }

        val state = deliveryConfigRepository.getConstraintState(
          deliveryConfig.name,
          targetEnvironment.name,
          version,
          type)!!

        expectThat(state.status)
          .isEqualTo(ConstraintStatus.PENDING)
      }

      test("one region has failed, the other has passed") {
        coEvery {
          orcaService.getCorrelatedExecutions(any())
        } returns emptyList()

        coEvery {
          orcaService.getOrchestrationExecution(west1Id)
        } returns executionDetailResponse(west1Id, OrcaExecutionStatus.SUCCEEDED)

        coEvery {
          orcaService.getOrchestrationExecution(west2Id)
        } returns executionDetailResponse(west2Id, OrcaExecutionStatus.TERMINAL)

        expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
          .isTrue()

        val state = deliveryConfigRepository.getConstraintState(
          deliveryConfig.name,
          targetEnvironment.name,
          version,
          type)!!

        expectThat(state.status)
          .isEqualTo(ConstraintStatus.PASS)
      }
    }

    context("orca task submissions fail") {
      deriveFixture {
        copy(handlers = listOf(FailCanaryConstraintDeployHandler()))
      }

      before {
        deliveryConfigRepository.dropAll()
        deliveryConfigRepository.store(deliveryConfig)
      }

      test("retryable failures increments start attempts and remains pending") {
        coEvery {
          orcaService.getCorrelatedExecutions(any())
        } returns emptyList()

        repeat(3) {
          expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
            .isFalse()
        }

        val state = deliveryConfigRepository.getConstraintState(
          deliveryConfig.name,
          targetEnvironment.name,
          version,
          type)

        expectThat(state)
          .isNotNull()
          .and {
            get { status }.isEqualTo(ConstraintStatus.PENDING)
            get { attributes }
              .isA<CanaryConstraintAttributes>()
              .and {
                get { executions }.hasSize(0)
                get { startAttempt }.isEqualTo(3)
              }
          }
      }

      test("constraint fails when task launches fail and retries are exhausted") {
        coEvery {
          orcaService.getCorrelatedExecutions(any())
        } returns emptyList()

        repeat(4) {
          expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
            .isFalse()
        }

        val state = deliveryConfigRepository.getConstraintState(
          deliveryConfig.name,
          targetEnvironment.name,
          version,
          type)

        expectThat(state)
          .isNotNull()
          .and {
            get { status }.isEqualTo(ConstraintStatus.FAIL)
          }
      }
    }
  }

  fun executionDetailResponse(
    id: String = randomUID().toString(),
    status: OrcaExecutionStatus = OrcaExecutionStatus.RUNNING
  ) =
    ExecutionDetailResponse(
      id = id,
      name = "fnord",
      application = "fnord",
      buildTime = clock.instant(),
      startTime = clock.instant(),
      endTime = when (status.isIncomplete()) {
        true -> null
        false -> clock.instant()
      },
      status = status)
}

class DummyCanaryConstraintDeployHandler : CanaryConstraintDeployHandler {
  override val supportedClouds = setOf("aws", "ec2")

  override suspend fun deployCanary(
    constraint: CanaryConstraint,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    regions: Set<String>
  ): Map<String, Task> {
    return regions.associateWith {
      Task(id = randomUID().toString(), name = "Canary $version in region $it")
    }
  }
}

class FailCanaryConstraintDeployHandler : CanaryConstraintDeployHandler {
  override val supportedClouds = setOf("aws", "ec2")

  override suspend fun deployCanary(
    constraint: CanaryConstraint,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    regions: Set<String>
  ): Map<String, Task> = emptyMap()
}
