package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.keel.events.ApplicationActuationResumed
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceActuationResumed
import com.netflix.spinnaker.keel.events.ResourceActuationVetoed
import com.netflix.spinnaker.keel.events.ResourceCheckError
import com.netflix.spinnaker.keel.events.ResourceCheckUnresolvable
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceHistoryEvent
import com.netflix.spinnaker.keel.events.ResourceMissing
import com.netflix.spinnaker.keel.events.ResourceTaskFailed
import com.netflix.spinnaker.keel.events.ResourceTaskSucceeded
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.ResourceStatus.ACTUATING
import com.netflix.spinnaker.keel.persistence.ResourceStatus.CREATED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.CURRENTLY_UNRESOLVABLE
import com.netflix.spinnaker.keel.persistence.ResourceStatus.DIFF
import com.netflix.spinnaker.keel.persistence.ResourceStatus.ERROR
import com.netflix.spinnaker.keel.persistence.ResourceStatus.HAPPY
import com.netflix.spinnaker.keel.persistence.ResourceStatus.MISSING_DEPENDENCY
import com.netflix.spinnaker.keel.persistence.ResourceStatus.PAUSED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.RESUMED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.UNHAPPY
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.kork.exceptions.SpinnakerException
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.isEqualTo

class ResourceStatusServiceTests : JUnit5Minutests {
  companion object Fixture {
    val repository = mockk<ResourceRepository>()
    val actuationPauser = mockk<ActuationPauser>()
    val subject = ResourceStatusService(repository, actuationPauser)
    val resource = resource()
    val user = "keel@keel.io"

    val events = mutableListOf<ResourceHistoryEvent>()
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture
    }

    context("a delivery config with a resource exists") {
      before {
        every { repository.get(resource.id) } returns resource
        every { repository.eventHistory(resource.id) } answers { events.reversed() }
      }

      context("resource status") {
        context("when resource is paused") {
          before {
            every { actuationPauser.isPaused(resource.id) } returns true
          }

          test("returns PAUSED") {
            expectThat(subject.getStatus(resource.id)).isEqualTo(PAUSED)
          }
        }

        context("when resource is not paused") {
          before {
            every { actuationPauser.isPaused(resource.id) } returns false
          }
          context("when last event is ResourceCreated") {
            before {
              events.add(ResourceCreated(resource))
            }

            test("returns CREATED") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(CREATED)
            }
          }

          context("when last event is ResourceValid") {
            before {
              events.add(ResourceValid(resource))
            }

            test("returns HAPPY") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(HAPPY)
            }
          }

          context("when last event is ResourceDeltaResolved") {
            before {
              events.add(ResourceDeltaResolved(resource))
            }

            test("returns HAPPY") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(HAPPY)
            }
          }

          context("when last event is ResourceActuationLaunched") {
            before {
              events.add(ResourceActuationLaunched(resource, "plugin", emptyList()))
            }

            test("returns ACTUATING") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(ACTUATING)
            }
          }

          context("when last event is ResourceTaskSucceeded") {
            before {
              events.add(ResourceTaskSucceeded(resource, emptyList()))
            }

            test("returns HAPPY") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(ACTUATING)
            }
          }

          context("when last event is ResourceTaskFailed") {
            before {
              events.add(ResourceTaskFailed(resource, "plugin", emptyList()))
            }

            test("returns ACTUATING") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(ACTUATING)
            }
          }

          context("when last event is ResourceCheckError") {
            before {
              events.add(ResourceCheckError(resource, object : SpinnakerException() {}))
            }

            test("returns ERROR") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(ERROR)
            }
          }

          context("when last event is ResourceDeltaDetected") {
            before {
              events.add(ResourceDeltaDetected(resource, emptyMap()))
            }

            test("returns DIFF") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(DIFF)
            }
          }

          context("when last event is ResourceMissing") {
            before {
              events.add(ResourceMissing(resource))
            }

            test("returns DIFF") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(DIFF)
            }
          }

          context("when last event is ResourceActuationVetoed with no status provided") {
            before {
              events.add(ResourceActuationVetoed(resource, "vetoed", "UnhappyVeto", null))
            }

            test("returns UNHAPPY") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(UNHAPPY)
            }
          }

          context("when last event is ResourceActuationVetoed due to a missing dependency") {
            before {
              events.add(ResourceActuationVetoed(resource, "vetoed", "RequiredBlahVeto", MISSING_DEPENDENCY))
            }

            test("returns MISSING_DEPENDENCY") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(MISSING_DEPENDENCY)
            }
          }

          context("when last event is ResourceActuationVetoed because missing dependency, but an older version of keel saved this event") {
            before {
              events.add(ResourceActuationVetoed(resource, "Load balancer blah is not found in test / us-west-2", "RequiredBlahVeto", null))
            }

            test("returns MISSING_DEPENDENCY") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(MISSING_DEPENDENCY)
            }
          }

          context("when last event is ResourceActuationResumed") {
            before {
              events.add(ResourceActuationResumed(resource, user))
            }

            test("returns RESUMED") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(RESUMED)
            }
          }

          context("when last event is ApplicationActuationResumed") {
            before {
              events.add(ApplicationActuationResumed(resource.application, user))
            }

            test("returns RESUMED") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(RESUMED)
            }
          }

          context("when last event is ResourceCheckUnresolvable") {
            before {
              events.add(ResourceCheckUnresolvable(resource, object : ResourceCurrentlyUnresolvable("") {}))
            }

            test("returns CURRENTLY_UNRESOLVABLE") {
              expectThat(subject.getStatus(resource.id)).isEqualTo(CURRENTLY_UNRESOLVABLE)
            }
          }

          context("when last several events alternate between ResourceDeltaDetected and ResourceActuationLaunched") {
            before {
              (1..5).forEach { _ ->
                events.add(ResourceDeltaDetected(resource, emptyMap()))
                events.add(ResourceActuationLaunched(resource, "plugin", emptyList()))
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
}
