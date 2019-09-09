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

import com.netflix.spinnaker.kork.test.KorkTestException
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expect
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isGreaterThan
import strikt.assertions.isNotEmpty
import strikt.assertions.isTrue

class DataContainerTest : JUnit5Minutests {

  fun tests() = rootContext<DataContainer> {
    fixture {
      DataContainer()
    }

    context("loading a new data source") {
      test("data source does not exist") {
        expectThrows<KorkTestException> {
          load("wut.yml")
        }
      }

      test("data source exists") {
        expectThat(load("mimicker-foo.yml")).and {
          get { get("foo/hello") }.isEqualTo("world")
        }
      }
    }

    context("get value of type") {
      test("incorrect type") {
        expectThrows<KorkTestException> {
          getOfType("words", String::class.java)
        }
      }

      test("correct type") {
        expectThat(getOfType("words", List::class.java)).isA<List<String>>()
      }
    }

    test("exists") {
      expect {
        that(exists("nope")).isFalse()
        that(exists("words")).isTrue()
      }
    }

    test("list") {
      expectThat(list<String>("words"))
        .isA<List<String>>()
        .isNotEmpty()
    }

    test("random") {
      val result = (1..10).toList().map { random("words") }.distinct()
      expectThat(result).get { size }.isGreaterThan(1)
    }
  }
}
