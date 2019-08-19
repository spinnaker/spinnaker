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
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.api.uid
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.none
import java.time.Clock
import java.time.Duration
import java.util.UUID.randomUUID

abstract class ResourceRepositoryTests<T : ResourceRepository> : JUnit5Minutests {

  private val clock = MutableClock()

  abstract fun factory(clock: Clock): T

  open fun flush() {}

  data class Fixture<T : ResourceRepository>(
    val subject: T,
    val callback: (ResourceHeader) -> Unit = mockk(relaxed = true) // has to be relaxed due to https://github.com/mockk/mockk/issues/272
  )

  fun tests() = rootContext<Fixture<T>> {
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

      test("it can be retrieved by name") {
        val retrieved = subject.get<DummyResourceSpec>(resource.name)
        expectThat(retrieved).isEqualTo(resource)
      }

      test("it can be retrieved by uid") {
        val retrieved = subject.get<DummyResourceSpec>(resource.uid)
        expectThat(retrieved).isEqualTo(resource)
      }

      context("storing another resource with a different name") {
        before {
          subject.store(anotherResource)
          subject.appendHistory(ResourceCreated(resource, clock))
        }

        test("it does not overwrite the first resource") {
          subject.allResources(callback)

          verifyAll {
            callback(match { it.uid == resource.uid })
            callback(match { it.uid == anotherResource.uid })
          }
        }

        test("one resource is returned for each application") {
          expectThat(subject.getByApplication("fnord")).hasSize(1)
          expectThat(subject.getByApplication("wow")).hasSize(1)
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
          expectThat(subject.get<DummyResourceSpec>(resource.name))
            .get(Resource<*>::spec)
            .isEqualTo(updatedResource.spec)
        }
      }

      context("updating the state of the resource") {
        context("an event that should be ignored in history") {
          before {
            tick()
            subject.appendHistory(ResourceValid(resource, clock))
          }

          test("the event is not included in the history") {
            expectThat(subject.eventHistory(resource.uid))
              .none { isA<ResourceValid>() }
          }
        }

        context("an event that should be persisted in history") {
          before {
            tick()
            // TODO: ensure persisting a map with actual data
            subject.appendHistory(ResourceUpdated(resource, emptyMap(), clock))
          }

          test("the event is included in the history") {
            expectThat(subject.eventHistory(resource.uid))
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
              expectThat(subject.eventHistory(resource.uid))
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
                expectThat(subject.eventHistory(resource.uid))
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
              expectThat(subject.eventHistory(resource.uid))
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
          subject.delete(resource.name)
        }

        test("the resource is no longer returned when listing all resources") {
          subject.allResources(callback)

          verify(exactly = 0) { callback(any()) }
        }

        test("the resource can no longer be retrieved by name") {
          expectThrows<NoSuchResourceException> {
            subject.get<DummyResourceSpec>(resource.name)
          }
        }

        test("events for the resource are also deleted") {
          expectThrows<NoSuchResourceException>() {
            subject.eventHistory(resource.uid)
          }
        }
      }
    }
  }

  private fun tick() {
    clock.incrementBy(ONE_SECOND)
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

fun <T : List<E>, E> Assertion.Builder<T>.second(): Assertion.Builder<E> =
  get("second element %s") { this[1] }

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
