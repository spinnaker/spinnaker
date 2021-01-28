package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.notifications.NotificationScope.RESOURCE
import com.netflix.spinnaker.keel.notifications.NotificationType.RESOURCE_UNHEALTHY
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.time.Clock
import java.time.Duration

abstract class NotificationRepositoryTests<T: NotificationRepository> : JUnit5Minutests {
  abstract fun factory(clock: Clock): T

  open fun T.flush() {}

  val clock = MutableClock()
  val resourceId = "ec2:cluster:test:us-west-2:keeldemo-managed"
  val application = "keeldemo"

  data class Fixture<T : NotificationRepository>(
    val subject: T
  )

  fun tests() = rootContext<Fixture<T>> {
    fixture {
      Fixture(subject = factory(clock))
    }

    after { subject.flush() }

    context("new notification"){
      test("notification is due when we add it") {
        expectThat(subject.addNotification(RESOURCE, "id", RESOURCE_UNHEALTHY)).isTrue()
      }
    }

    context("existing notification") {
      before {
        subject.addNotification(RESOURCE, "id", RESOURCE_UNHEALTHY)
        clock.incrementBy(Duration.ofDays(1) + Duration.ofHours(1))
      }

      test("due for notification") {
        expectThat(subject.dueForNotification(RESOURCE, "id", RESOURCE_UNHEALTHY)).isTrue()
      }

      test("marking sent means it's no longer due") {
        subject.markSent(RESOURCE, "id", RESOURCE_UNHEALTHY)
        expectThat(subject.dueForNotification(RESOURCE, "id", RESOURCE_UNHEALTHY)).isFalse()
      }

      test("removing means we should not notify") {
        subject.clearNotification(RESOURCE, "id", RESOURCE_UNHEALTHY)
        clock.incrementBy(Duration.ofDays(1) + Duration.ofHours(1))
        expectThat(subject.dueForNotification(RESOURCE, "id", RESOURCE_UNHEALTHY)).isFalse()
      }
    }
  }

}
