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
import strikt.api.expectThat
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.isEmpty
import strikt.assertions.isFalse
import strikt.assertions.isTrue

abstract class PausedRepositoryTests<T : PausedRepository> : JUnit5Minutests {
  abstract fun factory(): T

  open fun T.flush() {}

  val application = "keeldemo"

  data class Fixture<T : PausedRepository>(
    val subject: T
  )

  fun tests() = rootContext<Fixture<T>> {
    fixture {
      Fixture(subject = factory())
    }

    after { subject.flush() }

    context("application not vetoed") {
      test("shows not paused") {
        expectThat(subject.applicationPaused(application)).isFalse()
      }

      test("no applications are paused") {
        expectThat(subject.getPausedApplications()).isEmpty()
      }
    }

    context("application paused") {
      before {
        subject.pauseApplication(application, "keel@keel.io")
      }

      test("app appears in list of paused apps") {
        expectThat(subject.getPausedApplications()).containsExactlyInAnyOrder(application)
      }

      test("paused reflects correctly") {
        expectThat(subject.applicationPaused(application)).isTrue()
      }

      test("resume works") {
        subject.resumeApplication(application)
        expectThat(subject.applicationPaused(application)).isFalse()
      }
    }
  }
}
