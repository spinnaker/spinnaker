/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.time.Clock
import java.time.Duration
import java.util.UUID.randomUUID

abstract class ResourceRepositoryTests<T : ResourceRepository> : JUnit5Minutests {

  private val clock = MutableClock()

  abstract fun factory(clock: Clock): T

  open fun flush() {}

  data class Fixture<T : ResourceRepository>(
    val subject: T,
    val callback: (ResourceHeader) -> Unit
  )

  fun tests() = rootContext<Fixture<T>> {
    fixture {
      Fixture(
        subject = factory(clock),
        callback = mockk(relaxed = true) // has to be relaxed due to https://github.com/mockk/mockk/issues/272
      )
    }

    after { confirmVerified(callback) }
    after { flush() }

    context("no resources exist") {
      test("allResources is a no-op") {
        subject.allResources(callback)

        verify { callback wasNot Called }
      }

      test("getting state history throws an exception") {
        expectThrows<NoSuchResourceUID> {
          subject.eventHistory(randomUID())
        }
      }
    }

    context("a resource exists") {
      val resource = Resource(
        apiVersion = SPINNAKER_API_V1,
        metadata = ResourceMetadata(
          name = ResourceName("SecurityGroup:ec2:test:us-west-2:fnord"),
          resourceVersion = 1234L,
          uid = randomUID(),
          data = randomData()
        ),
        kind = "ec2:SecurityGroup",
        spec = randomData()
      )

      before {
        subject.store(resource)
        subject.appendHistory(ResourceCreated(resource, clock))
      }

      test("it is returned by allResources") {
        subject.allResources(callback)

        verify {
          callback.invoke(ResourceHeader(resource))
        }
      }

      test("it can be retrieved by name") {
        val retrieved = subject.get<Map<String, Any>>(resource.metadata.name)
        expectThat(retrieved).isEqualTo(resource)
      }

      test("it can be retrieved by uid") {
        val retrieved = subject.get<Map<String, Any>>(resource.metadata.uid)
        expectThat(retrieved).isEqualTo(resource)
      }

      context("storing another resource with a different name") {
        val anotherResource = Resource(
          metadata = ResourceMetadata(
            name = ResourceName("SecurityGroup:ec2:test:us-east-1:fnord"),
            resourceVersion = 1234L,
            uid = randomUID(),
            data = randomData()
          ),
          apiVersion = SPINNAKER_API_V1,
          kind = "ec2:SecurityGroup",
          spec = randomData()
        )

        before {
          subject.store(anotherResource)
          subject.appendHistory(ResourceCreated(resource, clock))
        }

        test("it does not overwrite the first resource") {
          subject.allResources(callback)

          verifyAll {
            callback(match { it.uid == resource.metadata.uid })
            callback(match { it.uid == anotherResource.metadata.uid })
          }
        }
      }


      context("storing a new version of the resource") {
        val updatedResource = resource.copy(
          spec = randomData()
        )

        before {
          clock.incrementBy(ONE_SECOND)
          subject.store(updatedResource)
        }

        test("it replaces the original resource") {
          expectThat(subject.get<Map<String, Any>>(resource.metadata.name))
            .get(Resource<*>::spec)
            .isEqualTo(updatedResource.spec)
        }
      }

      context("updating the state of the resource") {
        before {
          clock.incrementBy(ONE_SECOND)
          subject.appendHistory(ResourceUpdated(resource, clock))
        }

        test("the new state is included in the history") {
          expectThat(subject.eventHistory(resource.metadata.uid))
            .hasSize(2)
            .first()
            .isA<ResourceUpdated>()
        }

        context("updating the state again") {
          before {
            clock.incrementBy(ONE_SECOND)
            subject.appendHistory(ResourceDeltaDetected(resource, clock))
          }

          test("the new state is included in the history") {
            expectThat(subject.eventHistory(resource.metadata.uid))
              .hasSize(3)
              .first()
              .isA<ResourceDeltaDetected>()
          }
        }
      }

      context("deleting the resource") {
        before {
          subject.delete(resource.metadata.name)
        }

        test("the resource is no longer returned when listing all resources") {
          subject.allResources(callback)

          verify(exactly = 0) { callback(any()) }
        }

        test("the resource can no longer be retrieved by name") {
          expectThrows<NoSuchResourceException> {
            subject.get<Map<String, Any>>(resource.metadata.name)
          }
        }

        test("events for the resource are also deleted") {
          expectThrows<NoSuchResourceException>() {
            subject.eventHistory(resource.metadata.uid)
          }
        }
      }
    }
  }

  companion object {
    val ONE_SECOND: Duration = Duration.ofSeconds(1)
  }
}

fun randomData(length: Int = 4): Map<String, Any> {
  val map = mutableMapOf<String, Any>()
  (0 until length).forEach { _ ->
    map[randomString()] = randomString()
  }
  return map
}

fun randomString(length: Int = 8) =
  randomUUID()
    .toString()
    .map { it.toInt().toString(16) }
    .joinToString("")
    .substring(0 until length)

