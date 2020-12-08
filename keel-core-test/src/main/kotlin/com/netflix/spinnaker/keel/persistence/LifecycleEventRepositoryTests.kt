package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.lifecycle.LifecycleEvent
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventRepository
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventScope.PRE_DEPLOYMENT
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.FAILED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.NOT_STARTED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.RUNNING
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventStatus.SUCCEEDED
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventType.BAKE
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import java.time.Clock
import java.time.Instant

abstract class LifecycleEventRepositoryTests<T: LifecycleEventRepository> : JUnit5Minutests {
  abstract fun factory(clock: Clock): T

  open fun T.flush() {}

  val clock = MutableClock()

  data class Fixture<T : LifecycleEventRepository>(
    val subject: T
  )
  val artifact = DockerArtifact(name = "my-artifact", deliveryConfigName = "my-config")
  val version = "123.4"
  val event = LifecycleEvent(
    scope = PRE_DEPLOYMENT,
    artifactRef = artifact.toLifecycleRef(),
    artifactVersion = version,
    type = BAKE,
    status = NOT_STARTED,
    id = "bake-$version",
    text = "Submitting bake for version $version",
    link = "www.bake.com/$version",
    data = mapOf("hi" to "whatsup")
  )
  val anotherEvent = event.copy(id = "bake-$version-2")

  fun tests() = rootContext<Fixture<T>> {
    fixture {
      Fixture(subject = factory(clock))
    }

    after { subject.flush() }

    context("saving events") {
      before {
        subject.saveEvent(event)
      }

      test("can get saved event") {
        val events = subject.getEvents(artifact, version)
        expect {
          that(events.size).isEqualTo(1)
          that(events.first().status).isEqualTo(NOT_STARTED)
          that(events.first().timestamp).isNotNull()
          that(events.first().data).isNotEmpty()
          that(events.first().data["hi"]).isEqualTo("whatsup")
        }
      }

      test("updates timestamp if duplicate event") {
        clock.tickMinutes(1)
        val now = clock.instant()
        subject.saveEvent(event.copy(timestamp = now))
        val events = subject.getEvents(artifact, version)
        expect {
          that(events.size).isEqualTo(1)
          that(events.first().timestamp).isEqualTo(now)
        }
      }
    }

    context("turning events into steps") {
      before {
        subject.saveEvent(event)
      }

      test("can transform single event to step") {
        val steps = subject.getSteps(artifact, version)
        expect {
          that(steps.size).isEqualTo(1)
          that(steps.first().status).isEqualTo(NOT_STARTED)
          that(steps.first().startedAt).isNotNull()
          that(steps.first().completedAt).isNull()
        }
      }

      context("multiple events single id"){
        before {
          clock.tickMinutes(1)
          subject.saveEvent(event.copy(status = RUNNING, text = null, link = null))
          clock.tickMinutes(1)
          subject.saveEvent(event.copy(status = SUCCEEDED, text = "Bake finished! Here's your cake", link = null))
        }

        test("can transform multiple events to step") {
          val steps = subject.getSteps(artifact, version)
          expect {
            that(steps.size).isEqualTo(1)
            that(steps.first().status).isEqualTo(SUCCEEDED)
            that(steps.first().text).isEqualTo("Bake finished! Here's your cake")
            that(steps.first().link).isEqualTo("www.bake.com/$version")
            that(steps.first().startedAt).isNotNull()
            that(steps.first().completedAt).isNotNull()
            that(steps.first().startedAt).isNotEqualTo(steps.first().completedAt)
          }
        }
      }

      context("multiple event ids") {
        before {
          clock.tickMinutes(1)
          subject.saveEvent(anotherEvent)
          clock.tickMinutes(1)
          subject.saveEvent(event.copy(status = RUNNING, text = null, link = null))
          clock.tickMinutes(1)
          subject.saveEvent(anotherEvent.copy(status = RUNNING, text = null, link = null))
          clock.tickMinutes(1)
          subject.saveEvent(event.copy(status = FAILED, text = "Oops, this failed", link = null))
          clock.tickMinutes(1)
          subject.saveEvent(anotherEvent.copy(status = SUCCEEDED, text = "Bake succeeded", link = null))
        }

        test("can transform multiple events to multiple steps") {
          val steps = subject.getSteps(artifact, version)
          expect {
            that(steps.size).isEqualTo(2)
            that(steps.first().status).isEqualTo(FAILED)
            that(steps.first().text).isEqualTo("Oops, this failed")
            that(steps.first().link).isEqualTo("www.bake.com/$version")
            that(steps.last().status).isEqualTo(SUCCEEDED)
            that(steps.last().text).isEqualTo("Bake succeeded")
            that(steps.last().link).isEqualTo("www.bake.com/$version")
          }
        }
      }
    }

    context("don't need a NOT_STARTED event to calculate steps") {
      before {
        subject.saveEvent(event.copy(status = RUNNING))
        clock.tickMinutes(1)
        subject.saveEvent(event.copy(status = SUCCEEDED, text = "Bake finished! Here's your cake", link = null))
      }

      test("successful step generated") {
        val steps = subject.getSteps(artifact, version)
        expect {
          that(steps.size).isEqualTo(1)
          that(steps.first().status).isEqualTo(SUCCEEDED)
          that(steps.first().text).isEqualTo("Bake finished! Here's your cake")
          that(steps.first().link).isEqualTo("www.bake.com/$version")
          that(steps.first().startedAt).isNotNull()
          that(steps.first().completedAt).isNotNull()
        }
      }
    }

    context("time") {
      var startTime: Instant? = null
      var endTime: Instant? = null
      before {
        startTime = clock.instant()
        subject.saveEvent(event)
        clock.tickMinutes(1)
        subject.saveEvent(event.copy(status = RUNNING, text = null, link = null))
        clock.tickMinutes(1)
        subject.saveEvent(event.copy(status = SUCCEEDED, text = "Bake succeeded", link = null))
        endTime = clock.instant()
      }
      test("time is correct") {
        val steps = subject.getSteps(artifact, version)
        expect {
          that(steps.size).isEqualTo(1)
          that(steps.first().startedAt).isEqualTo(startTime)
          that(steps.first().completedAt).isEqualTo(endTime)
        }
      }
    }
  }
}
