package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventRepository
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventScope
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType.BAKE
import com.netflix.spinnaker.keel.lifecycle.LifecycleMonitorRepository
import com.netflix.spinnaker.keel.lifecycle.MonitoredTask
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Clock
import java.time.Duration

abstract class LifecycleMonitorRepositoryTests<T : LifecycleMonitorRepository, EVENT : LifecycleEventRepository> : JUnit5Minutests {
  abstract fun monitorFactory(clock: Clock): T
  abstract fun eventFactory(clock: Clock): EVENT

  open fun T.flush() {}
  open fun EVENT.flush() {}

  val clock = MutableClock()

  data class Fixture<T : LifecycleMonitorRepository, EVENT : LifecycleEventRepository>(
    val subject: T,
    val eventRepository: EVENT
  )
  val artifact = DockerArtifact(name = "my-artifact", deliveryConfigName = "my-config")
  val version = "123.4"
  val event = LifecycleEvent(
    scope = LifecycleEventScope.PRE_DEPLOYMENT,
    artifactRef = artifact.toLifecycleRef(),
    artifactVersion = version,
    type = BAKE,
    status = LifecycleEventStatus.NOT_STARTED,
    id = "bake-$version",
    text = "Submitting bake for version $version",
    link = "www.bake.com/$version"
  )
  val task = MonitoredTask(type = BAKE, link = "www.bake.com/$version", triggeringEvent = event)

  fun tests() = rootContext<Fixture<T, EVENT>> {
    fixture {
      Fixture(subject = monitorFactory(clock), eventRepository = eventFactory(clock))
    }

    after {
      subject.flush()
      eventRepository.flush()
    }

    context("adding task") {
      before {
        eventRepository.saveEvent(event)
        subject.save(task)
      }
      test("immediately due for check") {
        val tasks = subject.tasksDueForCheck(Duration.ofMinutes(1), 1)
        expectThat(tasks.size).isEqualTo(1)
      }

      test("due only once") {
        var tasks = subject.tasksDueForCheck(Duration.ofMinutes(1), 1)
        expectThat(tasks.size).isEqualTo(1)
        tasks = subject.tasksDueForCheck(Duration.ofMinutes(1), 1)
        expectThat(tasks.size).isEqualTo(0)
      }

      test("can delete") {
        expectThat(subject.numTasksMonitoring()).isEqualTo(1)
        subject.delete(task)
        expectThat(subject.numTasksMonitoring()).isEqualTo(0)
      }

    }
  }
}
