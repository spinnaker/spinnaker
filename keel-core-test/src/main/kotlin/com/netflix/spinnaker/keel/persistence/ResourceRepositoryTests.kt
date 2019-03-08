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
import com.netflix.spinnaker.keel.persistence.ResourceState.Diff
import com.netflix.spinnaker.keel.persistence.ResourceState.Ok
import com.netflix.spinnaker.keel.persistence.ResourceState.Unknown
import com.nhaarman.mockitokotlin2.argumentCaptor
import com.nhaarman.mockitokotlin2.eq
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.never
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.times
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.verifyZeroInteractions
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.map
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAmount
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
        callback = mock()
      )
    }

    after { reset(callback) }
    after { flush() }

    context("no resources exist") {
      test("allResources is a no-op") {
        subject.allResources(callback)

        verifyZeroInteractions(callback)
      }
    }

    context("a resource exists") {
      val resource = Resource(
        apiVersion = SPINNAKER_API_V1,
        metadata = ResourceMetadata(
          name = ResourceName("SecurityGroup:ec2:test:us-west-2:fnord"),
          resourceVersion = 1234L,
          uid = randomUID()
        ),
        kind = "ec2:SecurityGroup",
        spec = randomData()
      )

      before {
        subject.store(resource)
      }

      test("it is returned by allResources") {
        subject.allResources(callback)

        verify(callback).invoke(ResourceHeader(resource.metadata.uid, resource.metadata.name, resource.metadata.resourceVersion, resource.apiVersion, resource.kind))
      }

      test("it can be retrieved by name") {
        val retrieved = subject.get<Map<String, Any>>(resource.metadata.name)
        expectThat(retrieved).isEqualTo(resource)
      }

      test("it can be retrieved by uid") {
        val retrieved = subject.get<Map<String, Any>>(resource.metadata.uid)
        expectThat(retrieved).isEqualTo(resource)
      }

      test("its state is unknown") {
        expectThat(subject.lastKnownState(resource.metadata.uid))
          .first
          .isEqualTo(Unknown)
      }

      context("storing another resource with a different name") {
        val anotherResource = Resource(
          metadata = ResourceMetadata(
            name = ResourceName("SecurityGroup:ec2:test:us-east-1:fnord"),
            resourceVersion = 1234L,
            uid = randomUID()
          ),
          apiVersion = SPINNAKER_API_V1,
          kind = "ec2:SecurityGroup",
          spec = randomData()
        )

        before {
          subject.store(anotherResource)
        }

        test("it does not overwrite the first resource") {
          subject.allResources(callback)

          argumentCaptor<ResourceHeader>().apply {
            verify(callback, times(2)).invoke(capture())
            expectThat(allValues)
              .hasSize(2)
              .map { it.uid }
              .containsExactlyInAnyOrder(resource.metadata.uid, anotherResource.metadata.uid)
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
          subject.updateState(resource.metadata.uid, Ok)
        }

        test("it reports the new state") {
          expectThat(subject.lastKnownState(resource.metadata.uid))
            .first
            .isEqualTo(Ok)
        }

        context("updating the state again") {
          before {
            clock.incrementBy(ONE_SECOND)
            subject.updateState(resource.metadata.uid, Diff)
          }

          test("it reports the newest state") {
            expectThat(subject.lastKnownState(resource.metadata.uid))
              .first
              .isEqualTo(Diff)
          }
        }

        context("after updating the resource") {
          before {
            clock.incrementBy(ONE_SECOND)
            subject.store(resource.copy(
              spec = randomData()
            ))
          }

          test("its state becomes unknown again") {
            expectThat(subject.lastKnownState(resource.metadata.uid))
              .first
              .isEqualTo(Unknown)
          }
        }
      }

      context("deleting the resource") {
        before {
          subject.delete(resource.metadata.name)
        }

        test("the resource is no longer returned when listing all resources") {
          subject.allResources(callback)

          verify(callback, never()).invoke(eq(ResourceHeader(resource)))
        }

        test("the resource can no longer be retrieved by name") {
          expectThrows<NoSuchResourceException> {
            subject.get<Map<String, Any>>(resource.metadata.name)
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

internal class MutableClock(
  private var instant: Instant = Instant.now(),
  private val zone: ZoneId = ZoneId.systemDefault()
) : Clock() {

  override fun withZone(zone: ZoneId): MutableClock {
    return MutableClock(instant, zone)
  }

  override fun getZone(): ZoneId {
    return zone
  }

  override fun instant(): Instant {
    return instant
  }

  fun incrementBy(amount: TemporalAmount) {
    instant = instant.plus(amount)
  }

  fun instant(newInstant: Instant) {
    instant = newInstant
  }
}
