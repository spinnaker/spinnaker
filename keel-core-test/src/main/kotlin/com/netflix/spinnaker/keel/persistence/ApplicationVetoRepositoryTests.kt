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

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isFalse
import strikt.assertions.isTrue

abstract class ApplicationVetoRepositoryTests<T : ApplicationVetoRepository> : JUnit5Minutests {

  abstract fun factory(): T

  open fun flush() {}

  data class Fixture<T : ApplicationVetoRepository>(
    val subject: T,
    val callback: (ResourceHeader) -> Unit = mockk(relaxed = true)
  )

  fun tests() = rootContext<Fixture<T>> {
    fixture {
      Fixture(subject = factory())
    }

    after { flush() }

    context("nothing vetoed") {
      test("no applications are returned") {
        expectThat(subject.getAll()).hasSize(0)
      }

      test("application myapp is opted in") {
        expectThat(subject.appVetoed("myapp")).isFalse()
      }
    }

    context("app vetoed") {
      before {
        subject.optOut("myapp")
      }

      test("myapp returned") {
        expectThat(subject.getAll()).hasSize(1)
      }

      test("application myapp is opted out") {
        expectThat(subject.appVetoed("myapp")).isTrue()
      }
    }

    context("app un-vetoed") {
      before {
        subject.optOut("myapp")
        subject.optIn("myapp")
      }

      test("myapp not returned") {
        expectThat(subject.getAll()).hasSize(0)
      }

      test("application myapp is opted in") {
        expectThat(subject.appVetoed("myapp")).isFalse()
      }
    }
  }
}
