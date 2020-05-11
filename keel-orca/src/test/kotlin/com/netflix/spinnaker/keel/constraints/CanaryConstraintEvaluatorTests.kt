package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.core.api.CanaryConstraint
import com.netflix.spinnaker.keel.core.api.CanarySource
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaExecutionStages
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.RUNNING
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.TERMINAL
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.experimental.minus
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.CapturingSlot
import io.mockk.MockKAnswerScope
import io.mockk.Runs
import io.mockk.coEvery as every
import io.mockk.coVerify as verify
import io.mockk.just
import io.mockk.mockk
import java.time.Clock
import java.time.Duration
import java.util.concurrent.atomic.AtomicInteger
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
import strikt.assertions.last
import strikt.mockk.captured
import strikt.mockk.isCaptured

internal class CanaryConstraintEvaluatorTests : JUnit5Minutests {

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
    val artifact: DeliveryArtifact = DebianArtifact(name = "fnord", vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2"))),
    val version: String = "fnord-1.42.0",
    val targetEnvironment: Environment,
    val handlers: List<CanaryConstraintDeployHandler> = listOf(DummyCanaryConstraintDeployHandler())
  ) {
    val clock: Clock = Clock.systemUTC()
    val repository: KeelRepository = mockk()
    val orcaService: OrcaService = mockk(relaxUnitFun = true)
    val eventPublisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
    val type: String = "canary"

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
      repository = repository,
      clock = clock,
      eventPublisher = eventPublisher
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture(constraint = defaultConstraint, targetEnvironment = defaultTargetEnvironment)
    }

    context("promotion is gated on launching a canary and its status") {
      context("no canaries were launched yet") {
        before {
          every { orcaService.getCorrelatedExecutions(any(), any()) } returns emptyList()

          every {
            repository.getConstraintState(deliveryConfig.name, targetEnvironment.name, version, constraint.type)
          } answers {
            canaryConstraintState(NOT_EVALUATED)
          }
        }

        test("first pass persists state and launches canaries") {

          val persistedState = CapturingSlot<ConstraintState>()
          every { repository.storeConstraintState(capture(persistedState)) } just Runs

          expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
            .isFalse()

          expectThat(persistedState)
            .isCaptured()
            .with(CapturingSlot<ConstraintState>::captured) {
              get { status }.isEqualTo(PENDING)
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
      }

      context("canaries are running") {
        before {
          val west1Id = randomUID().toString()
          val west2Id = randomUID().toString()

          every { orcaService.getCorrelatedExecutions(any(), any()) } returns listOf(west1Id, west2Id)
          every { orcaService.getOrchestrationExecution(any(), any()) } answers { executionDetailResponse(id = firstArg()) }

          every {
            repository.getConstraintState(deliveryConfig.name, targetEnvironment.name, version, constraint.type)
          } answers {
            canaryConstraintState(
              status = PENDING,
              attributes = CanaryConstraintAttributes(
                executions = setOf(
                  RegionalExecutionId(region = "us-west-1", executionId = west1Id),
                  RegionalExecutionId(region = "us-west-2", executionId = west2Id)
                ),
                startAttempt = 1
              )
            )
          }
        }

        test("the next pass checks canary status") {
          val persistedState = CapturingSlot<ConstraintState>()
          every { repository.storeConstraintState(capture(persistedState)) } just Runs

          expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
            .isFalse()

          expectThat(persistedState)
            .isCaptured()
            .with(CapturingSlot<ConstraintState>::captured) {
              get { status }.isEqualTo(PENDING)
              get { attributes }
                .isA<CanaryConstraintAttributes>()
                .and {
                  get { executions }.hasSize(2)
                  get { startAttempt }.isEqualTo(1)
                  get { status }
                    .hasSize(2)
                    .all {
                      get { executionStatus }.isEqualTo(RUNNING.toString())
                    }
                }
            }
        }
      }

      context("all canaries have passed") {
        before {
          val west1Id = randomUID().toString()
          val west2Id = randomUID().toString()

          every { orcaService.getCorrelatedExecutions(any(), any()) } returns emptyList()
          every {
            orcaService.getOrchestrationExecution(any(), any())
          } answers {
            ExecutionDetailResponse(
              id = firstArg(),
              name = "fnord",
              application = "fnord",
              buildTime = clock.instant(),
              startTime = clock.instant(),
              endTime = clock.instant(),
              status = SUCCEEDED,
              execution = OrcaExecutionStages(
                stages = listOf(
                  mapOf(
                    "refId" to "canary",
                    "context" to mapOf(
                      "canaryScoreMessage" to "Final canary score 100.0 met or exceeded the pass score threshold."
                    )
                  )
                )
              )
            )
          }

          every {
            repository.getConstraintState(deliveryConfig.name, targetEnvironment.name, version, constraint.type)
          } answers {
            canaryConstraintState(
              status = PENDING,
              attributes = CanaryConstraintAttributes(
                executions = setOf(
                  RegionalExecutionId(region = "us-west-1", executionId = west1Id),
                  RegionalExecutionId(region = "us-west-2", executionId = west2Id)
                ),
                startAttempt = 1
              )
            )
          }
        }

        test("promotion is allowed when all canaries pass") {
          val stateSlot = CapturingSlot<ConstraintState>()
          every { repository.storeConstraintState(capture(stateSlot)) } just Runs

          expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
            .isTrue()

          expectThat(stateSlot)
            .isCaptured()
            .with(CapturingSlot<ConstraintState>::captured) {
              get { status }.isEqualTo(PASS)
              get { comment }.isNotNull().contains("canary score 100.0")
            }
        }
      }

      context("a canary fails early in one region but is still running in another") {
        val west1Id = randomUID().toString()
        val west2Id = randomUID().toString()

        before {
          every { orcaService.getCorrelatedExecutions("$judge:us-west-1", any()) } returns listOf(west1Id)
          every { orcaService.getCorrelatedExecutions("$judge:us-west-2", any()) } returns emptyList()

          every { orcaService.getOrchestrationExecution(west1Id, any()) } returns executionDetailResponse(west1Id)
          every { orcaService.getOrchestrationExecution(west2Id, any()) } returns executionDetailResponse(west2Id, TERMINAL)

          every {
            repository.getConstraintState(deliveryConfig.name, targetEnvironment.name, version, constraint.type)
          } answers {
            canaryConstraintState(
              status = PENDING,
              attributes = CanaryConstraintAttributes(
                executions = setOf(
                  RegionalExecutionId(region = "us-west-1", executionId = west1Id),
                  RegionalExecutionId(region = "us-west-2", executionId = west2Id)
                ),
                startAttempt = 1
              )
            )
          }
        }

        test("the constraint fails and the running canary is cancelled") {
          val persistedState = CapturingSlot<ConstraintState>()
          every { repository.storeConstraintState(capture(persistedState)) } just Runs

          expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
            .isFalse()

          expectThat(persistedState)
            .isCaptured()
            .captured
            .get { status }
            .isEqualTo(FAIL)

          verify(exactly = 1) {
            orcaService.cancelOrchestration(west1Id, any())
          }
        }
      }
    }

    context("the constraint only requires a single region to pass") {
      deriveFixture {
        val newConstraint = defaultConstraint.copy(minSuccessfulRegions = 1)
        copy(
          constraint = newConstraint,
          targetEnvironment = targetEnvironment.copy(
            constraints = setOf(newConstraint)
          )
        )
      }

      val west1Id = randomUID().toString()
      val west2Id = randomUID().toString()

      before {
        every { orcaService.getCorrelatedExecutions("$judge:us-west-1", any()) } returns listOf(west1Id)
        every { orcaService.getCorrelatedExecutions("$judge:us-west-2", any()) } returns emptyList()

        every {
          repository.getConstraintState(deliveryConfig.name, targetEnvironment.name, version, constraint.type)
        } answers {
          canaryConstraintState(
            status = PENDING,
            attributes = CanaryConstraintAttributes(
              executions = setOf(
                RegionalExecutionId(region = "us-west-1", executionId = west1Id),
                RegionalExecutionId(region = "us-west-2", executionId = west2Id)
              ),
              startAttempt = 1
            )
          )
        }
      }

      context("one region failed, another is still running") {
        before {
          every { orcaService.getOrchestrationExecution(west1Id, any()) } returns executionDetailResponse(west1Id)
          every { orcaService.getOrchestrationExecution(west2Id, any()) } returns executionDetailResponse(west2Id, TERMINAL)
        }

        test("the running region continues") {
          val persistedState = CapturingSlot<ConstraintState>()
          every { repository.storeConstraintState(capture(persistedState)) } just Runs

          expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
            .isFalse()

          expectThat(persistedState)
            .isCaptured()
            .captured
            .get { status }
            .isEqualTo(PENDING)

          verify(exactly = 0) {
            orcaService.cancelOrchestration(any(), any())
          }
        }
      }

      context("one region failed, the other has passed") {
        before {
          every { orcaService.getOrchestrationExecution(west1Id, any()) } returns executionDetailResponse(west1Id, SUCCEEDED)
          every { orcaService.getOrchestrationExecution(west2Id, any()) } returns executionDetailResponse(west2Id, TERMINAL)
        }

        test("one region has failed, the other has passed") {
          val persistedState = CapturingSlot<ConstraintState>()
          every { repository.storeConstraintState(capture(persistedState)) } just Runs

          expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
            .isTrue()

          expectThat(persistedState)
            .isCaptured()
            .captured
            .get { status }
            .isEqualTo(PASS)
        }
      }
    }

    context("orca task submissions fail") {
      deriveFixture {
        copy(handlers = listOf(FailCanaryConstraintDeployHandler()))
      }

      before {
        every { orcaService.getCorrelatedExecutions(any(), any()) } returns emptyList()

        val retryCount = AtomicInteger()

        every {
          repository.getConstraintState(deliveryConfig.name, targetEnvironment.name, version, constraint.type)
        } answers {
          canaryConstraintState(NOT_EVALUATED)
        } andThen {
          canaryConstraintState(
            status = PENDING,
            attributes = CanaryConstraintAttributes(
              startAttempt = retryCount.incrementAndGet()
            )
          )
        }
      }

      test("retryable failures increments start attempts and remains pending") {
        val persistedState = mutableListOf<ConstraintState>()
        every { repository.storeConstraintState(capture(persistedState)) } just Runs

        repeat(3) {
          expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
            .isFalse()
        }

        expectThat(persistedState)
          .hasSize(3)
          .all {
            get { status }.isEqualTo(PENDING)
          }
          .last()
          .and {
            get { attributes }
              .isA<CanaryConstraintAttributes>()
              .and {
                get { executions }.hasSize(0)
                get { startAttempt }.isEqualTo(3)
              }
          }
      }

      test("constraint fails when retries are exhausted") {
        val persistedState = mutableListOf<ConstraintState>()
        every { repository.storeConstraintState(capture(persistedState)) } just Runs

        every {
          orcaService.getCorrelatedExecutions(any(), any())
        } returns emptyList()

        repeat(4) {
          expectThat(subject.canPromote(artifact, version, deliveryConfig, targetEnvironment))
            .isFalse()
        }

        expectThat(persistedState)
          .last()
          .get { status }
          .isEqualTo(FAIL)
      }
    }
  }

  fun MockKAnswerScope<*, *>.canaryConstraintState(
    status: ConstraintStatus,
    attributes: CanaryConstraintAttributes = CanaryConstraintAttributes()
  ) = ConstraintState(
    deliveryConfigName = firstArg(),
    environmentName = secondArg(),
    artifactVersion = thirdArg(),
    type = arg(3),
    status = status,
    attributes = attributes
  )

  fun Fixture.executionDetailResponse(
    id: String = randomUID().toString(),
    status: OrcaExecutionStatus = RUNNING
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
