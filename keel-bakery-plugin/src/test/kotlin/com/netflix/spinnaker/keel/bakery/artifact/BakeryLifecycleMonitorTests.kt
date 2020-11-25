package com.netflix.spinnaker.keel.bakery.artifact

import com.netflix.spinnaker.config.LifecycleConfig
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventScope.PRE_DEPLOYMENT
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.NOT_STARTED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType.BAKE
import com.netflix.spinnaker.keel.lifecycle.LifecycleMonitorRepository
import com.netflix.spinnaker.keel.lifecycle.MonitoredTask
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.BUFFERED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.PAUSED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.RUNNING
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.TERMINAL
import com.netflix.spinnaker.keel.orca.OrcaService
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import java.lang.RuntimeException
import java.time.Instant

class BakeryLifecycleMonitorTests : JUnit5Minutests {

  internal class Fixture {
    val monitorRepository: LifecycleMonitorRepository = mockk(relaxUnitFun = true)
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
    val lifecycleConfig = LifecycleConfig()
    val orcaService: OrcaService = mockk()
    val spinnakerBaseUrl = "www.spin.com"
    val id = "12344445565D"

    val event = LifecycleEvent(
      type = BAKE,
      scope = PRE_DEPLOYMENT,
      status = NOT_STARTED,
      id = "my-bake-1",
      artifactRef = "my-config:my-artifact",
      artifactVersion = "123.4",
      text = "Starting bake",
      link = id,
      timestamp = Instant.now()
    )

    val task = MonitoredTask(
      link = id,
      triggeringEvent = event
    )

    val subject = BakeryLifecycleMonitor(monitorRepository, publisher, lifecycleConfig, orcaService, spinnakerBaseUrl)
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    test("handles bakes") {
      expectThat(subject.handles(BAKE)).isTrue()
    }

    context("updating status") {
      context("buffered") {
        before {
          coEvery { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } returns
            getExecution(BUFFERED)
          runBlocking { subject.monitor(task) }
        }

        test("publishes link update") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(NOT_STARTED)
        }
      }

      context("running") {
        before {
          coEvery { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } returns
            getExecution(RUNNING)
          runBlocking { subject.monitor(task) }
        }

        test("publishes running event") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(LifecycleEventStatus.RUNNING)
        }
      }

      context("succeeded") {
        before {
          coEvery { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } returns
            getExecution(SUCCEEDED)
          runBlocking { subject.monitor(task) }
        }

        test("publishes succeeded event") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(LifecycleEventStatus.SUCCEEDED)
        }
      }

      context("failed") {
        before {
          coEvery { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } returns
            getExecution(TERMINAL)
          runBlocking { subject.monitor(task) }
        }

        test("publishes failed event and marks failure") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(LifecycleEventStatus.FAILED)
        }
      }
      context("unknown") {
        before {
          coEvery { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } returns
            getExecution(PAUSED)
          runBlocking { subject.monitor(task) }
        }

        test("publishes unknown event") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(LifecycleEventStatus.UNKNOWN)
        }
      }
    }

    context("handling failure") {
      before {
        coEvery { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } throws
          RuntimeException("this is embarrassing..")
      }

      test("failure is marked") {
        runBlocking { subject.monitor(task) }
        verify(exactly = 1) {
          monitorRepository.markFailureGettingStatus(task)
        }
      }

      test("monitoring is ended after max number is hit") {
        val slot = slot<LifecycleEvent>()
        val failingTask = task.copy(numFailures = 4)
        runBlocking { subject.monitor(failingTask) }
        verify(exactly = 1) {
          publisher.publishEvent(capture(slot))
          monitorRepository.delete(failingTask)
        }
        expectThat(slot.captured.status).isEqualTo(LifecycleEventStatus.UNKNOWN)
      }
    }

    context("handling intermittent failure") {
      before {
        coEvery { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } returns
          getExecution(PAUSED)
      }
      test("failure count is cleared after a success") {
        val failingTask = task.copy(numFailures = 2)
        runBlocking { subject.monitor(failingTask) }
        verify(exactly = 1) {
          monitorRepository.clearFailuresGettingStatus(failingTask)
        }
      }
    }
  }

  private fun getExecution(status: OrcaExecutionStatus) =
    ExecutionDetailResponse(
      id = "12344445565D",
      name = "bake it up",
      application = "keel",
      buildTime = Instant.now(),
      startTime = null,
      endTime = null,
      status = status
    )
}
