package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.ConstraintStatus
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.PipelineConstraint
import com.netflix.spinnaker.keel.api.PipelineConstraintStateAttributes
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import java.time.Clock
import java.util.HashMap
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isLessThanOrEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue

internal class PipelineConstraintEvaluatorTests : JUnit5Minutests {

  companion object {
    val clock: Clock = Clock.systemUTC()
    val deliveryConfigRepository = InMemoryDeliveryConfigRepository(clock)
    val eventPublisher: ApplicationEventPublisher = mockk(relaxed = true)
  }

  data class Fixture(
    val constraint: PipelineConstraint
  ) {
    val type = "pipeline"
    val orcaService: OrcaService = mockk()
    val artifact = DebianArtifact("fnord")
    val version = "1.1"
    val environment = Environment(
      name = "prod",
      constraints = setOf(constraint)
    )
    val manifest = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = setOf(environment)
    )
    val executionId = randomUID().toString()
    val capturedId = slot<String>()
    val trigger = slot<HashMap<String, Any>>()
    val subject = PipelineConstraintEvaluator(orcaService, deliveryConfigRepository, eventPublisher, clock)
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
        deliveryConfigRepository.store(manifest)

        coEvery {
          orcaService.triggerPipeline(any(), capture(capturedId), capture(trigger))
        } returns TaskRefResponse("/pipelines/$executionId")

        every {
          eventPublisher.publishEvent(any())
        } just Runs
      }

      test("first pass persists state and triggers the pipeline") {
        expectThat(subject.canPromote(artifact, version, manifest, environment))
          .isFalse()

        expectThat(capturedId.captured)
          .isEqualTo(constraint.pipelineId)

        expectThat(trigger.captured)
          .isEqualTo(
            HashMap(
              mapOf(
                "parameters" to mapOf<String, Any?>("youSayManaged" to "weSayDelivery"),
                "type" to "managed",
                "user" to "keel")))

        val state = deliveryConfigRepository.getConstraintState(manifest.name, environment.name, version, type)
        expectThat(state)
          .isNotNull()
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

      test("the next pass updates the execution status") {
        coEvery {
          orcaService.getPipelineExecution(any())
        } returns getExecutionDetailResponse(executionId, OrcaExecutionStatus.RUNNING)

        expectThat(subject.canPromote(artifact, version, manifest, environment))
          .isFalse()

        val state = deliveryConfigRepository.getConstraintState(manifest.name, environment.name, version, type)
        expectThat(state)
          .isNotNull()
          .and {
            get { status }.isEqualTo(ConstraintStatus.PENDING)
            get { attributes }
              .isA<PipelineConstraintStateAttributes>()
              .and {
                get { lastExecutionStatus }.isEqualTo("RUNNING")
              }
          }
      }

      test("promotion is allowed when the pipeline succeeds") {
        coEvery {
          orcaService.getPipelineExecution(any())
        } returns getExecutionDetailResponse(executionId, OrcaExecutionStatus.SUCCEEDED)

        expectThat(subject.canPromote(artifact, version, manifest, environment))
          .isTrue()

        val state = deliveryConfigRepository.getConstraintState(manifest.name, environment.name, version, type)
        expectThat(state)
          .isNotNull()
          .and {
            get { status }.isEqualTo(ConstraintStatus.PASS)
            get { attributes }
              .isA<PipelineConstraintStateAttributes>()
              .and {
                get { lastExecutionStatus }.isEqualTo("SUCCEEDED")
              }
          }
      }

      test("the pipeline has failed") {
        val state = deliveryConfigRepository
          .getConstraintState(manifest.name, environment.name, version, type)!!
          .copy(status = ConstraintStatus.PENDING)
        val runningAttributes = (state.attributes as PipelineConstraintStateAttributes)
          .copy(lastExecutionStatus = "RUNNING")
        deliveryConfigRepository.storeConstraintState(state.copy(attributes = runningAttributes))

        coEvery {
          orcaService.getPipelineExecution(any())
        } returns getExecutionDetailResponse(executionId, OrcaExecutionStatus.TERMINAL)

        expectThat(subject.canPromote(artifact, version, manifest, environment))
          .isFalse()

        val endState = deliveryConfigRepository.getConstraintState(manifest.name, environment.name, version, type)
        expectThat(endState)
          .isNotNull()
          .and {
            get { status }.isEqualTo(ConstraintStatus.FAIL)
            get { attributes }
              .isA<PipelineConstraintStateAttributes>()
              .and {
                get { lastExecutionStatus }.isEqualTo("TERMINAL")
              }
          }
      }
    }
  }

  private fun getExecutionDetailResponse(id: String, status: OrcaExecutionStatus) =
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
