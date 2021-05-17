package com.netflix.spinnaker.keel.bakery.artifact

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.config.BaseUrlConfig
import com.netflix.spinnaker.config.LifecycleConfig
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventScope.PRE_DEPLOYMENT
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.FAILED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.NOT_STARTED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.UNKNOWN
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType.BAKE
import com.netflix.spinnaker.keel.lifecycle.LifecycleMonitorRepository
import com.netflix.spinnaker.keel.lifecycle.MonitoredTask
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.orca.OrcaExecutionStages
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.BUFFERED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.PAUSED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.RUNNING
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.SUCCEEDED
import com.netflix.spinnaker.keel.orca.OrcaExecutionStatus.TERMINAL
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.persistence.BakedImageRepository
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import java.time.Instant
import io.mockk.coEvery as every

class BakeryLifecycleMonitorTests : JUnit5Minutests {

  internal class Fixture {
    val monitorRepository: LifecycleMonitorRepository = mockk(relaxUnitFun = true)
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
    val bakedImageRepository: BakedImageRepository = mockk(relaxUnitFun = true)
    val imageService: ImageService = mockk(relaxUnitFun = true) {
      every { findBaseImageName(any(), any()) } returns "base-1-2-3"
    }
    val lifecycleConfig = LifecycleConfig()
    val clock = MutableClock()
    val objectMapper: ObjectMapper = configuredTestObjectMapper()
    val orcaService: OrcaService = mockk()
    val spinnakerBaseUrl = "www.spin.com"
    val id = "12344445565D"

    val event = LifecycleEvent(
      type = BAKE,
      scope = PRE_DEPLOYMENT,
      status = NOT_STARTED,
      id = "my-bake-1",
      deliveryConfigName = "my-config",
      artifactReference = "my-artifact",
      artifactVersion = "123.4",
      text = "Starting bake",
      link = id,
      timestamp = Instant.now()
    )

    val task = MonitoredTask(
      link = id,
      triggeringEvent = event,
      triggeringEventUid = "uid-i-am"
    )

    val subject = BakeryLifecycleMonitor(
      monitorRepository = monitorRepository,
      publisher = publisher,
      lifecycleConfig = lifecycleConfig,
      orcaService = orcaService,
      baseUrlConfig = BaseUrlConfig(),
      bakedImageRepository = bakedImageRepository,
      imageService = imageService,
      clock = clock,
      objectMapper = objectMapper
    )
  }



  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    test("handles bakes") {
      expectThat(subject.handles(BAKE)).isTrue()
    }

    context("updating status") {
      context("buffered") {
        before {
          every { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } returns
            getExecution(BUFFERED)
          runBlocking { subject.monitor(task) }
        }

        test("publishes link update") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(NOT_STARTED)
          expectThat(slot.captured.startMonitoring).isEqualTo(false)
        }
      }

      context("running") {
        before {
          every { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } returns
            getExecution(RUNNING)
          runBlocking { subject.monitor(task) }
        }

        test("publishes running event") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(LifecycleEventStatus.RUNNING)
          expectThat(slot.captured.startMonitoring).isEqualTo(false)
          expectThat(slot.captured.timestamp).isEqualTo(null)
        }
      }

      context("succeeded") {
        before {
          every { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } returns
            getSucceededBakeExecution()
          runBlocking { subject.monitor(task) }
        }

        test("publishes succeeded event") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(LifecycleEventStatus.SUCCEEDED)
          expectThat(slot.captured.startMonitoring).isEqualTo(false)
          expectThat(slot.captured.timestamp).isEqualTo(null)
        }

        test("saves baked image") {
          verify(exactly = 1) { bakedImageRepository.store(any()) }
        }
      }

      context("failed") {
        before {
          every { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } returns
            getExecution(TERMINAL)
          runBlocking { subject.monitor(task) }
        }

        test("publishes fail(ed) event and marks failure") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(FAILED)
          expectThat(slot.captured.startMonitoring).isEqualTo(false)
          expectThat(slot.captured.timestamp).isEqualTo(null)
        }

        test("does not save baked image because it isn't in the task output") {
          verify(exactly = 0) { bakedImageRepository.store(any()) }
        }
      }
      context("unknown") {
        before {
          every { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } returns
            getExecution(PAUSED)
          runBlocking { subject.monitor(task) }
        }

        test("publishes unknown event") {
          val slot = slot<LifecycleEvent>()
          verify(exactly = 1) {
            publisher.publishEvent(capture(slot))
          }
          expectThat(slot.captured.status).isEqualTo(UNKNOWN)
          expectThat(slot.captured.startMonitoring).isEqualTo(false)
          expectThat(slot.captured.timestamp).isEqualTo(null)
        }
      }
    }

    context("handling failure") {
      before {
        every { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } throws
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
        expectThat(slot.captured.status).isEqualTo(UNKNOWN)
        expectThat(slot.captured.startMonitoring).isEqualTo(false)
      }
    }

    context("handling intermittent failure") {
      before {
        every { orcaService.getOrchestrationExecution(id, DEFAULT_SERVICE_ACCOUNT) } returns
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

  private fun getSucceededBakeExecution() =
    ExecutionDetailResponse(
      id = "12344445565D",
      name = "bake it up",
      application = "keel",
      buildTime = Instant.now(),
      startTime = null,
      endTime = null,
      status = SUCCEEDED,
      execution = OrcaExecutionStages(
        listOf(mapOf(
          "type" to "bake",
          "outputs" to mapOf(
            "deploymentDetails" to listOf(
              mapOf(
                "ami" to "ami-222",
                "imageId" to "ami-222",
                "imageName" to "waffle-time-server-0.1.0_rc.6-h14.a03a954-x86_64-20210304183413-bionic-classic-hvm-sriov-ebs",
                "amiSuffix" to "20210304183413",
                "baseLabel" to "release",
                "baseOs" to "bionic-classic",
                "storeType" to "ebs",
                "vmType" to "hvm",
                "region" to "us-east-1",
                "package" to "waffle-time-server_0.1.0~rc.6-h14.a03a954_all.deb",
                "cloudProviderType" to "aws",
                "baseAmiId" to "ami-111"
              )
            )
          )
        ))
      )
    )
}
