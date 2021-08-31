package com.netflix.spinnaker.keel.titus

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.keel.api.TaskStatus
import com.netflix.spinnaker.keel.api.action.ActionState
import com.netflix.spinnaker.keel.api.actuation.SubjectType
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.actuation.TaskLauncher
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.api.titus.TitusServerGroup
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.titus.batch.RUN_JOB_TYPE
import com.netflix.spinnaker.keel.titus.verification.LinkStrategy
import com.netflix.spinnaker.keel.titus.verification.TASKS
import de.huxhorn.sulky.ulid.ULID
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.get
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess
import java.time.Instant

internal class ContainerRunnerTests {
  private val taskLauncher: TaskLauncher = mockk()
  private val spectator = NoopRegistry()
  private val orca: OrcaService = mockk(relaxUnitFun = true)

  private val subject = ContainerRunner(taskLauncher, orca, spectator)

  private val imageId = "my/image:id"
  private val subjectLine = "we are launching"
  private val description = "to the moon"
  private val serviceAccount = "me@rocket.launch"
  private val application = "rockettime"
  private val environmentName = "moon"
  private val location = TitusServerGroup.Location(
    account = "nasa",
    region = "the-moon-region"
  )
  private val environmentVariables = mapOf("fuelType" to "rocket")
  private val linkStrategy: LinkStrategy? = null


  @Test
  fun `launch function launches a container job`() {
    val taskId = stubTaskLaunch()

    expectCatching { subject.launchContainer(
      imageId = imageId,
      subjectLine = subjectLine,
      description = description,
      serviceAccount = serviceAccount,
      application = application,
      environmentName = environmentName,
      location = location,
      environmentVariables = environmentVariables,
    ) }
      .isSuccess()
      .get(TASKS)
      .isA<Iterable<String>>()
      .first() isEqualTo taskId

    coVerify {
      taskLauncher.submitJob(
        type = SubjectType.VERIFICATION,
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

  @ParameterizedTest(name = "action is considered still running if orca status is {0}")
  @EnumSource(
    TaskStatus::class,
    names = ["NOT_STARTED", "RUNNING", "PAUSED", "SUSPENDED", "BUFFERED"]
  )
  fun `action is considered still running if orca task is running`(taskStatus: TaskStatus) {
    val taskId = ULID().nextULID()
    val previousState = runningState(taskId)

    coEvery {
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

    runBlocking {
      expectThat(subject.getNewState(previousState, linkStrategy).status) isEqualTo ConstraintStatus.PENDING
    }
  }

  @ParameterizedTest(name = "verification is considered successful if orca status is {0}")
  @EnumSource(
    TaskStatus::class,
    names = ["SUCCEEDED"]
  )
  fun `verification is considered successful if orca task succeeded`(taskStatus: TaskStatus) {
    val taskId = ULID().nextULID()
    val previousState = runningState(taskId)

    coEvery {
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

    runBlocking {
      expectThat(subject.getNewState(previousState, linkStrategy).status) isEqualTo ConstraintStatus.PASS
    }
  }

  @ParameterizedTest(name = "verification is considered failed if orca status is {0}")
  @EnumSource(
    TaskStatus::class,
    names = ["TERMINAL", "FAILED_CONTINUE", "STOPPED", "CANCELED"]
  )
  fun `verification is considered failed if orca task failed`(taskStatus: TaskStatus) {
    val taskId = ULID().nextULID()
    val previousState = runningState(taskId)

    coEvery {
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

    runBlocking {
      expectThat(subject.getNewState(previousState, linkStrategy).status) isEqualTo ConstraintStatus.FAIL
    }
  }

  private fun runningState(taskId: String?) =
    ActionState(
      status = ConstraintStatus.PENDING,
      startedAt = Instant.now().minusSeconds(120),
      endedAt = null,
      metadata = mapOf(TASKS to listOf(taskId))
    )

  private fun stubTaskLaunch(): String =
    ULID()
      .nextULID()
      .also { taskId ->
        coEvery {
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
}
