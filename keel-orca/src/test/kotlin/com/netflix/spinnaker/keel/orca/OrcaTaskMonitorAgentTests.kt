package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.actuation.SubjectType
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.events.ResourceTaskFailed
import com.netflix.spinnaker.keel.events.ResourceTaskSucceeded
import com.netflix.spinnaker.keel.events.TaskCreatedEvent
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.TERMINAL
import com.netflix.spinnaker.keel.persistence.NoSuchResourceId
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.TaskRecord
import com.netflix.spinnaker.keel.persistence.TaskTrackingRepository
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.coEvery as every
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher

internal class OrcaTaskMonitorAgentTests : JUnit5Minutests {

  companion object {
    val clock: Clock = Clock.systemUTC()
    val orcaService: OrcaService = mockk(relaxed = true)
    val publisher: ApplicationEventPublisher = mockk(relaxed = true)
    val taskTrackingRepository: TaskTrackingRepository = mockk(relaxUnitFun = true)
    val resourceRepository: ResourceRepository = mockk()
    val resource: Resource<DummyResourceSpec> = resource()

    val taskResourceRecord = TaskRecord(
      id = "123",
      subject = "${SubjectType.RESOURCE}:${resource.id}",
      name = "upsert server group")

    val taskConstraintRecord = TaskRecord(
      id = "123",
      subject = "${SubjectType.CONSTRAINT}:${resource.id}",
      name = "canary constraint")

    val task = Task(
      id = "123",
      name = "upsert server group"
    )
  }

  data class OrcaTaskMonitorAgentFixture(
    var event: TaskCreatedEvent
  ) {
    val subject: OrcaTaskMonitorAgent = OrcaTaskMonitorAgent(
      taskTrackingRepository,
      resourceRepository,
      orcaService,
      publisher,
      clock
    )
  }

  fun orcaTaskMonitorAgentTests() = rootContext<OrcaTaskMonitorAgentFixture> {
    fixture {
      OrcaTaskMonitorAgentFixture(
        event = TaskCreatedEvent(taskResourceRecord)
      )
    }

    context("a new orca task is being stored") {
      before {
        every { resourceRepository.get(resource.id) } returns resource
        every { taskTrackingRepository.getTasks() } returns setOf(event.taskRecord)
      }

      after {
        clearAllMocks()
      }

      test("do not process running orca tasks") {
        every {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(event.taskRecord.id)

        runBlocking {
          subject.invokeAgent()
        }

        verify(exactly = 0) { publisher.publishEvent(any()) }
        verify(exactly = 0) { taskTrackingRepository.store(any()) }
      }

      test("a task ended with succeeded status") {
        every {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(event.taskRecord.id, SUCCEEDED)

        runBlocking {
          subject.invokeAgent()
        }

        verify(exactly = 1) { publisher.publishEvent(ofType<ResourceTaskSucceeded>()) }
        verify { taskTrackingRepository.delete(event.taskRecord.id) }
      }

      test("a task is ended with a failure status with no error") {
        every {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(event.taskRecord.id, TERMINAL)

        runBlocking {
          subject.invokeAgent()
        }

        verify(exactly = 1) { publisher.publishEvent(ofType<ResourceTaskFailed>()) }
        verify { taskTrackingRepository.delete(event.taskRecord.id) }
      }

      test("a task is ended with a failure status with a general exception") {
        every {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(
          event.taskRecord.id,
          TERMINAL,
          OrcaExecutionStages(listOf(orcaContext(exception = orcaExceptions())))
        )

        runBlocking {
          subject.invokeAgent()
        }

        verify(exactly = 1) { publisher.publishEvent(ofType<ResourceTaskFailed>()) }
        verify { taskTrackingRepository.delete(event.taskRecord.id) }
      }

      test("a task is ended with a failure status with a kato exception") {
        every {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(
          event.taskRecord.id,
          TERMINAL,
          OrcaExecutionStages(listOf(orcaContext(katoException = clouddriverExceptions())))
        )

        runBlocking {
          subject.invokeAgent()
        }

        verify(exactly = 1) { publisher.publishEvent(ofType<ResourceTaskFailed>()) }
        verify { taskTrackingRepository.delete(event.taskRecord.id) }
      }
    }

    context("resource not found") {
      before {
        every { resourceRepository.get(resource.id) } throws NoSuchResourceId(resource.id)
        every { taskTrackingRepository.getTasks() } returns setOf(event.taskRecord)
        every {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(event.taskRecord.id, SUCCEEDED)
      }

      test("task record is removed") {
        runBlocking {
          subject.invokeAgent()
        }

        verify { taskTrackingRepository.delete(event.taskRecord.id) }
      }
    }

    context("constraint events") {
      modifyFixture {
        event = TaskCreatedEvent(taskConstraintRecord)
      }

      before {
        every { taskTrackingRepository.getTasks() } returns setOf(event.taskRecord)
        every {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(event.taskRecord.id, SUCCEEDED)
      }

      test("do not process tasks which are constraint and not resources") {
        runBlocking {
          subject.invokeAgent()
        }
        verify(exactly = 0) { publisher.publishEvent(any()) }
        verify { taskTrackingRepository.delete(event.taskRecord.id) }
      }
    }
  }

  private fun executionDetailResponse(
    id: String = randomUID().toString(),
    status: OrcaExecutionStatus = OrcaExecutionStatus.RUNNING,
    execution: OrcaExecutionStages = OrcaExecutionStages(emptyList())
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
      status = status,
      execution = execution)

  private fun orcaExceptions() =
    OrcaException(
      details = GeneralErrorsDetails(errors =
      listOf("Too many retries.  10 attempts have been made to bake ... its probably not going to happen."),
        stackTrace = "",
        responseBody = "",
        kind = "",
        error = ""),
      exceptionType = "",
      shouldRetry = false)

  private fun orcaContext(
    exception: OrcaException? = null,
    katoException: List<Map<String, Any>>? = emptyList()
  ): Map<String, Any> =
    mapOf("context" to
      OrcaContext(
        exception = exception,
        clouddriverException = katoException
      ))

  private fun clouddriverExceptions():
    List<Map<String, Any>> = (
    listOf(mapOf(
      "exception" to ClouddriverException(
        cause = "",
        message = "The following security groups do not exist: 'keeldemo-main-elb' in 'test' vpc-46f5a223",
        operation = "",
        type = "EXCEPTION"
      )
    ))
    )
}
