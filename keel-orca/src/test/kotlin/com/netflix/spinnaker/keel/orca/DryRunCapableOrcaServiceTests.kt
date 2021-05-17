package com.netflix.spinnaker.keel.orca

import com.netflix.spinnaker.keel.model.OrchestrationRequest
import dev.minutest.TestContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.called
import io.mockk.clearMocks
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.coEvery as every
import io.mockk.coVerify as verify
import kotlinx.coroutines.runBlocking
import org.springframework.core.env.Environment

class DryRunCapableOrcaServiceTests : JUnit5Minutests {
  object Fixture {
    const val user = "keel"
    const val someId = "blah"
    val delegate: OrcaService = mockk()
    val springEnv: Environment = mockk()
    val subject = DryRunCapableOrcaService(delegate, springEnv)
    val request: OrchestrationRequest = mockk()
    val taskResponse: TaskRefResponse = mockk()
    val trigger = HashMap<String, Any>()
    val executionResponse: ExecutionDetailResponse = mockk()
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture
    }

    before {
      every { delegate.orchestrate(user, request) } returns taskResponse
      every { delegate.triggerPipeline(user, someId, trigger) } returns taskResponse
      every { delegate.cancelOrchestration(someId, user) } just runs
      every { delegate.getPipelineExecution(someId, user) } returns executionResponse
      every { delegate.getOrchestrationExecution(someId, user) } returns executionResponse
      every { delegate.getCorrelatedExecutions(someId, user) } returns listOf("1", "2", "3")
    }

    context("dry run disabled") {
      before {
        every { springEnv.getProperty("keel.dryRun.enabled", Boolean::class.java, false) } returns false
      }

      test("OrcaDryRunService delegates orchestrate call") {
        runBlocking { subject.orchestrate(user, request) }
        verify(exactly = 1) {
          delegate.orchestrate(user, request)
        }
      }

      test("OrcaDryRunService delegates triggerPipeline call") {
        runBlocking { subject.triggerPipeline(user, someId, trigger) }
        verify(exactly = 1) {
          delegate.triggerPipeline(user, someId, trigger)
        }
      }

      test("OrcaDryRunService delegates cancelOrchestration call") {
        runBlocking { subject.cancelOrchestration(someId, user) }
        verify(exactly = 1) {
          delegate.cancelOrchestration(someId, user)
        }
      }

      testReadOnlyCalls()
    }

    context("dry run enabled") {
      before {
        clearMocks(delegate, answers = false)
        every { springEnv.getProperty("keel.dryRun.enabled", Boolean::class.java, false) } returns true
      }

      test("OrcaDryRunService does not delegate orchestrate call") {
        runBlocking { subject.orchestrate(user, request) }
        verify {
          delegate wasNot called
        }
      }

      test("OrcaDryRunService does not delegate triggerPipeline call") {
        runBlocking { subject.triggerPipeline(user, someId, trigger) }
        verify {
          delegate wasNot called
        }
      }

      test("OrcaDryRunService does not delegate cancelOrchestration call") {
        runBlocking { subject.cancelOrchestration(someId, user) }
        verify {
          delegate wasNot called
        }
      }

      testReadOnlyCalls()
    }
  }

  private fun TestContextBuilder<Fixture, Fixture>.testReadOnlyCalls() {
    test("OrcaDryRunService delegates all read-only calls") {
      runBlocking { subject.getPipelineExecution(someId, user) }
      verify(exactly = 1) {
        delegate.getPipelineExecution(someId, user)
      }

      runBlocking { subject.getOrchestrationExecution(someId, user) }
      verify(exactly = 1) {
        delegate.getOrchestrationExecution(someId, user)
      }

      runBlocking { subject.getCorrelatedExecutions(someId, user) }
      verify(exactly = 1) {
        delegate.getCorrelatedExecutions(someId, user)
      }
    }
  }
}