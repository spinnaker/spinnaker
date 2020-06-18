package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.constraints.ConstraintRepository
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.NOT_EVALUATED
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.core.api.PipelineConstraint
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.RUNNING
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.TERMINAL
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.MockKAnswerScope
import io.mockk.Runs
import io.mockk.coEvery as every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.time.Instant
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isLessThanOrEqualTo
import strikt.assertions.isNull
import strikt.assertions.isTrue
import strikt.mockk.captured

internal class PipelineConstraintEvaluatorTests : JUnit5Minutests {

  data class Fixture(
    val constraint: PipelineConstraint
  ) {
    val clock: Clock = Clock.systemUTC()
    val repository: ConstraintRepository = mockk()
    val eventPublisher: EventPublisher = mockk(relaxUnitFun = true)

    val type = "pipeline"
    val orcaService: OrcaService = mockk()
    val version = "1.1"
    val environment = Environment(
      name = "prod",
      constraints = setOf(constraint)
    )
    val manifest = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(
        DebianArtifact(
          name = "fnord",
          vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2"))
        )
      ),
      environments = setOf(environment)
    )
    val executionId = randomUID().toString()
    val capturedId = slot<String>()
    val capturedTrigger = slot<HashMap<String, Any>>()
    val persistedState = slot<ConstraintState>()
    val subject = PipelineConstraintEvaluator(orcaService, repository, eventPublisher, clock)

    var result: Boolean? = null

    fun evaluate() {
      result = subject.canPromote(DebianArtifact("fnord", vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2"))), version, manifest, environment)
    }
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture(
        constraint = PipelineConstraint(
          pipelineId = randomUID().toString(),
          parameters = mapOf("youSayManaged" to "weSayDelivery")
        ))
    }

    context("promotion is gated on launching a pipeline and its final status") {
      before {
        every {
          orcaService.triggerPipeline(any(), capture(capturedId), capture(capturedTrigger))
        } returns TaskRefResponse("/pipelines/$executionId")

        every {
          repository.storeConstraintState(capture(persistedState))
        } just Runs
      }

      context("first pass") {
        before {
          every {
            repository.getConstraintState(manifest.name, environment.name, version, type)
          } answers {
            canaryConstraintState(NOT_EVALUATED)
          }

          evaluate()
        }

        test("does not yet pass the constraint") {
          expectThat(result).isFalse()
        }

        test("triggers the pipeline") {
          expectThat(capturedId)
            .captured
            .isEqualTo(constraint.pipelineId)

          expectThat(capturedTrigger)
            .captured
            .isEqualTo(
              hashMapOf(
                "parameters" to mapOf<String, Any?>("youSayManaged" to "weSayDelivery"),
                "type" to "managed",
                "user" to "keel",
                "linkText" to "Env prod"
              )
            )
        }

        test("persists initial state") {
          expectThat(persistedState)
            .captured
            .and {
              get { attributes }
                .isA<PipelineConstraintStateAttributes>()
                .and {
                  get { executionId }.isEqualTo(fixture.executionId)
                  get { attempt }.isEqualTo(1)
                  get { latestAttempt }.isLessThanOrEqualTo(clock.instant())
                  get { lastExecutionStatus }.isNull()
                }
            }
        }
      }

      context("a pipeline was previously launched") {
        before {
          every {
            repository.getConstraintState(manifest.name, environment.name, version, type)
          } answers {
            canaryConstraintState(
              status = PENDING,
              attributes = PipelineConstraintStateAttributes(
                executionId = executionId,
                attempt = 1,
                latestAttempt = Instant.now()
              )
            )
          }
        }

        context("the pipeline is still running") {
          before {
            every {
              orcaService.getPipelineExecution(any(), any())
            } returns getExecutionDetailResponse(executionId, RUNNING)

            evaluate()
          }

          test("promotion is not yet allowed") {
            expectThat(result).isFalse()
          }

          test("updates the execution status") {
            expectThat(persistedState)
              .captured
              .and {
                get { status }.isEqualTo(PENDING)
                get { attributes }
                  .isA<PipelineConstraintStateAttributes>()
                  .and {
                    get { lastExecutionStatus }.isEqualTo("RUNNING")
                  }
              }
          }
        }

        context("the pipeline succeeds") {
          before {
            every {
              orcaService.getPipelineExecution(any(), any())
            } returns getExecutionDetailResponse(executionId, SUCCEEDED)

            evaluate()
          }

          test("promotion is allowed") {
            expectThat(result).isTrue()
          }

          test("state is updated") {
            expectThat(persistedState)
              .captured
              .and {
                get { status }.isEqualTo(PASS)
                get { attributes }
                  .isA<PipelineConstraintStateAttributes>()
                  .and {
                    get { lastExecutionStatus }.isEqualTo("SUCCEEDED")
                  }
              }
          }
        }

        context("the pipeline fails") {
          before {
            every {
              orcaService.getPipelineExecution(any(), any())
            } returns getExecutionDetailResponse(executionId, TERMINAL)

            evaluate()
          }

          test("promotion is not allowed") {
            expectThat(result).isFalse()
          }

          test("state is updated") {
            expectThat(persistedState)
              .captured
              .and {
                get { status }.isEqualTo(FAIL)
                get { attributes }
                  .isA<PipelineConstraintStateAttributes>()
                  .and {
                    get { lastExecutionStatus }.isEqualTo("TERMINAL")
                  }
              }
          }
        }
      }
    }
  }

  fun MockKAnswerScope<*, *>.canaryConstraintState(
    status: ConstraintStatus,
    attributes: PipelineConstraintStateAttributes? = null
  ) = ConstraintState(
    deliveryConfigName = firstArg(),
    environmentName = secondArg(),
    artifactVersion = thirdArg(),
    type = arg(3),
    status = status,
    attributes = attributes
  )

  private fun Fixture.getExecutionDetailResponse(id: String, status: OrcaExecutionStatus) =
    ExecutionDetailResponse(
      id = id,
      name = "fnord",
      application = "fnord",
      buildTime = clock.instant(),
      startTime = clock.instant(),
      endTime = null,
      status = status
    )
}
