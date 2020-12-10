package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventRepository
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventScope
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType.BAKE
import com.netflix.spinnaker.keel.lifecycle.LifecycleMonitorRepository
import com.netflix.spinnaker.keel.lifecycle.MonitoredTask
import com.netflix.spinnaker.keel.lifecycle.StartMonitoringEvent
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.time.Clock
import java.time.Duration

abstract class LifecycleMonitorRepositoryTests<T : LifecycleMonitorRepository, EVENT : LifecycleEventRepository> : JUnit5Minutests {
  abstract fun monitorFactory(clock: Clock): T
  abstract fun eventFactory(clock: Clock, publisher: ApplicationEventPublisher): EVENT

  open fun T.flush() {}
  open fun EVENT.flush() {}

  val clock = MutableClock()
  val publisher: ApplicationEventPublisher = mockk(relaxed = true)

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

  private fun createTask(uid: String): MonitoredTask =
    MonitoredTask(type = BAKE, link = "www.bake.com/$version", triggeringEvent = event, triggeringEventUid = uid)

  fun tests() = rootContext<Fixture<T, EVENT>> {
    fixture {
      Fixture(subject = monitorFactory(clock), eventRepository = eventFactory(clock, publisher))
    }

    after {
      subject.flush()
      eventRepository.flush()
    }

    context("adding task") {
      before {
        val uid = eventRepository.saveEvent(event)
        subject.save(StartMonitoringEvent(uid, event))
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
        val tasks = subject.tasksDueForCheck(Duration.ofMinutes(1), 1)
        subject.delete(tasks.first())
        expectThat(subject.numTasksMonitoring()).isEqualTo(0)
      }
    }

    context("clearing failure") {
      before {
        val uid = eventRepository.saveEvent(event)
        subject.save(StartMonitoringEvent(uid, event))
        subject.markFailureGettingStatus(createTask(uid))
        subject.markFailureGettingStatus(createTask(uid))
        subject.clearFailuresGettingStatus(createTask(uid))
      }
      test("failures reset to 0") {
        clock.tickMinutes(5)
        val tasks = subject.tasksDueForCheck(Duration.ofMinutes(1), 1)
        expectThat(tasks)
          .hasSize(1)
          .get { tasks.first().numFailures }.isEqualTo(0)

      }
    }
  }
}
