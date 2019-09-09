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
package com.netflix.spinnaker.kork.test.mimicker.producers

import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.contains
import strikt.assertions.isGreaterThan
import java.security.SecureRandom

class RandomProducerTest : JUnit5Minutests {

  fun tests() = rootContext<RandomProducer> {
    fixture {
      RandomProducer(SecureRandom())
    }

    test("true or false") {
      val results = (1..10).toList().map { trueOrFalse() }
      expectThat(results).contains(true, false)
    }

    test("element") {
      val input = listOf("1", "2", "3", "4", "5")
      val results = (1..10).toList().map { element(input) }.distinct()
      expectThat(results).get { size }.isGreaterThan(1)
    }
  }
}
