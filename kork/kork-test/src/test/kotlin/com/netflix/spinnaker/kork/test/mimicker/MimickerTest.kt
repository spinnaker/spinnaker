/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 */
package com.netflix.spinnaker.kork.test.mimicker

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isNotSameInstanceAs

class MimickerTest : JUnit5Minutests {

  fun tests() = rootContext<Mimicker> {
    fixture {
      Mimicker()
    }

    context("top-level producers are always new") {
      test("text") {
        expectThat(text()).isNotSameInstanceAs(text())
      }
      test("random") {
        expectThat(random()).isNotSameInstanceAs(random())
      }
      test("moniker") {
        expectThat(moniker()).isNotSameInstanceAs(moniker())
      }
      test("aws") {
        expectThat(aws()).isNotSameInstanceAs(aws())
      }
      test("network") {
        expectThat(network()).isNotSameInstanceAs(network())
      }
    }
  }
}
