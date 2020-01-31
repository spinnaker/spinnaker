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

import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.time.Clock
import strikt.api.expectThat
import strikt.assertions.isEqualTo

abstract class DiffFingerprintRepositoryTests<T : DiffFingerprintRepository> : JUnit5Minutests {
  abstract fun factory(clock: Clock): T

  open fun T.flush() {}

  val clock = MutableClock()
  val r = resource()

  data class Fixture<T : DiffFingerprintRepository>(
    val subject: T
  )

  fun tests() = rootContext<Fixture<T>> {
    fixture {
      Fixture(subject = factory(clock))
    }
    after { subject.flush() }

    context("storing hash") {
      test("succeeds when new") {
        val diff = DefaultResourceDiff(mapOf("spec" to "hi"), mapOf("spec" to "bye"))
        subject.store(r.id, diff)
        expectThat(subject.diffCount(r.id)).isEqualTo(1)
      }

      test("updates count") {
        val diff = DefaultResourceDiff(mapOf("spec" to "hi"), mapOf("spec" to "bye"))
        subject.store(r.id, diff)
        subject.store(r.id, diff)
        subject.store(r.id, diff)
        subject.store(r.id, diff)
        subject.store(r.id, diff)
        expectThat(subject.diffCount(r.id)).isEqualTo(5)
      }

      test("resets count when different hash") {
        val diff = DefaultResourceDiff(mapOf("spec" to "hi"), mapOf("spec" to "bye"))
        subject.store(r.id, diff)
        subject.store(r.id, diff)
        expectThat(subject.diffCount(r.id)).isEqualTo(2)
        val diff2 = DefaultResourceDiff(mapOf("spec" to "hi"), mapOf("spec" to "byeBYEbyeee"))
        subject.store(r.id, diff2)
        expectThat(subject.diffCount(r.id)).isEqualTo(1)
      }
    }

    context("querying when nothing exists") {
      test("returns 0") {
        expectThat(subject.diffCount(r.id)).isEqualTo(0)
      }
    }
  }
}
