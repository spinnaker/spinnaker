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
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.locatableResource
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.RootContextBuilder
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import java.time.Clock
import java.time.Duration
import java.time.Period
import java.util.UUID.randomUUID
import strikt.api.Assertion
import strikt.api.expect
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.failed
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isGreaterThanOrEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotNull
import strikt.assertions.map

abstract class ResourceRepositoryTests<T : ResourceRepository> : JUnit5Minutests {

  private val clock = MutableClock()

  abstract fun factory(clock: Clock): T

  open fun flush() {}

  data class Fixture<T : ResourceRepository>(
    val subject: T,
    val callback: (ResourceHeader) -> Unit = mockk(relaxed = true) // has to be relaxed due to https://github.com/mockk/mockk/issues/272
  )

  fun tests(): RootContextBuilder = rootContext<Fixture<T>> {
    fixture {
      Fixture(subject = factory(clock))
    }

    after { confirmVerified(callback) }
    after { flush() }

    context("no resources exist") {
      test("allResources is a no-op") {
        subject.allResources(callback)

        verify { callback wasNot Called }
      }

      test("getting state history throws an exception") {
        expectThrows<NoSuchResourceId> {
          subject.eventHistory("whatever")
        }
      }

      test("deleting a non-existent resource throws an exception") {
        expectThrows<NoSuchResourceId> {
          subject.delete("whatever")
        }
      }
    }

    context("a resource exists") {
      val resource = resource()
      val anotherResource = resource(application = "wow")

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

      test("it can be retrieved by id") {
        val retrieved = subject.get<DummyResourceSpec>(resource.id)
        expectThat(retrieved).isEqualTo(resource)
      }

      test("it can be retrieved by uid") {
        val retrieved = subject.get<DummyResourceSpec>(resource.id)
        expectThat(retrieved).isEqualTo(resource)
      }

      context("storing another resource with a different name") {
        before {
          subject.store(anotherResource)
          subject.appendHistory(ResourceCreated(anotherResource, clock))
        }

        test("it does not overwrite the first resource") {
          subject.allResources(callback)

          verifyAll {
            callback(match { it.id == resource.id })
            callback(match { it.id == anotherResource.id })
          }
        }
      }

      context("retrieving resources by application") {
        val lr = locatableResource(application = "toast")
        before {
          subject.store(lr)
          subject.appendHistory(ResourceCreated(lr, clock))
        }

        test("one resource is returned for the application") {
          expectThat(subject.getResourceIdsByApplication("toast")).hasSize(1)
        }

        test("full resource returned for the application") {
          expectThat(subject.getResourcesByApplication("toast")).hasSize(1)
        }

        test("resource summary is formatted correctly") {
          val summary = subject.getSummaryByApplication("toast")

          expect {
            that(summary).hasSize(1)
            that(summary.first().id).isEqualTo(lr.id)
            that(summary.first().locations).isNotNull()
          }
        }
      }

      context("storing a new version of the resource") {
        val updatedResource = resource.copy(
          spec = resource.spec.copy(data = randomString())
        )

        before {
          tick()
          subject.store(updatedResource)
        }

        test("it replaces the original resource") {
          expectThat(subject.get<DummyResourceSpec>(resource.id))
            .get(Resource<*>::spec)
            .isEqualTo(updatedResource.spec)
        }
      }

      context("updating the state of the resource") {
        context("an event that should be persisted in history") {
          before {
            tick()
            // TODO: ensure persisting a map with actual data
            subject.appendHistory(ResourceUpdated(resource, emptyMap(), clock))
          }

          test("the event is included in the history") {
            expectThat(subject.eventHistory(resource.id))
              .hasSize(2)
              .first()
              .isA<ResourceUpdated>()
          }

          context("updating the state again") {
            before {
              tick()
              // TODO: ensure persisting a map with actual data
              subject.appendHistory(ResourceDeltaDetected(resource, emptyMap(), clock))
            }

            test("the event is included in the history") {
              expectThat(subject.eventHistory(resource.id))
                .hasSize(3)
                .first()
                .isA<ResourceDeltaDetected>()
            }

            context("a subsequent identical event that should be ignored") {
              before {
                tick()
                subject.appendHistory(ResourceDeltaDetected(resource, emptyMap(), clock))
              }

              test("the event is not included in the history") {
                expectThat(subject.eventHistory(resource.id))
                  .and {
                    first().isA<ResourceDeltaDetected>()
                    second().not().isA<ResourceDeltaDetected>()
                  }
              }
            }
          }

          context("a subsequent identical event") {
            before {
              tick()
              subject.appendHistory(ResourceUpdated(resource, emptyMap(), clock))
            }

            test("the new state is included in the history") {
              expectThat(subject.eventHistory(resource.id))
                .hasSize(3)
                .and {
                  first().isA<ResourceUpdated>()
                  second().isA<ResourceUpdated>()
                }
            }
          }
        }
      }

      context("deleting the resource") {
        before {
          subject.delete(resource.id)
        }

        test("the resource is no longer returned when listing all resources") {
          subject.allResources(callback)

          verify(exactly = 0) { callback(any()) }
        }

        test("the resource can no longer be retrieved by name") {
          expectThrows<NoSuchResourceException> {
            subject.get<DummyResourceSpec>(resource.id)
          }
        }

        test("events for the resource are also deleted") {
          expectThrows<NoSuchResourceException> {
            subject.eventHistory(resource.id)
          }
        }
      }

      context("deleting resource by application name") {
        before {
          subject.store(resource)
          subject.deleteByApplication(resource.application)
        }

        test("the resource is no longer returned when listing all resources") {
          subject.allResources(callback)
          verify(exactly = 0) { callback(any()) }
        }

        test("the resource can no longer be retrieved by name") {
          expectThrows<NoSuchResourceException> {
            subject.get<DummyResourceSpec>(resource.id)
          }
        }

        test("events for the resource are also deleted") {
          expectThrows<NoSuchResourceException> {
            subject.eventHistory(resource.id)
          }
        }

        test("deleting a non-existent application throws an exception") {
          expectThat(subject.deleteByApplication(resource.application)).isEqualTo(0)
        }
      }

      context("fetching event history for the resource") {
        before {
          repeat(3) {
            clock.incrementBy(TEN_MINUTES)
            subject.appendHistory(ResourceUpdated(resource, emptyMap(), clock))
            subject.appendHistory(ResourceDeltaDetected(resource, emptyMap(), clock))
            subject.appendHistory(ResourceActuationLaunched(resource, "whatever", emptyList(), clock))
            subject.appendHistory(ResourceDeltaResolved(resource, clock))
          }
          clock.incrementBy(TEN_MINUTES)
        }

        test("default limit is 10 events") {
          expectThat(subject.eventHistory(resource.id))
            .isNotEmpty()
            .hasSize(ResourceRepository.DEFAULT_MAX_EVENTS)
        }

        test("events can be limited by number") {
          expectThat(subject.eventHistory(resource.id, limit = 3))
            .hasSize(3)
        }

        test("the limit can be higher than the default") {
          expectThat(subject.eventHistory(resource.id, limit = 20))
            .hasSize(13)
        }

        test("zero limit is not allowed") {
          expectCatching { subject.eventHistory(resource.id, limit = 0) }
            .failed()
            .isA<IllegalArgumentException>()
        }

        test("negative limit is not allowed") {
          expectCatching { subject.eventHistory(resource.id, limit = -1) }
            .failed()
            .isA<IllegalArgumentException>()
        }
      }
    }
  }

  private fun tick() {
    clock.incrementBy(ONE_SECOND)
  }

  companion object {
    val ONE_SECOND: Duration = Duration.ofSeconds(1)
    val TEN_MINUTES: Duration = Duration.ofMinutes(10)
  }

  fun <T : Iterable<E>, E : ResourceEvent> Assertion.Builder<T>.areNoOlderThan(age: Period): Assertion.Builder<T> =
    and {
      val cutoff = clock.instant().minus(age)
      map { it.timestamp }
        .all { isGreaterThanOrEqualTo(cutoff) }
    }
}

operator fun Duration.div(divisor: Long): Duration = dividedBy(divisor)

fun <T : Any> Assertion.Builder<T>.isNotIn(expected: Collection<T>) =
  assert("is not in $expected") {
    if (!expected.contains(it)) pass() else fail()
  }

fun <T : List<E>, E> Assertion.Builder<T>.second(): Assertion.Builder<E> =
  get("second element %s") { this[1] }

fun randomString(length: Int = 8) =
  randomUUID()
    .toString()
    .map { it.toInt().toString(16) }
    .joinToString("")
    .substring(0 until length)
