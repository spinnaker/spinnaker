package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.time.Clock
import java.time.Duration
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.contains
import strikt.assertions.containsExactlyInAnyOrder
import strikt.assertions.doesNotContain
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isNotEmpty
import strikt.assertions.isNotEqualTo

abstract class PeriodicallyCheckedRepositoryTests<T : Any, S : PeriodicallyCheckedRepository<T>> : JUnit5Minutests {
  abstract val descriptor: String
  abstract val factory: (clock: Clock) -> S
  abstract val createAndStore: Fixture<T, S>.(count: Int) -> Collection<T>
  abstract val updateOne: Fixture<T, S>.() -> T
  open fun flush() {}

  data class Fixture<T : Any, S : PeriodicallyCheckedRepository<T>>(
    val factory: (Clock) -> S,
    val createAndStore: Fixture<T, S>.(Int) -> Collection<T>,
    val updateOne: Fixture<T, S>.() -> T,
    val ifNotCheckedInLast: Duration = Duration.ofMinutes(30),
    val limit: Int = 2
  ) {
    val clock = MutableClock()
    val subject: S = factory(clock)

    fun nextResults(): Collection<T> =
      subject.itemsDueForCheck(ifNotCheckedInLast, limit)
  }

  fun tests() = rootContext<Fixture<T, S>> {
    fixture {
      Fixture(factory, createAndStore, updateOne)
    }

    after { flush() }

    context("no ${descriptor}s exist") {
      test("returns an empty collection") {
        expectThat(nextResults()).isEmpty()
      }
    }

    context("multiple ${descriptor}s exist") {
      before {
        createAndStore(4)
      }

      test("next results returns at most 2 ${descriptor}s") {
        expectThat(nextResults()).hasSize(limit)
      }

      test("multiple calls return different results") {
        val results1 = nextResults()
        val results2 = nextResults()
        expect {
          that(results1)
            .hasSize(2)
            .isNotEqualTo(results2)
            .all {
              isNotIn(results2)
            }
          that(results2).hasSize(2)
        }
      }

      test("once all ${descriptor}s are exhausted no more results are returned") {
        val results1 = nextResults()
        val results2 = nextResults()
        val results3 = nextResults()
        expect {
          that(results1).isNotEmpty().hasSize(2)
          that(results2).isNotEmpty().hasSize(2)
          that(results3).isEmpty()
        }
      }

      test("once time passes the same results are returned again") {
        val results1 = nextResults()
        clock.incrementBy(ifNotCheckedInLast / 2)
        clock.incrementBy(Duration.ofSeconds(1))
        val results2 = nextResults()
        clock.incrementBy(ifNotCheckedInLast / 2)
        clock.incrementBy(Duration.ofSeconds(1))
        val results3 = nextResults()
        expect {
          that(results1).isNotEmpty()
          that(results2).isNotEmpty().doesNotContain(results1)
          that(results3).isNotEmpty().containsExactlyInAnyOrder(results1)
        }
      }

      test("after a $descriptor is updated is it returned") {
        nextResults()
        nextResults()

        val updated = updateOne()

        val results3 = nextResults()

        expect {
          that(results3).hasSize(1).contains(updated)
        }
      }
    }
  }
}
