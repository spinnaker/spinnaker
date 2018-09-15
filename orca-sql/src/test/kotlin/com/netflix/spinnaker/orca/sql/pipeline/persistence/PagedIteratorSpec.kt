package com.netflix.spinnaker.orca.sql.pipeline.persistence

import com.nhaarman.mockito_kotlin.*
import org.assertj.core.api.AbstractAssert
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.*

internal object PagedIteratorSpec : Spek({

  describe("fetching paged data") {

    val pageSize = 3
    val nextPage = mock<(Int, String?) -> Iterable<String>>()

    given("there is no data") {
      val subject = PagedIterator(pageSize, String::toString, nextPage)

      beforeGroup {
        whenever(nextPage(eq(pageSize), anyOrNull())) doReturn emptyList<String>()
      }

      afterGroup { reset(nextPage) }

      it("has no elements") {
        assertThat(subject.hasNext()).isFalse()
      }

      it("won't return anything") {
        assertThatThrownBy { subject.next() }
          .isInstanceOf<NoSuchElementException>()
      }
    }

    given("there is less than one full page of data") {
      val subject = PagedIterator(pageSize, String::toString, nextPage)

      beforeGroup {
        whenever(nextPage(eq(pageSize), anyOrNull())) doAnswer {
          val (_, cursor) = it.arguments
          when (cursor) {
            null -> listOf("ONE", "TWO")
            else -> emptyList()
          }
        }
      }

      afterGroup { reset(nextPage) }

      it("has some elements") {
        assertThat(subject.hasNext()).isTrue()
      }

      on("draining the iterator") {
        val results = mutableListOf<String>()
        subject.forEachRemaining { results.add(it) }

        it("iterates over the available elements") {
          assertThat(results).containsExactly("ONE", "TWO")
        }

        it("does not try to fetch another page") {
          verify(nextPage).invoke(pageSize, null)
          verifyNoMoreInteractions(nextPage)
        }
      }
    }

    given("there is exactly one full page of data") {
      val subject = PagedIterator(pageSize, String::toString, nextPage)

      beforeGroup {
        whenever(nextPage(eq(pageSize), anyOrNull())) doAnswer {
          val (_, cursor) = it.arguments
          when (cursor) {
            null -> listOf("ONE", "TWO", "THREE")
            else -> emptyList()
          }
        }
      }

      afterGroup { reset(nextPage) }

      it("has some elements") {
        assertThat(subject.hasNext()).isTrue()
      }

      on("draining the iterator") {
        val results = mutableListOf<String>()
        subject.forEachRemaining { results.add(it) }

        it("iterates over the available elements") {
          assertThat(results).containsExactly("ONE", "TWO", "THREE")
        }

        it("tries to fetch the next page before stopping") {
          verify(nextPage).invoke(pageSize, null)
          verify(nextPage).invoke(pageSize, "THREE")
          verifyNoMoreInteractions(nextPage)
        }
      }
    }

    given("there are multiple pages of data") {
      val subject = PagedIterator(pageSize, String::toString, nextPage)

      beforeGroup {
        whenever(nextPage(eq(pageSize), anyOrNull())) doAnswer {
          val (_, cursor) = it.arguments
          when (cursor) {
            null    -> listOf("ONE", "TWO", "THREE")
            "THREE" -> listOf("FOUR", "FIVE", "SIX")
            "SIX"   -> listOf("SEVEN")
            else    -> emptyList()
          }
        }
      }

      afterGroup { reset(nextPage) }

      it("has some elements") {
        assertThat(subject.hasNext()).isTrue()
      }

      on("draining the iterator") {
        val results = mutableListOf<String>()
        subject.forEachRemaining { results.add(it) }

        it("iterates over the available elements") {
          assertThat(results)
            .containsExactly("ONE", "TWO", "THREE", "FOUR", "FIVE", "SIX", "SEVEN")
        }

        it("does not try to fetch additional pages") {
          verify(nextPage, times(3)).invoke(eq(pageSize), anyOrNull())
        }
      }
    }
  }

})

private inline fun <reified T> AbstractAssert<*, *>.isInstanceOf() =
  isInstanceOf(T::class.java)
