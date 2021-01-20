package com.netflix.spinnaker.keel.titus.verification

import com.netflix.spinnaker.keel.api.actuation.SubjectType.VERIFICATION
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.titus.TestContainerVerification
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup.Location
import com.netflix.spinnaker.keel.api.verification.VerificationContext
import com.netflix.spinnaker.keel.api.verification.VerificationState
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.titus.batch.RUN_JOB_TYPE
import de.huxhorn.sulky.ulid.ULID
import io.mockk.CapturingSlot
import io.mockk.mockk
import io.mockk.slot
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess
import strikt.mockk.withCaptured
import java.time.Instant.now
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

internal class TestContainerVerificationEvaluatorTests {

  private val orca = mockk<OrcaService>()
  private val taskLauncher = mockk<TaskLauncher>()
  private val subject = TestContainerVerificationEvaluator(orca, taskLauncher)

  private val context = VerificationContext(
    deliveryConfig = deliveryConfig(),
    environmentName = "test",
    artifactReference = "fnord",
    version = "1.1"
  )
  private val verification = TestContainerVerification(
    repository = "illuminati/fnord",
    location = Location(
      account = "titustestvpc",
      region = "ap-south-1"
    ),
    application = "fnord-test-app"
  )

  @Test
  fun `starting verification launches a container job via task launcher`() {
    val taskId = stubTaskLaunch()

    expectCatching { subject.start(context, verification) }
      .isSuccess()
      .get(TASKS)
      .isA<Iterable<String>>()
      .first() isEqualTo taskId

    verify {
      taskLauncher.submitJob(
        type = VERIFICATION,
        user = any(),
        application = any(),
        notifications = any(),
        subject = any(),
        description = any(),
        correlationId = any(),
        stages = match {
          it.first()["type"] == RUN_JOB_TYPE
        }
      )
    }
  }

  @Test
  fun `container job runs with verification's application`() {
    val job = captureTaskLaunch()

    subject.start(context, verification)

    expectThat(job).withCaptured {
      hasSize(1)
        .first()
        .get("cluster").isA<Map<String, Any?>>()
        .get("application") isEqualTo verification.application
    }
  }

  @Test
  fun `if no application is specified container job runs with delivery config's application`() {
    val job = captureTaskLaunch()

    subject.start(context, verification.copy(application = null))

    expectThat(job).withCaptured {
      hasSize(1)
        .first()
        .get("cluster").isA<Map<String, Any?>>()
        .get("application") isEqualTo context.deliveryConfig.application
    }
  }

  @ParameterizedTest(name = "verification is considered still running if orca status is {0}")
  @EnumSource(
    OrcaExecutionStatus::class,
    names = ["NOT_STARTED", "RUNNING", "PAUSED", "SUSPENDED", "BUFFERED"]
  )
  fun `verification is considered still running if orca task is running`(taskStatus: OrcaExecutionStatus) {
    val taskId = ULID().nextULID()
    val previousState = runningState(taskId)

    every {
      orca.getOrchestrationExecution(taskId, any())
    } returns ExecutionDetailResponse(
      id = taskId,
      name = "whatever",
      application = "fnord",
      buildTime = previousState.startedAt,
      startTime = previousState.startedAt,
      endTime = null,
      status = taskStatus
    )

    expectThat(subject.evaluate(context, verification, previousState.metadata)) isEqualTo PENDING
  }

  @ParameterizedTest(name = "verification is considered successful if orca status is {0}")
  @EnumSource(
    OrcaExecutionStatus::class,
    names = ["SUCCEEDED"]
  )
  fun `verification is considered successful if orca task succeeded`(taskStatus: OrcaExecutionStatus) {
    val taskId = ULID().nextULID()
    val previousState = runningState(taskId)

    every {
      orca.getOrchestrationExecution(taskId, any())
    } returns ExecutionDetailResponse(
      id = taskId,
      name = "whatever",
      application = "fnord",
      buildTime = previousState.startedAt,
      startTime = previousState.startedAt,
      endTime = null,
      status = taskStatus
    )

    expectThat(subject.evaluate(context, verification, previousState.metadata)) isEqualTo PASS
  }

  @ParameterizedTest(name = "verification is considered failed if orca status is {0}")
  @EnumSource(
    OrcaExecutionStatus::class,
    names = ["TERMINAL", "FAILED_CONTINUE", "STOPPED", "CANCELED"]
  )
  fun `verification is considered failed if orca task failed`(taskStatus: OrcaExecutionStatus) {
    val taskId = ULID().nextULID()
    val previousState = runningState(taskId)

    every {
      orca.getOrchestrationExecution(taskId, any())
    } returns ExecutionDetailResponse(
      id = taskId,
      name = "whatever",
      application = "fnord",
      buildTime = previousState.startedAt,
      startTime = previousState.startedAt,
      endTime = null,
      status = taskStatus
    )

    expectThat(subject.evaluate(context, verification, previousState.metadata)) isEqualTo FAIL
  }

  private fun stubTaskLaunch(): String =
    ULID()
      .nextULID()
      .also { taskId ->
        every {
          taskLauncher.submitJob(
            type = any(),
            user = any(),
            application = any(),
            notifications = any(),
            subject = any(),
            description = any(),
            correlationId = any(),
            stages = any()
          )
        } answers { Task(id = taskId, name = arg(3)) }
      }

  private fun captureTaskLaunch(): CapturingSlot<List<Map<String, Any?>>> =
    slot<List<Map<String, Any?>>>()
      .also { slot ->
        every {
          taskLauncher.submitJob(
            type = any(),
            user = any(),
            application = any(),
            notifications = any(),
            subject = any(),
            description = any(),
            correlationId = any(),
            stages = capture(slot)
          )
        } answers { Task(id = ULID().nextULID(), name = arg(3)) }
      }

  private fun runningState(taskId: String?) =
    VerificationState(
      status = PENDING,
      startedAt = now().minusSeconds(120),
      endedAt = null,
      metadata = mapOf(TASKS to listOf(taskId))
    )
}
