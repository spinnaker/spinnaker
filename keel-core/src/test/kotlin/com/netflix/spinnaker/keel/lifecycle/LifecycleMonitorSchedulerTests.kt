package com.netflix.spinnaker.keel.lifecycle

import com.netflix.spinnaker.config.LifecycleConfig
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.FAILED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.RUNNING
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.SUCCEEDED
import com.netflix.spinnaker.keel.telemetry.LifecycleMonitorLoadFailed
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

class LifecycleMonitorSchedulerTests : JUnit5Minutests {
  class Fixture {
    val monitor: LifecycleMonitor = mockk(relaxed = true)
    val monitors: List<LifecycleMonitor> = listOf(monitor)
    val monitorRepository: LifecycleMonitorRepository = mockk(relaxed = true)
    val publisher: ApplicationEventPublisher = mockk(relaxed = true)
    val config = LifecycleConfig()

    val artifactRef = "my-artifact:my-config"
    val version = "123.4"
    val link = "www.bake.com/$version"
    val event = LifecycleEvent(
      scope = LifecycleEventScope.PRE_DEPLOYMENT,
      artifactRef = artifactRef,
      artifactVersion = version,
      type = LifecycleEventType.BAKE,
      status = LifecycleEventStatus.NOT_STARTED,
      id = "bake-$version",
      text = "Submitting bake for version $version",
      link = link
    )

    val task1 = MonitoredTask(
      triggeringEvent = event,
      link = link + "-1"
    )
    val task2 = MonitoredTask(
      triggeringEvent = event.copy(id = event.id + "-2"),
      link = link + "-2"
    )

    val subject = LifecycleMonitorScheduler(
      monitors,
      monitorRepository,
      publisher,
      config
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("application down") {
      before {
        subject.invokeMonitoring()
      }
      test("monitoring loop does nothing") {
        verify(exactly = 0) {
          monitorRepository.tasksDueForCheck(any(), any())
        }
      }
    }

    context("application up") {
      before {
        subject.onApplicationUp()
        every { monitor.handles(any()) } returns true
      }

      context("storing events for monitoring") {
        test("not started event") {
          subject.onLifecycleEvent(event)
          verify(exactly = 1) { monitorRepository.save(any()) }
        }

        test("running event") {
          subject.onLifecycleEvent(event.copy(status = RUNNING))
          verify(exactly = 0) { monitorRepository.save(any()) }
        }

        test("failed event") {
          subject.onLifecycleEvent(event.copy(status = FAILED))
          verify(exactly = 0) { monitorRepository.save(any()) }
        }

        test("succeeded event") {
          subject.onLifecycleEvent(event.copy(status = SUCCEEDED))
          verify(exactly = 0) { monitorRepository.save(any()) }
        }
      }

      context("monitoring loop") {
        before {
          every { monitorRepository.tasksDueForCheck(any(), any()) } returns listOf(task1, task2)
          subject.invokeMonitoring()
        }

        test("both tasks get monitored") {
          coVerify(exactly = 2) { monitor.monitor(any()) }
        }
      }

      context("failure to load tasks to monitor") {
        before {
          every { monitorRepository.tasksDueForCheck(any(), any()) } throws RuntimeException("oh dear.")
          subject.invokeMonitoring()
        }

        test("failure event published") {
          verify(exactly = 1) { publisher.publishEvent(ofType<LifecycleMonitorLoadFailed>()) }
        }
      }
    }
  }
}
