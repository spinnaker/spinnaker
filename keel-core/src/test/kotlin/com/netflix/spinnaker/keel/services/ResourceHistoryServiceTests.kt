package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.events.ApplicationActuationPaused
import com.netflix.spinnaker.keel.events.ApplicationActuationResumed
import com.netflix.spinnaker.keel.events.ApplicationEvent
import com.netflix.spinnaker.keel.events.PersistentEvent
import com.netflix.spinnaker.keel.events.ResourceActuationPaused
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.ResourceStatus.CREATED
import com.netflix.spinnaker.keel.test.combinedInMemoryRepository
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.slot
import java.time.Duration
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.map

class ResourceHistoryServiceTests : JUnit5Minutests {
  companion object Fixture {
    val clock = MutableClock()
    val repository = combinedInMemoryRepository(clock)
    val actuationPauser = ActuationPauser(repository.resourceRepository, repository.pausedRepository, repository.publisher, clock)
    val resourceHistoryService = ResourceHistoryService(repository, actuationPauser)
    val resource = resource()
    val deliveryConfig = deliveryConfig(resource)
    val user = "keel@keel.io"
    val TEN_MINUTES: Duration = Duration.ofMinutes(10)
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture
    }

    before {
      val event = slot<PersistentEvent>()
      every {
        repository.publisher.publishEvent(capture(event))
      } answers {
        if (event.captured is ApplicationEvent) {
          repository.appendApplicationHistory(event.captured as ApplicationEvent)
        } else {
          repository.appendResourceHistory(event.captured as ResourceEvent)
        }
      }
    }

    after {
      repository.dropAll()
      clock.reset()
    }

    context("a delivery config with a resource exists") {
      before {
        repository.upsertDeliveryConfig(deliveryConfig)
        repository.appendResourceHistory(ResourceCreated(resource, clock))
      }

      test("can get resource summary by delivery config") {
        val summaries = resourceHistoryService.getResourceSummariesFor(deliveryConfig)

        expect {
          that(summaries.size).isEqualTo(1)
          that(summaries.map { it.status }.filter { it == CREATED }.size).isEqualTo(1)
        }
      }
    }

    context("enriched resource event history") {
      context("with application paused at various times after resource creation") {
        before {
          repository.storeResource(resource)
          repository.appendResourceHistory(ResourceCreated(resource, clock))
          clock.incrementBy(TEN_MINUTES)
          repository.appendResourceHistory(ResourceValid(resource, clock))
          clock.incrementBy(TEN_MINUTES)
          actuationPauser.pauseApplication(resource.application, user)
          clock.incrementBy(TEN_MINUTES)
          actuationPauser.resumeApplication(resource.application, user)
          clock.incrementBy(TEN_MINUTES)
          actuationPauser.pauseApplication(resource.application, user)
        }

        test("has application paused and resumed events injected in the right positions") {
          val events = resourceHistoryService.getEnrichedEventHistory(resource.id)
          expectThat(events)
            .map { it.javaClass }
            .containsExactly(
              ApplicationActuationPaused::class.java,
              ApplicationActuationResumed::class.java,
              ApplicationActuationPaused::class.java,
              ResourceValid::class.java,
              ResourceCreated::class.java
            )
        }
      }

      context("with a new resource created after the application is paused") {
        before {
          actuationPauser.pauseApplication(resource.application, user)
          clock.incrementBy(TEN_MINUTES)
          repository.storeResource(resource)
          repository.appendResourceHistory(ResourceCreated(resource, clock))
        }

        test("has application paused event injected before resource creation") {
          val events = resourceHistoryService.getEnrichedEventHistory(resource.id)
          expectThat(events)
            .map { it.javaClass }
            .containsExactly(
              ResourceCreated::class.java,
              ApplicationActuationPaused::class.java
            )
        }

        test("paused event specifies who paused the application") {
          val events = resourceHistoryService.getEnrichedEventHistory(resource.id)
          expectThat(events.last())
            .isA<ApplicationActuationPaused>()
            .get { triggeredBy }
            .isEqualTo(user)
        }
      }

      context("with previous event history wiped and a new resource created after the application is paused") {
        before {
          repository.storeResource(resource)
          repository.appendResourceHistory(ResourceCreated(resource, clock))
          repository.deleteResource(resource.id)
          actuationPauser.pauseApplication(resource.application, user)
          clock.incrementBy(TEN_MINUTES)
          repository.resourceRepository.clearResourceEvents(resource.id)
          repository.resourceRepository.clearApplicationEvents(resource.application)
          repository.storeResource(resource)
          repository.appendResourceHistory(ResourceCreated(resource, clock))
        }

        test("still has application paused event injected before resource creation") {
          val events = resourceHistoryService.getEnrichedEventHistory(resource.id)
          expectThat(events)
            .map { it.javaClass }
            .containsExactly(
              ResourceCreated::class.java,
              ApplicationActuationPaused::class.java
            )
        }
      }

      context("with a resource recreated after being paused and deleted") {
        before {
          repository.storeResource(resource)
          repository.appendResourceHistory(ResourceCreated(resource, clock))
          clock.incrementBy(TEN_MINUTES)
          actuationPauser.pauseResource(resource.id, user)
          clock.incrementBy(TEN_MINUTES)
          repository.deleteResource(resource.id)
          clock.incrementBy(TEN_MINUTES)
          repository.storeResource(resource)
          repository.appendResourceHistory(ResourceCreated(resource, clock))
        }

        test("has resource paused event injected before resource creation") {
          val events = resourceHistoryService.getEnrichedEventHistory(resource.id)
          expectThat(events)
            .map { it.javaClass }
            .containsExactly(
              ResourceCreated::class.java,
              ResourceActuationPaused::class.java
            )
        }

        test("paused event specifies who paused the resource") {
          val events = resourceHistoryService.getEnrichedEventHistory(resource.id)
          expectThat(events.last())
            .isA<ResourceActuationPaused>()
            .get { triggeredBy }
            .isEqualTo(user)
        }
      }
    }
  }
}
