package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.keel.events.ApplicationActuationResumed
import com.netflix.spinnaker.keel.events.ApplicationEvent
import com.netflix.spinnaker.keel.events.PersistentEvent
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceActuationResumed
import com.netflix.spinnaker.keel.events.ResourceActuationVetoed
import com.netflix.spinnaker.keel.events.ResourceCheckError
import com.netflix.spinnaker.keel.events.ResourceCheckUnresolvable
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.ResourceTaskFailed
import com.netflix.spinnaker.keel.events.ResourceTaskSucceeded
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.ResourceStatus.ACTUATING
import com.netflix.spinnaker.keel.persistence.ResourceStatus.CREATED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.CURRENTLY_UNRESOLVABLE
import com.netflix.spinnaker.keel.persistence.ResourceStatus.DIFF
import com.netflix.spinnaker.keel.persistence.ResourceStatus.ERROR
import com.netflix.spinnaker.keel.persistence.ResourceStatus.HAPPY
import com.netflix.spinnaker.keel.persistence.ResourceStatus.PAUSED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.RESUMED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.UNHAPPY
import com.netflix.spinnaker.keel.persistence.ResourceStatus.VETOED
import com.netflix.spinnaker.keel.test.combinedInMemoryRepository
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.slot
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ResourceStatusServiceTests : JUnit5Minutests {
  companion object Fixture {
    val clock = MutableClock()
    val repository = combinedInMemoryRepository(clock)
    val actuationPauser = ActuationPauser(repository.resourceRepository, repository.pausedRepository, repository.publisher, clock)
    val subject = ResourceStatusService(repository.resourceRepository, actuationPauser)
    val resource = resource()
    val deliveryConfig = deliveryConfig(resource)
    val user = "keel@keel.io"
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
      }

      context("resource status") {
        context("when resource is paused") {
          before {
            actuationPauser.pauseResource(resource.id, user)
          }

          test("returns PAUSED") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(PAUSED)
          }
        }

        context("when resource's parent application is paused") {
          before {
            actuationPauser.pauseApplication(resource.application, user)
          }

          test("returns PAUSED") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(PAUSED)
          }
        }

        context("when last event is ResourceCreated") {
          before {
            repository.appendResourceHistory(ResourceCreated(resource, clock))
          }

          test("returns CREATED") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(CREATED)
          }
        }

        context("when last event is ResourceValid") {
          before {
            repository.appendResourceHistory(ResourceValid(resource, clock))
          }

          test("returns HAPPY") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(HAPPY)
          }
        }

        context("when last event is ResourceDeltaResolved") {
          before {
            repository.appendResourceHistory(ResourceDeltaResolved(resource, clock))
          }

          test("returns HAPPY") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(HAPPY)
          }
        }

        context("when last event is ResourceActuationLaunched") {
          before {
            repository.appendResourceHistory(ResourceActuationLaunched(resource, "plugin", emptyList(), clock))
          }

          test("returns ACTUATING") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(ACTUATING)
          }
        }

        context("when last event is ResourceTaskSucceeded") {
          before {
            repository.appendResourceHistory(ResourceTaskSucceeded(resource, emptyList(), clock))
          }

          test("returns HAPPY") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(ACTUATING)
          }
        }

        context("when last event is ResourceTaskFailed") {
          before {
            repository.appendResourceHistory(ResourceTaskFailed(resource, "plugin", emptyList(), clock))
          }

          test("returns ACTUATING") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(ACTUATING)
          }
        }

        context("when last event is ResourceCheckError") {
          before {
            repository.appendResourceHistory(ResourceCheckError(resource, object : SpinnakerException() {}, clock))
          }

          test("returns ERROR") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(ERROR)
          }
        }

        context("when last event is ResourceDeltaDetected") {
          before {
            repository.appendResourceHistory(ResourceDeltaDetected(resource, emptyMap(), clock))
          }

          test("returns DIFF") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(DIFF)
          }
        }

        context("when last event is ResourceMissing") {
          before {
            repository.appendResourceHistory(ResourceMissing(resource, clock))
          }

          test("returns DIFF") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(DIFF)
          }
        }

        context("when last event is ResourceActuationVetoed") {
          before {
            repository.appendResourceHistory(ResourceActuationVetoed(resource, "vetoed", clock))
          }

          test("returns VETOED") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(VETOED)
          }
        }

        context("when last event is ResourceActuationResumed") {
          before {
            repository.appendResourceHistory(ResourceActuationResumed(resource, user, clock))
          }

          test("returns RESUMED") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(RESUMED)
          }
        }

        context("when last event is ApplicationActuationResumed") {
          before {
            repository.appendApplicationHistory(ApplicationActuationResumed(resource.application, user, clock))
          }

          test("returns RESUMED") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(RESUMED)
          }
        }

        context("when last event is ResourceCheckUnresolvable") {
          before {
            repository.appendResourceHistory(ResourceCheckUnresolvable(resource, object : ResourceCurrentlyUnresolvable("") {}, clock))
          }

          test("returns CURRENTLY_UNRESOLVABLE") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(CURRENTLY_UNRESOLVABLE)
          }
        }

        context("when last several events alternate between ResourceDeltaDetected and ResourceActuationLaunched") {
          before {
            (1..5).forEach { _ ->
              repository.appendResourceHistory(ResourceDeltaDetected(resource, emptyMap(), clock))
              repository.appendResourceHistory(ResourceActuationLaunched(resource, "plugin", emptyList(), clock))
            }
          }

          test("returns UNHAPPY") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(UNHAPPY)
          }
        }
      }
    }
  }
}
