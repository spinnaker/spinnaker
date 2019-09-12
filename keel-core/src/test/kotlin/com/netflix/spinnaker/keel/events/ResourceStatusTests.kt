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

import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.exceptions.InvalidResourceFormatException
import com.netflix.spinnaker.keel.persistence.ResourceStatus.ACTUATING
import com.netflix.spinnaker.keel.persistence.ResourceStatus.CREATED
import com.netflix.spinnaker.keel.persistence.ResourceStatus.DIFF
import com.netflix.spinnaker.keel.persistence.ResourceStatus.ERROR
import com.netflix.spinnaker.keel.persistence.ResourceStatus.HAPPY
import com.netflix.spinnaker.keel.persistence.ResourceStatus.UNHAPPY
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal class ResourceStatusTests : JUnit5Minutests {
  object Fixture {
    val resourceRepository = InMemoryResourceRepository()
    val resource = resource()
    val createdEvent = ResourceCreated(resource)
    val missingEvent = ResourceMissing(resource)
    val deltaDetectedEvent = ResourceDeltaDetected(resource, mapOf("hi" to "wow"))
    val actuationLaunchedEvent = ResourceActuationLaunched(resource, "resourceHandlerPlugin", listOf(Task("1", "task name")))
    val deltaResolvedEvent = ResourceDeltaResolved(resource, "current")
    val errorEvent = ResourceCheckError(resource, InvalidResourceFormatException("bad resource", "who knows"))
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    before {
      resourceRepository.store(resource)
      resourceRepository.appendHistory(createdEvent)
    }

    after {
      resourceRepository.dropAll()
    }

    context("resource created") {
      test("returns created status") {
        expectThat(resourceRepository.getStatus(resource.id)).isEqualTo(CREATED)
      }
    }

    context("resource missing") {
      before {
        resourceRepository.appendHistory(missingEvent)
      }

      test("returns diff") {
        expectThat(resourceRepository.getStatus(resource.id)).isEqualTo(DIFF)
      }
    }

    context("resource delta was detected") {
      before {
        resourceRepository.appendHistory(missingEvent)
        resourceRepository.appendHistory(deltaDetectedEvent)
      }

      test("returns returns diff") {
        expectThat(resourceRepository.getStatus(resource.id)).isEqualTo(DIFF)
      }
    }

    context("resource actuation launched") {
      before {
        resourceRepository.appendHistory(missingEvent)
        resourceRepository.appendHistory(deltaDetectedEvent)
        resourceRepository.appendHistory(actuationLaunchedEvent)
      }

      test("returns returns actuating") {
        expectThat(resourceRepository.getStatus(resource.id)).isEqualTo(ACTUATING)
      }
    }

    context("resource is flapping") {
      before {
        resourceRepository.appendHistory(missingEvent)
        resourceRepository.appendHistory(deltaDetectedEvent)
        resourceRepository.appendHistory(actuationLaunchedEvent)
        resourceRepository.appendHistory(deltaDetectedEvent)
        resourceRepository.appendHistory(actuationLaunchedEvent)
        resourceRepository.appendHistory(deltaDetectedEvent)
        resourceRepository.appendHistory(actuationLaunchedEvent)
        resourceRepository.appendHistory(deltaDetectedEvent)
        resourceRepository.appendHistory(actuationLaunchedEvent)
        resourceRepository.appendHistory(deltaDetectedEvent)
        resourceRepository.appendHistory(actuationLaunchedEvent)
      }

      test("returns returns unhappy") {
        expectThat(resourceRepository.getStatus(resource.id)).isEqualTo(UNHAPPY)
      }
    }

    context("resource converged finally") {
      before {
        resourceRepository.appendHistory(missingEvent)
        resourceRepository.appendHistory(deltaDetectedEvent)
        resourceRepository.appendHistory(actuationLaunchedEvent)
        resourceRepository.appendHistory(deltaDetectedEvent)
        resourceRepository.appendHistory(actuationLaunchedEvent)
        resourceRepository.appendHistory(deltaDetectedEvent)
        resourceRepository.appendHistory(actuationLaunchedEvent)
        resourceRepository.appendHistory(deltaDetectedEvent)
        resourceRepository.appendHistory(actuationLaunchedEvent)
        resourceRepository.appendHistory(deltaDetectedEvent)
        resourceRepository.appendHistory(actuationLaunchedEvent)
        resourceRepository.appendHistory(deltaResolvedEvent)
      }

      test("returns returns happy") {
        expectThat(resourceRepository.getStatus(resource.id)).isEqualTo(HAPPY)
      }
    }

    context("resource delta resolved") {
      before {
        resourceRepository.appendHistory(missingEvent)
        resourceRepository.appendHistory(deltaDetectedEvent)
        resourceRepository.appendHistory(actuationLaunchedEvent)
        resourceRepository.appendHistory(deltaResolvedEvent)
      }

      test("returns returns happy") {
        expectThat(resourceRepository.getStatus(resource.id)).isEqualTo(HAPPY)
      }
    }

    context("resource in error state") {
      before {
        resourceRepository.appendHistory(errorEvent)
      }

      test("returns returns error") {
        expectThat(resourceRepository.getStatus(resource.id)).isEqualTo(ERROR)
      }
    }
  }
}
