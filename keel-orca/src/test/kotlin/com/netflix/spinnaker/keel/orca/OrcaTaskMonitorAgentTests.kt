package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.actuation.SubjectType
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.events.ResourceTaskFailed
import com.netflix.spinnaker.keel.events.ResourceTaskSucceeded
import com.netflix.spinnaker.keel.events.TaskCreatedEvent
import com.netflix.spinnaker.keel.persistence.NoSuchResourceId
import com.netflix.spinnaker.keel.persistence.TaskRecord
import com.netflix.spinnaker.keel.persistence.TaskTrackingRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryTaskTrackingRepository
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.failed
import strikt.assertions.first
import strikt.assertions.isA
import strikt.assertions.isEqualTo

internal class OrcaTaskMonitorAgentTests : JUnit5Minutests {

  companion object {
    val clock: Clock = Clock.systemUTC()
    val orcaService: OrcaService = mockk(relaxed = true)
    val publisher: ApplicationEventPublisher = mockk(relaxed = true)
    val repository: TaskTrackingRepository = InMemoryTaskTrackingRepository(clock)
    val resourceRepository = InMemoryResourceRepository()
    val resource: Resource<DummyResourceSpec> = resource()

    val taskResourceRecord = TaskRecord(
      id = "123",
      subject = "${SubjectType.RESOURCE}:${resource.id}",
      name = "upsert server group")

    val taskConstraintRecord = TaskRecord(
      id = "123",
      subject = "${SubjectType.CONSTRAINT}:${resource.id}",
      name = "canary constraint")
  }

  data class OrcaTaskMonitorAgentFixture(
    var event: TaskCreatedEvent
  ) {
    val subject: OrcaTaskMonitorAgent = OrcaTaskMonitorAgent(repository, resourceRepository, orcaService, publisher, clock)
  }

  fun orcaTaskMonitorAgentTests() = rootContext<OrcaTaskMonitorAgentFixture> {
    fixture {
      OrcaTaskMonitorAgentFixture(
        event = TaskCreatedEvent(taskResourceRecord)
      )
    }

    context("a new orca task is being stored") {
      before {
        subject.onTaskEvent(event)
        expectThat(repository.getTasks().size).isEqualTo(1)
        resourceRepository.store(resource)
      }

      after {
        clearAllMocks()
        resourceRepository.dropAll()
      }

      test("do not process running orca tasks") {
        coEvery {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(event.taskRecord.id)

        runBlocking {
          subject.invokeAgent()
        }

        verify(exactly = 0) { publisher.publishEvent(any()) }
        expectThat(repository.getTasks().size).isEqualTo(1)
      }

      test("a task ended with succeeded status") {
        coEvery {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(event.taskRecord.id, OrcaExecutionStatus.SUCCEEDED)

        runBlocking {
          subject.invokeAgent()
        }

        verify(exactly = 1) { publisher.publishEvent(ofType<ResourceTaskSucceeded>()) }
        expectThat(repository.getTasks().size).isEqualTo(0)
      }

      test("a task is ended with a failure status with no error") {
        coEvery {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(event.taskRecord.id, OrcaExecutionStatus.TERMINAL)

        runBlocking {
          subject.invokeAgent()
        }

        verify(exactly = 1) { publisher.publishEvent(ofType<ResourceTaskFailed>()) }
        expectThat(repository.getTasks().size).isEqualTo(0)
      }

      test("a task is ended with a failure status with a general exception") {
        coEvery {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(
          event.taskRecord.id,
          OrcaExecutionStatus.TERMINAL,
          OrcaExecutionStages(listOf(orcaContext(exception = orcaExceptions())))
        )

        runBlocking {
          subject.invokeAgent()
        }

        verify(exactly = 1) { publisher.publishEvent(ofType<ResourceTaskFailed>()) }
        resourceRepository.appendHistory(ResourceTaskFailed(resource, orcaExceptions().details.errors.joinToString(","), clock))

        expectThat(resourceRepository.eventHistory(resource.id))
          .first()
          .isA<ResourceTaskFailed>()
          .also {
            it.get {
              this.reason!!.contains(orcaExceptions().details.errors.joinToString(","))
            }
          }

        expectThat(repository.getTasks().size).isEqualTo(0)
      }

      test("a task is ended with a failure status with a kato exception") {
        coEvery {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(
          event.taskRecord.id,
          OrcaExecutionStatus.TERMINAL,
          OrcaExecutionStages(listOf(orcaContext(katoException = clouddriverExceptions())))
        )

        runBlocking {
          subject.invokeAgent()
        }

        verify(exactly = 1) { publisher.publishEvent(ofType<ResourceTaskFailed>()) }
        resourceRepository.appendHistory(ResourceTaskFailed(resource, "The following security groups do not exist: 'keeldemo-main-elb' in 'test' vpc-46f5a223", clock))

        expectThat(resourceRepository.eventHistory(resource.id))
          .first()
          .isA<ResourceTaskFailed>()
          .also {
            it.get {
              this.reason!!.contains("The following security groups do not exist: 'keeldemo-main-elb' in 'test' vpc-46f5a223")
            }
          }

        expectThat(repository.getTasks().size).isEqualTo(0)
      }
    }

    context("resource not found") {
      before {
        subject.onTaskEvent(event)
        expectThat(repository.getTasks().size).isEqualTo(1)
        coEvery {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(event.taskRecord.id,
          OrcaExecutionStatus.SUCCEEDED)
      }
      test("NoSuchResourceId exception") {
        runBlocking {
          subject.invokeAgent()
        }
        expectCatching {
          resourceRepository.get(resource.id)
        }.failed()
          .isA<NoSuchResourceId>()

        expectThat(repository.getTasks().size).isEqualTo(0)
      }
    }

    context("constraint events") {
      modifyFixture {
        event = TaskCreatedEvent(taskConstraintRecord)
      }
      before {
        subject.onTaskEvent(event)
        expectThat(repository.getTasks().size).isEqualTo(1)
        coEvery {
          orcaService.getOrchestrationExecution(event.taskRecord.id)
        } returns executionDetailResponse(event.taskRecord.id,
          OrcaExecutionStatus.SUCCEEDED)
      }
      test("do not process tasks which are constraint and not resources") {
        runBlocking {
          subject.invokeAgent()
        }
        verify(exactly = 0) { publisher.publishEvent(any()) }
        expectThat(repository.getTasks().size).isEqualTo(0)
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
