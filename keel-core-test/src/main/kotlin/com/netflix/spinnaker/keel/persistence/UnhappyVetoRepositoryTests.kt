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
package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import java.time.Clock
import java.time.Duration

abstract class UnhappyVetoRepositoryTests<T : UnhappyVetoRepository> : JUnit5Minutests {
  abstract fun factory(clock: Clock): T

  open fun store(resource: Resource<*>) {}
  open fun flush() {}

  val clock = MutableClock()
  val resource = resource()
  val application = "keeldemo"
  val waitDuration = Duration.ofMinutes(10)

  data class Fixture<T : UnhappyVetoRepository>(
    val subject: T
  )

  fun tests() = rootContext<Fixture<T>> {
    fixture {
      Fixture(subject = factory(clock))
    }

    before { store(resource) }
    after { flush() }

    context("nothing currently vetoed") {
      test("no applications returned") {
        expectThat(subject.getAll()).hasSize(0)
      }

      test("recheck time is null") {
        expectThat(subject.getRecheckTime(resource)).isNull()
      }
    }

    context("basic operations") {
      before {
        subject.markUnhappy(resource)
      }

      test("marking unhappy works") {
        expectThat(subject.getAll()).hasSize(1)
      }

      test("retrieving recheck time works") {
        expectThat(subject.getRecheckTime(resource)).isEqualTo(clock.instant() + waitDuration)
      }

      test("marking happy works") {
        subject.delete(resource)
        expectThat(subject.getAll()).hasSize(0)
      }
    }

    context("filtering") {
      before {
        subject.markUnhappy(resource)
      }
      test("filters out resources that are past the recheck time") {
        clock.incrementBy(Duration.ofMinutes(11))
        expectThat(subject.getAll()).hasSize(0)
      }
    }

    context("getting all by app name") {
      val resources = listOf(
        resource(application = "keeldemo"),
        resource(application = "keel"),
        resource(application = "keeldemo"),
        resource(application = "keel")
      )

      before {
        resources.forEach {
          store(it)
          subject.markUnhappy(it)
        }
      }

      test("get for keel returns only correct resources") {
        val unhappyIds = subject.getAllForApp("keel")
        expectThat(unhappyIds)
          .hasSize(2)
          .containsExactlyInAnyOrder(resources.filter { it.application == "keel" }.map { it.id })
      }
    }
  }
}
