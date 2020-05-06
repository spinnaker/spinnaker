/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.core.ResourceCurrentlyUnresolvable
import com.netflix.spinnaker.keel.exceptions.InvalidResourceFormatException
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
import com.netflix.spinnaker.keel.persistence.memory.InMemoryPausedRepository
import com.netflix.spinnaker.keel.services.ResourceHistoryService
import com.netflix.spinnaker.keel.test.combinedInMemoryRepository
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.slot
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class ResourceStatusTests : JUnit5Minutests {
  object Fixture {
    val clock = MutableClock()
    val repository = combinedInMemoryRepository(clock)
    val pausedRepository = InMemoryPausedRepository()
    val actuationPauser = ActuationPauser(repository.resourceRepository, pausedRepository, repository.publisher, clock)
    val resourceHistoryService = ResourceHistoryService(repository, actuationPauser)
    val resource = resource()
    val createdEvent = ResourceCreated(resource)
    val missingEvent = ResourceMissing(resource)
    val deltaDetectedEvent = ResourceDeltaDetected(resource, mapOf("hi" to "wow"))
    val actuationLaunchedEvent = ResourceActuationLaunched(resource, "resourceHandlerPlugin", listOf(Task("1", "task name")))
    val deltaResolvedEvent = ResourceDeltaResolved(resource)
    val dependencyMissingEvent = ResourceCheckUnresolvable(resource, object : ResourceCurrentlyUnresolvable("I guess I can't find the AMI or something") {})
    val errorEvent = ResourceCheckError(resource, InvalidResourceFormatException("bad resource", "who knows"))
    val resourceValidEvent = ResourceValid(resource)
    val user = "keel@keel.io"
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    before {
      repository.storeResource(resource)
      repository.appendResourceHistory(createdEvent)

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
      pausedRepository.flush()
    }

    context("resource created") {
      test("returns created status") {
        expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(CREATED)
      }
    }

    context("resource missing") {
      before {
        repository.appendResourceHistory(missingEvent)
      }

      test("returns diff status") {
        expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(DIFF)
      }
    }

    context("resource delta was detected") {
      before {
        repository.appendResourceHistory(missingEvent)
        repository.appendResourceHistory(deltaDetectedEvent)
      }

      test("returns diff status") {
        expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(DIFF)
      }
    }

    context("resource actuation launched") {
      before {
        repository.appendResourceHistory(missingEvent)
        repository.appendResourceHistory(deltaDetectedEvent)
        repository.appendResourceHistory(actuationLaunchedEvent)
      }

      test("returns actuating status") {
        expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(ACTUATING)
      }
    }

    context("resource is flapping") {
      before {
        repository.appendResourceHistory(missingEvent)
        repository.appendResourceHistory(deltaDetectedEvent)
        repository.appendResourceHistory(actuationLaunchedEvent)
        repository.appendResourceHistory(deltaDetectedEvent)
        repository.appendResourceHistory(actuationLaunchedEvent)
        repository.appendResourceHistory(deltaDetectedEvent)
        repository.appendResourceHistory(actuationLaunchedEvent)
        repository.appendResourceHistory(deltaDetectedEvent)
        repository.appendResourceHistory(actuationLaunchedEvent)
        repository.appendResourceHistory(deltaDetectedEvent)
        repository.appendResourceHistory(actuationLaunchedEvent)
      }

      test("returns unhappy status") {
        expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(UNHAPPY)
      }
    }

    context("resource converged finally") {
      before {
        repository.appendResourceHistory(missingEvent)
        repository.appendResourceHistory(deltaDetectedEvent)
        repository.appendResourceHistory(actuationLaunchedEvent)
        repository.appendResourceHistory(deltaDetectedEvent)
        repository.appendResourceHistory(actuationLaunchedEvent)
        repository.appendResourceHistory(deltaDetectedEvent)
        repository.appendResourceHistory(actuationLaunchedEvent)
        repository.appendResourceHistory(deltaDetectedEvent)
        repository.appendResourceHistory(actuationLaunchedEvent)
        repository.appendResourceHistory(deltaDetectedEvent)
        repository.appendResourceHistory(actuationLaunchedEvent)
        repository.appendResourceHistory(deltaResolvedEvent)
      }

      test("returns happy status") {
        expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(HAPPY)
      }
    }

    context("resource delta resolved") {
      before {
        repository.appendResourceHistory(missingEvent)
        repository.appendResourceHistory(deltaDetectedEvent)
        repository.appendResourceHistory(actuationLaunchedEvent)
        repository.appendResourceHistory(deltaResolvedEvent)
      }

      test("returns happy status") {
        expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(HAPPY)
      }
    }

    context("resource valid") {
      before {
        repository.appendResourceHistory(resourceValidEvent)
      }

      test("returns happy status") {
        expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(HAPPY)
      }
    }

    context("resource in error state") {
      before {
        repository.appendResourceHistory(errorEvent)
      }

      test("returns error status") {
        expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(ERROR)
      }
    }

    context("resource actuation paused") {
      before {
        actuationPauser.pauseResource(resource.id, user)
      }

      test("returns paused status") {
        expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(PAUSED)
      }
    }

    context("resource actuation resumed") {
      before {
        actuationPauser.pauseResource(resource.id, user)
        clock.tickMinutes(10)
        actuationPauser.resumeResource(resource.id, user)
      }

      test("returns resumed status") {
        expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(RESUMED)
      }
    }

    context("resource dependency is missing") {
      before {
        repository.appendResourceHistory(deltaDetectedEvent)
        repository.appendResourceHistory(dependencyMissingEvent)
      }

      test("returns diff status") {
        expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(CURRENTLY_UNRESOLVABLE)
      }
    }

    context("application actuation paused") {
      context("after resource created") {
        before {
          actuationPauser.pauseApplication(resource.application, user)
        }

        test("returns paused status") {
          expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(PAUSED)
        }
      }

      context("before resource created") {
        before {
          repository.deleteResource(resource.id)
          actuationPauser.pauseApplication(resource.application, user)
          clock.tickMinutes(10)
          repository.storeResource(resource)
          repository.appendResourceHistory(createdEvent)
        }

        test("returns paused status") {
          expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(PAUSED)
        }
      }

      context("before resource created, but resumed after") {
        before {
          repository.deleteResource(resource.id)
          actuationPauser.pauseApplication(resource.application, user)
          clock.tickMinutes(10)
          repository.storeResource(resource)
          repository.appendResourceHistory(createdEvent)
          clock.tickMinutes(10)
          actuationPauser.resumeApplication(resource.application, user)
        }

        test("returns resumed status") {
          expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(RESUMED)
        }
      }
    }

    context("application actuation paused and resumed after resource created") {
      before {
        actuationPauser.pauseApplication(resource.application, user)
        clock.tickMinutes(10)
        actuationPauser.resumeApplication(resource.application, user)
      }

      test("returns resumed status") {
        expectThat(resourceHistoryService.getStatus(resource.id)).isEqualTo(RESUMED)
      }
    }
  }
}
