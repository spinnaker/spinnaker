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

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.events.ApplicationActuationPaused
import com.netflix.spinnaker.keel.events.ApplicationActuationResumed
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceEvent
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.deliveryConfig
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
import org.springframework.context.ApplicationEventPublisher
import strikt.api.Assertion
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.all
import strikt.assertions.contains
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isGreaterThanOrEqualTo
import strikt.assertions.isNotEmpty
import strikt.assertions.isSuccess
import strikt.assertions.map
import java.time.Clock
import java.time.Duration
import java.time.Period
import java.util.UUID.randomUUID

abstract class ResourceRepositoryTests<T : ResourceRepository> : JUnit5Minutests {

  protected val clock = MutableClock()

  val publisher: ApplicationEventPublisher = mockk(relaxed = true)

  abstract fun factory(clock: Clock, publisher: ApplicationEventPublisher): T

  abstract val storeDeliveryConfig: (DeliveryConfig) -> Unit

  open fun flush() {}

  data class Fixture<T : ResourceRepository>(
    val deliveryConfig: DeliveryConfig = DeliveryConfig(
      name = "manifest",
      application = "toast",
      serviceAccount = "keel@spinnaker"
    ),
    val user: String = "keel@keel.io",
    val subject: T,
    val callback: (ResourceHeader) -> Unit = mockk(relaxed = true) // has to be relaxed due to https://github.com/mockk/mockk/issues/272
  )

  fun tests(): RootContextBuilder = rootContext<Fixture<T>> {
    fixture {
      Fixture(subject = factory(clock, publisher))
    }

    after { confirmVerified(callback) }
    after { flush() }

    context("no resources exist") {
      test("allResources is a no-op") {
        subject.allResources(callback)

        verify { callback wasNot Called }
      }

      test("getting state history returns an empty list") {
        expectThat(subject.eventHistory("whatever"))
          .isEmpty()
      }

      test("deleting a non-existent resource throws an exception") {
        expectThrows<NoSuchResourceId> {
          subject.delete("whatever")
        }
      }

      test("recheck does not throw an exception") {
        expectCatching { subject.triggerResourceRecheck("oh", "my") }.isSuccess()
      }
    }

    context("a resource exists") {
      val resource = resource()
      val deliveryConfig = deliveryConfig(resource = resource)
      val anotherResource = resource(application = "wow")
      val anotherDeliveryConfig = deliveryConfig(application = "wow", resource = anotherResource)

      before {
        subject.store(resource)
        storeDeliveryConfig(deliveryConfig)
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

      context("storing another resource with a different name") {
        before {
          subject.store(anotherResource)
          storeDeliveryConfig(anotherDeliveryConfig)
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
        val deliveryConfig = deliveryConfig(application = "toast", resource = lr)
        before {
          subject.store(lr)
          storeDeliveryConfig(deliveryConfig)
          subject.appendHistory(ResourceCreated(lr, clock))
        }

        test("one resource is returned for the application") {
          expectThat(subject.getResourceIdsByApplication("toast")).hasSize(1)
        }

        test("full resource returned for the application") {
          expectThat(subject.getResourcesByApplication("toast")).hasSize(1)
        }
      }

      context("storing a new version of the resource") {
        val updatedResource = resource.copy(
          spec = resource.spec.copy(data = randomString())
        )

        before {
          tick()
          with(subject) {
            store(updatedResource).also {
              appendHistory(ResourceUpdated(it, mapOf("delta" to "some-difference"), clock))
            }
          }
        }

        test("the resource spec is unchanged until linked to a new environment version") {
          expectThat(subject.get<DummyResourceSpec>(resource.id))
            .get(Resource<*>::spec)
            .isEqualTo(resource.spec)
        }

        test("the existing resource version still active until linked to a new environment version") {
          expectThat(subject.get<DummyResourceSpec>(resource.id))
            .get(Resource<*>::version) isEqualTo 1
        }

        test("history includes events for the previous version of the resource") {
          expectThat(subject.eventHistory(resource.id) as List<ResourceEvent>)
            .map(ResourceEvent::version)
            .contains(1, 2)
        }

        context("when deleted") {
          before {
            subject.delete(resource.id)
          }

          test("all versions of the resource are deleted") {
            expectCatching {
              subject.get(resource.id)
            }
              .isFailure()
              .isA<NoSuchResourceException>()
          }
        }
      }

      context("updating the event history of the resource") {
        context("appending a resource event") {
          before {
            tick()
            subject.appendHistory(ResourceUpdated(resource, mapOf("delta" to "some-difference"), clock))
          }

          test("the event is included in the resource history") {
            expectThat(subject.eventHistory(resource.id))
              .hasSize(2)
              .first()
              .isA<ResourceUpdated>()
              .get { delta }.isEqualTo(mapOf("delta" to "some-difference"))
          }
        }

        context("appending an identical resource event with duplicates allowed") {
          before {
            tick()
            subject.appendHistory(ResourceUpdated(resource, emptyMap(), clock))
            tick()
            subject.appendHistory(ResourceUpdated(resource, emptyMap(), clock))
          }

          test("the event is included in the resource history") {
            expectThat(subject.eventHistory(resource.id))
              .hasSize(3)
              .and {
                first().isA<ResourceUpdated>()
                second().isA<ResourceUpdated>()
              }
          }
        }

        context("appending a resource event with duplicates disallowed the first time") {
          before {
            tick()
            subject.appendHistory(ResourceValid(resource, clock))
          }

          test("the event is included in the resource history") {
            expectThat(subject.eventHistory(resource.id))
              .hasSize(2)
              .first()
              .isA<ResourceValid>()
          }
        }

        context("appending an identical resource event with duplicates disallowed the second time") {
          before {
            tick()
            subject.appendHistory(ResourceValid(resource, clock))
            tick()
            subject.appendHistory(ResourceValid(resource, clock))
          }

          test("the event is not included in the resource history") {
            expectThat(subject.eventHistory(resource.id)) {
              first().isA<ResourceValid>()
              second().not().isA<ResourceValid>()
            }
          }
        }

        context("appending application events that affect resource history") {
          before {
            tick()
            subject.appendHistory(ApplicationActuationPaused(resource.application, user, null, clock))
            tick()
            subject.appendHistory(ApplicationActuationResumed(resource.application, user,  clock))
          }

          test("the events are included in the resource history") {
            expectThat(subject.eventHistory(resource.id)) {
              first().isA<ApplicationActuationResumed>()
              second().isA<ApplicationActuationPaused>()
            }
          }
        }

        context("appending a resource event identical to the last resource event, but with a relevant application events in between") {
          before {
            tick()
            subject.appendHistory(ResourceValid(resource, clock))
            tick()
            subject.appendHistory(ApplicationActuationPaused(resource.application, user, null, clock))
            tick()
            subject.appendHistory(ApplicationActuationResumed(resource.application, user, clock))
            tick()
            subject.appendHistory(ResourceValid(resource, clock))
          }

          test("the event is included in the resource history") {
            expectThat(subject.eventHistory(resource.id)) {
              first().isA<ResourceValid>()
              second().isA<ApplicationActuationResumed>()
              third().isA<ApplicationActuationPaused>()
              fourth().isA<ResourceValid>()
            }
          }
        }
      }

      context("deleting the resource") {
        before {
          subject.appendHistory(ApplicationActuationPaused(resource.application, user, null, clock))
          subject.appendHistory(ApplicationActuationResumed(resource.application, user, clock))
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
          expectThat(subject.eventHistory(resource.id))
            .isEmpty()
        }

        test("events for the resource's parent application remain") {
          expectThat(
            subject.applicationEventHistory(resource.application)
          ).hasSize(2)
        }
      }

      context("fetching event history for the resource") {
        before {
          repeat(3) {
            tick()
            subject.appendHistory(ResourceUpdated(resource, emptyMap(), clock))
            subject.appendHistory(ResourceDeltaDetected(resource, emptyMap(), clock))
            subject.appendHistory(ResourceActuationLaunched(resource, "whatever", emptyList(), clock))
            subject.appendHistory(ResourceDeltaResolved(resource, clock))
          }
          tick()
          subject.appendHistory(ApplicationActuationPaused(resource.application, user, null, clock))
          tick()
          subject.appendHistory(ApplicationActuationResumed(resource.application, user, clock))
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
            .hasSize(15)
        }

        test("zero limit is not allowed") {
          expectCatching { subject.eventHistory(resource.id, limit = 0) }
            .isFailure()
            .isA<IllegalArgumentException>()
        }

        test("negative limit is not allowed") {
          expectCatching { subject.eventHistory(resource.id, limit = -1) }
            .isFailure()
            .isA<IllegalArgumentException>()
        }

        test("event history includes relevant application events") {
          expectThat(subject.eventHistory(resource.id)) {
            first().isA<ApplicationActuationResumed>()
            second().isA<ApplicationActuationPaused>()
          }
        }
      }

      context("counting resource deletion attempts") {
        val resource = resource()
        before {
          subject.store(resource)
        }

        test("initial deletion attempt count is zero") {
          expectThat(subject.countDeletionAttempts(resource)).isEqualTo(0)
        }

        test("can increment deletion attempts") {
          subject.incrementDeletionAttempts(resource)
          subject.incrementDeletionAttempts(resource)
          expectThat(subject.countDeletionAttempts(resource)).isEqualTo(2)
        }
      }
    }
  }

  private fun tick() {
    clock.incrementBy(ONE_MINUTE)
  }

  companion object {
    val ONE_MINUTE: Duration = Duration.ofMinutes(1)
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

fun <T : List<E>, E> Assertion.Builder<T>.third(): Assertion.Builder<E> =
  get("third element %s") { this[2] }

fun <T : List<E>, E> Assertion.Builder<T>.fourth(): Assertion.Builder<E> =
  get("fourth element %s") { this[3] }

fun randomString(length: Int = 8) =
  randomUUID()
    .toString()
    .map { it.toInt().toString(16) }
    .joinToString("")
    .substring(0 until length)
