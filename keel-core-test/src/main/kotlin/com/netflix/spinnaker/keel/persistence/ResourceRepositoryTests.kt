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

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.persistence.ResourceState.Diff
import com.netflix.spinnaker.keel.persistence.ResourceState.Ok
import com.netflix.spinnaker.keel.persistence.ResourceState.Unknown
import com.nhaarman.mockito_kotlin.argumentCaptor
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import de.huxhorn.sulky.ulid.ULID
import dev.minutest.RootContextBuilder
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

  private val ulid = ULID()

  open fun flush() {}

  data class Fixture<T : ResourceRepository>(
    val subject: T,
    val callback: (Triple<ResourceName, ApiVersion, String>) -> Unit
  )

  fun tests(): RootContextBuilder<Fixture<T>> = rootContext {

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
          uid = ulid.nextValue()
        ),
        kind = "ec2:SecurityGroup",
        spec = randomData()
      )

      before {
        subject.store(resource)
      }

      test("it is returned by allResources") {
        subject.allResources(callback)

        verify(callback).invoke(Triple(resource.metadata.name, resource.apiVersion, resource.kind))
      }

      test("it can be retrieved by id") {
        val retrieved = subject.get<Map<String, Any>>(resource.metadata.name)
        expectThat(retrieved).isEqualTo(resource)
      }

      test("its id can be retrieved by name") {

      }

      test("its state is unknown") {
        expectThat(subject.lastKnownState(resource.metadata.name))
          .first
          .isEqualTo(Unknown)
      }

      context("storing another resource with a different id") {
        val anotherResource = Resource(
          metadata = ResourceMetadata(
            name = ResourceName("SecurityGroup:ec2:test:us-east-1:fnord"),
            resourceVersion = 1234L,
            uid = ulid.nextValue()
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

          argumentCaptor<Triple<ResourceName, ApiVersion, String>>().apply {
            verify(callback, times(2)).invoke(capture())
            expectThat(allValues)
              .hasSize(2)
              .map { it.first }
              .containsExactlyInAnyOrder(resource.metadata.name, anotherResource.metadata.name)
          }
        }
      }


      context("storing a new version of the resource") {
        val updatedResource = resource.copy(
          spec = randomData()
        )

        before {
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
          clock.incrementBy(Duration.ofSeconds(1))
          subject.updateState(resource.metadata.name, Ok)
        }

        test("it reports the new state") {
          expectThat(subject.lastKnownState(resource.metadata.name))
            .first
            .isEqualTo(Ok)
        }

        context("updating the state again") {
          before {
            clock.incrementBy(Duration.ofSeconds(1))
            subject.updateState(resource.metadata.name, Diff)
          }

          test("it reports the newest state") {
            expectThat(subject.lastKnownState(resource.metadata.name))
              .first
              .isEqualTo(Diff)
          }
        }

        context("after updating the resource") {
          before {
            clock.incrementBy(Duration.ofSeconds(1))
            subject.store(resource.copy(
              spec = randomData()
            ))
          }

          test("its state becomes unknown again") {
            expectThat(subject.lastKnownState(resource.metadata.name))
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

          verifyZeroInteractions(callback)
        }

        test("the resource can no longer be retrieved by id") {
          expectThrows<NoSuchResourceException> {
            subject.get<Map<String, Any>>(resource.metadata.name)
          }
        }
      }
    }
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
