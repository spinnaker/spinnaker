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
package com.netflix.spinnaker.kork.sql

import com.nhaarman.mockito_kotlin.anyOrNull
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyNoMoreInteractions
import com.nhaarman.mockito_kotlin.whenever
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature

internal object PagedIteratorSpec : Spek({

  Feature("fetching paged data") {
    val pageSize = 3
    val nextPage = mock<(Int, String?) -> Iterable<String>>()

    Scenario("there is no data") {
      val subject = PagedIterator(pageSize, String::toString, nextPage)

      beforeScenario {
        whenever(nextPage(eq(pageSize), anyOrNull())) doReturn emptyList<String>()
      }

      afterScenario { reset(nextPage) }

      Then("has no elements") {
        assertThat(subject.hasNext()).isFalse()
      }

      Then("won't return anything") {
        assertThatThrownBy { subject.next() }
          .isInstanceOf<NoSuchElementException>()
      }
    }

    Scenario("there is less than one full page of data") {
      val subject = PagedIterator(pageSize, String::toString, nextPage)

      beforeScenario {
        whenever(nextPage(eq(pageSize), anyOrNull())) doAnswer {
          val (_, cursor) = it.arguments
          when (cursor) {
            null -> listOf("ONE", "TWO")
            else -> emptyList()
          }
        }
      }

      afterScenario { reset(nextPage) }

      Given("has some elements") {
        assertThat(subject.hasNext()).isTrue()
      }

      val results = mutableListOf<String>()
      When("draining the iterator") {
        subject.forEachRemaining { results.add(it) }
      }

      Then("iterates over the available elements") {
        assertThat(results).containsExactly("ONE", "TWO")
      }

      Then("does not try to fetch another page") {
        verify(nextPage).invoke(pageSize, null)
        verifyNoMoreInteractions(nextPage)
      }
    }

    Scenario("there is exactly one full page of data") {
      val subject = PagedIterator(pageSize, String::toString, nextPage)

      beforeScenario {
        whenever(nextPage(eq(pageSize), anyOrNull())) doAnswer {
          val (_, cursor) = it.arguments
          when (cursor) {
            null -> listOf("ONE", "TWO", "THREE")
            else -> emptyList()
          }
        }
      }

      afterScenario { reset(nextPage) }

      Given("has some elements") {
        assertThat(subject.hasNext()).isTrue()
      }

      val results = mutableListOf<String>()
      When("draining the iterator") {
        subject.forEachRemaining { results.add(it) }
      }

      Then("iterates over the available elements") {
        assertThat(results).containsExactly("ONE", "TWO", "THREE")
      }

      Then("tries to fetch the next page before stopping") {
        verify(nextPage).invoke(pageSize, null)
        verify(nextPage).invoke(pageSize, "THREE")
        verifyNoMoreInteractions(nextPage)
      }
    }

    Scenario("there are multiple pages of data") {
      val subject = PagedIterator(pageSize, String::toString, nextPage)

      beforeScenario {
        whenever(nextPage(eq(pageSize), anyOrNull())) doAnswer {
          val (_, cursor) = it.arguments
          when (cursor) {
            null -> listOf("ONE", "TWO", "THREE")
            "THREE" -> listOf("FOUR", "FIVE", "SIX")
            "SIX" -> listOf("SEVEN")
            else -> emptyList()
          }
        }
      }

      afterScenario { reset(nextPage) }

      Given("has some elements") {
        assertThat(subject.hasNext()).isTrue()
      }

      val results = mutableListOf<String>()
      When("draining the iterator") {
        subject.forEachRemaining { results.add(it) }
      }

      Then("iterates over the available elements") {
        assertThat(results)
          .containsExactly("ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN")
      }

      Then("does not try to fetch additional pages") {
        verify(nextPage, times(3)).invoke(eq(pageSize), anyOrNull())
      }
    }
  }
})

private inline fun <reified T> AbstractAssert<*, *>.isInstanceOf() =
  isInstanceOf(T::class.java)
