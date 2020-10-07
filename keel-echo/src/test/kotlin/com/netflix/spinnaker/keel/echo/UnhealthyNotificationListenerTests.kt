package com.netflix.spinnaker.keel.echo

import com.netflix.spinnaker.config.UnhealthyNotificationConfig
import com.netflix.spinnaker.keel.api.NotificationConfig
import com.netflix.spinnaker.keel.api.NotificationFrequency
import com.netflix.spinnaker.keel.api.NotificationType
import com.netflix.spinnaker.keel.events.ClearNotificationEvent
import com.netflix.spinnaker.keel.events.NotificationEvent
import com.netflix.spinnaker.keel.events.ResourceHealthEvent
import com.netflix.spinnaker.keel.notifications.NotificationScope.RESOURCE
import com.netflix.spinnaker.keel.notifications.NotificationType.UNHEALTHY_RESOURCE
import com.netflix.spinnaker.keel.persistence.UnhealthyRepository
import com.netflix.spinnaker.keel.test.locatableResource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher
import org.springframework.core.env.Environment
import java.time.Duration

class UnhealthyNotificationListenerTests : JUnit5Minutests {
  class Fixture {
    val config = UnhealthyNotificationConfig()
    val unhealthyRepository: UnhealthyRepository = mockk(relaxed = true)
    val springEnv: Environment = mockk(relaxed = true) {
      every { getProperty("keel.notifications.unhealthy", Boolean::class.java, true)} returns true
    }
    val publisher: ApplicationEventPublisher = mockk(relaxed = true)
    val subject = UnhealthyNotificationListener(
      config,
      unhealthyRepository,
      publisher,
      springEnv,
      "https://wwwblah.com"
    )

    val r = locatableResource()
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture()
    }

    context("no existing record") {
      before {
        every {
          unhealthyRepository.durationUnhealthy(r.id)
        } returns Duration.ZERO
      }

      test("healthy marks resource healthy and clears unhealthy notification") {
        subject.onResourceHealthEvent(ResourceHealthEvent(resource = r, isHealthy = true))
        verify(exactly = 1) { unhealthyRepository.markHealthy(r.id) }
        verify(exactly = 1) { publisher.publishEvent(ClearNotificationEvent(RESOURCE, r.id, UNHEALTHY_RESOURCE)) }
      }

      test("unhealthy adds a record") {
        subject.onResourceHealthEvent(ResourceHealthEvent(resource = r, isHealthy = false))
        verify(exactly = 1) { unhealthyRepository.markUnhealthy(r.id) }
        verify(exactly = 0) { publisher.publishEvent(any()) }
      }
    }

    context("existing unhealthy record") {
      before {
        subject.onResourceHealthEvent(ResourceHealthEvent(resource = r, isHealthy = false))
      }

      context("it's been 4 minutes (less than the min time)") {
        before {
          every {
            unhealthyRepository.durationUnhealthy(r.id)
          } returns Duration.ofMinutes(4)
        }

        test("we still don't notify") {
          subject.onResourceHealthEvent(ResourceHealthEvent(resource = r, isHealthy = false))
          verify(exactly = 0) { publisher.publishEvent(any()) }
        }
      }

      context("it's been 6 minutes (more than the min time)") {
        before {
          every {
            unhealthyRepository.durationUnhealthy(r.id)
          } returns Duration.ofMinutes(6)
        }

        test("we notify") {
          subject.onResourceHealthEvent(ResourceHealthEvent(resource = r, isHealthy = false))
          verify(exactly = 1) {
            publisher.publishEvent(
              NotificationEvent(RESOURCE, r.id, UNHEALTHY_RESOURCE, subject.message(r, Duration.ofMinutes(6)))
            )
          }
        }
      }
    }
  }
}
