/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.orca.sql.pipeline.persistence

import com.nhaarman.mockito_kotlin.mock
import com.oneeyedmen.minutest.junit.JupiterTests
import com.oneeyedmen.minutest.junit.context
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions
import org.assertj.core.api.Assertions.assertThat

class PagedIteratorTest : JupiterTests {

  override val tests = context<PagedIterator<String, String>> {

    val pageSize = 3
    val nextPage = mock<(Int, String?) -> Iterable<String>>()

    context("fetching paged data") {

      context("there is no data") {
        fixture {
          PagedIterator(pageSize, String::toString) { _, _ -> emptyList() }
        }

        test("won't return anything") {
          Assertions.assertThat(it.hasNext()).isFalse()
          Assertions.assertThatThrownBy { it.next() }
            .isInstanceOf<NoSuchElementException>()
        }
      }

      context("there is less than one full page of data") {
        fixture {
          PagedIterator(pageSize, String::toString) { _, cursor ->
            when (cursor) {
              null -> listOf("ONE", "TWO")
              else -> emptyList()
            }
          }
        }

        test("iterates over available elements") {
          assertThat(it.hasNext()).isTrue()

          val results = mutableListOf<String>()
          it.forEachRemaining { results.add(it) }

          assertThat(results).containsExactly("ONE", "TWO")
        }
      }

      context("there is exactly one full page of data") {
        fixture {
          PagedIterator(pageSize, String::toString) { _, cursor ->
            when (cursor) {
              null -> listOf("ONE", "TWO", "THREE")
              else -> emptyList()
            }
          }
        }

        test("iterates over available elements") {
          assertThat(it.hasNext()).isTrue()

          val results = mutableListOf<String>()
          it.forEachRemaining { results.add(it) }

          assertThat(results).containsExactly("ONE", "TWO", "THREE")
        }
      }

      context("there are multiple pages of data") {
        fixture {
          PagedIterator(pageSize, String::toString) { _, cursor ->
            when (cursor) {
              null    -> listOf("ONE", "TWO", "THREE")
              "THREE" -> listOf("FOUR", "FIVE", "SIX")
              "SIX"   -> listOf("SEVEN")
              else    -> emptyList()
            }
          }
        }

        test("iterates over the available elements") {
          assertThat(it.hasNext()).isTrue()

          val results = mutableListOf<String>()
          it.forEachRemaining { results.add(it) }

          assertThat(results).containsExactly("ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN")
        }
      }
    }
  }
}

private inline fun <reified T> AbstractAssert<*, *>.isInstanceOf() =
  isInstanceOf(T::class.java)
