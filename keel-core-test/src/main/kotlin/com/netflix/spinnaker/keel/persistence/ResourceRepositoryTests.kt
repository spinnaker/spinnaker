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
import strikt.api.Assertion
import strikt.api.expect
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.contains
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotEqualTo
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

      test("deleting a non-existent resource throws an exception") {
        expectThrows<NoSuchResourceName> {
          subject.delete(ResourceName("whatever"))
        }
      }
    }

    context("a resource exists") {
      val resource = Resource(
        apiVersion = SPINNAKER_API_V1,
        metadata = ResourceMetadata(
          name = ResourceName("ec2:security-group:test:us-west-2:fnord"),
          uid = randomUID(),
          data = randomData()
        ),
        kind = "security-group",
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
            name = ResourceName("ec2:security-group:test:us-east-1:fnord"),
            uid = randomUID(),
            data = randomData()
          ),
          apiVersion = SPINNAKER_API_V1,
          kind = "security-group",
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
          // TODO: ensure persisting a map with actual data
          subject.appendHistory(ResourceUpdated(resource, emptyMap(), clock))
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
            // TODO: ensure persisting a map with actual data
            subject.appendHistory(ResourceDeltaDetected(resource, emptyMap(), clock))
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

  data class CheckLockFixture<T : ResourceRepository>(
    val subject: T,
    val ifNotCheckedInLast: Duration = Duration.ofMinutes(30),
    val limit: Int = 2
  ) {
    fun nextResults(): Collection<ResourceHeader> =
      subject.nextResourcesDueForCheck(ifNotCheckedInLast, limit)
  }

  fun checkLockTests() = rootContext<CheckLockFixture<T>> {
    fixture {
      CheckLockFixture(
        subject = factory(clock)
      )
    }

    after { flush() }

    context("no resources exist") {
      test("returns an empty collection") {
        expectThat(nextResults()).isEmpty()
      }
    }

    context("multiple resources exist") {
      before {
        repeat(4) {
          val resource = Resource(
            apiVersion = SPINNAKER_API_V1,
            metadata = ResourceMetadata(
              name = ResourceName("ec2:security-group:test:us-west-2:fnord-$it"),
              uid = randomUID(),
              data = randomData()
            ),
            kind = "security-group",
            spec = randomData()
          )
          subject.store(resource)
        }
      }

      test("next resources returns at most 2 resources") {
        expectThat(nextResults()).hasSize(limit)
      }

      test("multiple calls to next resources return different results") {
        val results1 = nextResults()
        val results2 = nextResults()
        expect {
          that(results1)
            .hasSize(2)
            .isNotEqualTo(results2)
            .all {
              isNotIn(results2)
            }
          that(results2).hasSize(2)
        }
      }

      test("once all resources are exhausted no more results are returned") {
        val results1 = nextResults()
        val results2 = nextResults()
        val results3 = nextResults()
        expect {
          that(results1).isNotEmpty().hasSize(2)
          that(results2).isNotEmpty().hasSize(2)
          that(results3).isEmpty()
        }
      }

      test("once time passes the same resources are returned again") {
        val results1 = nextResults()
        clock.incrementBy(ifNotCheckedInLast / 2)
        clock.incrementBy(Duration.ofSeconds(1))
        val results2 = nextResults()
        clock.incrementBy(ifNotCheckedInLast / 2)
        clock.incrementBy(Duration.ofSeconds(1))
        val results3 = nextResults()
        expect {
          that(results1).isNotEmpty()
          that(results2).isNotEmpty().isNotEqualTo(results1)
          that(results3).isNotEmpty().isEqualTo(results1)
        }
      }

      test("if a resource was explicitly marked due for checking is it returned") {
        val resource = subject.get(
          ResourceName("ec2:security-group:test:us-west-2:fnord-0"),
          Any::class.java
        )

        nextResults()
        nextResults()

        subject.markCheckDue(resource)
        clock.incrementBy(Duration.ofSeconds(1))

        val results3 = nextResults()

        expect {
          that(results3).hasSize(1).contains(ResourceHeader(resource))
        }
      }
    }
  }

  companion object {
    val ONE_SECOND: Duration = Duration.ofSeconds(1)
  }
}

operator fun Duration.div(divisor: Long): Duration = dividedBy(divisor)

fun <T : Any> Assertion.Builder<T>.isNotIn(expected: Collection<T>) =
  assert("is not in $expected") {
    if (!expected.contains(it)) pass() else fail()
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
