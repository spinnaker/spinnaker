package com.netflix.spinnaker.keel.echo

import com.netflix.spectator.api.NoopRegistry
import com.netflix.spinnaker.config.KeelNotificationConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency.quiet
import com.netflix.spinnaker.keel.api.NotificationType.email
import com.netflix.spinnaker.keel.api.NotificationType.slack
import com.netflix.spinnaker.keel.events.ClearNotificationEvent
import com.netflix.spinnaker.keel.notifications.Notification
import com.netflix.spinnaker.keel.events.UnhealthyNotification
import com.netflix.spinnaker.keel.notifications.NotificationScope.RESOURCE
import com.netflix.spinnaker.keel.notifications.NotificationType.RESOURCE_UNHEALTHY
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoSuchResourceId
import com.netflix.spinnaker.keel.persistence.NotificationRepository
import com.netflix.spinnaker.keel.persistence.OrphanedResourceException
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import strikt.api.expectCatching
import strikt.assertions.isSuccess
import org.springframework.core.env.Environment as SpringframeworkCoreEnvEnvironment


class NotifierTests : JUnit5Minutests {

  class Fixture {
    val keelNotificationConfig = KeelNotificationConfig()
    val echoService: EchoService = mockk(relaxed = true)
    val repository: KeelRepository = mockk(relaxed = true)
    val notificationRepository: NotificationRepository = mockk(relaxed = true)
    val springEnv: SpringframeworkCoreEnvEnvironment = mockk(relaxed = true) {
      every { getProperty("keel.notifications.resource", Boolean::class.java, true)} returns true
    }
    val registry: NoopRegistry = NoopRegistry()
    val subject = Notifier(echoService, repository, notificationRepository, keelNotificationConfig, springEnv, registry)

    val r = resource()
    val env = Environment(
      name = "test",
      resources = setOf(r),
      notifications = setOf(
        NotificationConfig(type = slack, address = "#ohmy", frequency = quiet),
        NotificationConfig(type = email, address = "oh@my.com", frequency = quiet)
      ),
      constraints = setOf()
    )
    val event = UnhealthyNotification(
      r.id,
      Notification("hi", "you")
    )

    val clearEvent = ClearNotificationEvent(
      RESOURCE,
      r.id,
      RESOURCE_UNHEALTHY,
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("resource exists") {
      before {
        every { repository.getResource(r.id) } returns r
        every { repository.environmentFor(r.id) } returns env
      }

      context("new notification") {
        before {
          every { notificationRepository.addNotification(event.scope, event.ref, event.type)} returns true
        }

        test("two notifications fire (slack and email)") {
          subject.onResourceNotificationEvent(event)
          coVerify(exactly = 2) { echoService.sendNotification(any()) }
        }
      }

      context("notification already exists") {
        before {
          every { notificationRepository.addNotification(event.scope, event.ref, event.type)} returns false
        }

        test("no notifications fire") {
          subject.onResourceNotificationEvent(event)
          coVerify(exactly = 0) { echoService.sendNotification(any()) }
        }
      }
    }

    context("resource doesn't exist") {
      before {
        every { notificationRepository.addNotification(event.scope, event.ref, event.type)} returns true
        every { repository.getResource(r.id) } throws NoSuchResourceId(r.id)
      }

      test("no notifications fire") {
        expectCatching {
          subject.onResourceNotificationEvent(event)
        }.isSuccess()
        coVerify(exactly = 0) { echoService.sendNotification(any()) }
      }
    }

    context("env doesn't exist") {
      before {
        every { notificationRepository.addNotification(event.scope, event.ref, event.type)} returns true
        every { repository.getResource(r.id) } returns r
        every { repository.environmentFor(r.id) } throws OrphanedResourceException(r.id)
      }

      test("no notifications fire") {
        expectCatching {
          subject.onResourceNotificationEvent(event)
        }.isSuccess()
        coVerify(exactly = 0) { echoService.sendNotification(any()) }
      }
    }

    context("clearing notifications") {
      context("notification exists") {
        before {
          every { notificationRepository.addNotification(event.scope, event.ref, event.type)} returns true
          every { repository.getResource(r.id) } returns r
          every { repository.environmentFor(r.id) } returns env
        }

        test("clearing works") {
          subject.onClearNotificationEvent(clearEvent)
          verify(exactly = 1) { notificationRepository.clearNotification(event.scope, event.ref, event.type) }
        }
      }
      context("nothing exists") {
        test("clearing works") {
          subject.onClearNotificationEvent(clearEvent)
          verify(exactly = 1) { notificationRepository.clearNotification(event.scope, event.ref, event.type) }
        }
      }

    }
  }
}
