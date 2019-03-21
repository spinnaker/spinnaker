package com.netflix.spinnaker.keel.sync

import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue
import java.time.Clock
import java.time.Duration

abstract class LockTests<T : Lock> : JUnit5Minutests {

  /**
   * Create and return an instance of the lock implementation.
   */
  protected abstract fun subject(clock: Clock): T

  /**
   * Override if you need to do something to flush data from the backing store used by the lock
   * implementation.
   */
  protected open fun flush() {}

  /**
   * Override if you need to do something to simulate time passing. Not all implementations will
   * need this.
   */
  protected open fun simulateTimePassing(name: String, duration: Duration) {}

  private val clock = MutableClock()
  private val duration = Duration.ofSeconds(30)

  fun tests() = rootContext<Lock> {
    fixture { subject(clock) }

    after {
      flush()
    }

    context("no pre-existing lock") {
      test("can acquire the lock") {
        expectThat(tryAcquire("lock1", duration)).isTrue()
      }
    }

    context("a pre-existing lock that has expired") {
      before {
        tryAcquire("lock1", duration)
        with(duration * 2) {
          clock.incrementBy(this)
          simulateTimePassing("lock1", this)
        }
      }

      test("can acquire the lock") {
        expectThat(tryAcquire("lock1", duration)).isTrue()
      }
    }

    context("a pre-existing lock that has not expired") {
      before {
        tryAcquire("lock1", duration)
        with(duration / 2) {
          clock.incrementBy(this)
          simulateTimePassing("lock1", this)
        }
      }

      test("cannot acquire the lock") {
        expectThat(tryAcquire("lock1", duration)).isFalse()
      }

      test("can acquire a different lock") {
        expectThat(tryAcquire("lock2", duration)).isTrue()
      }
    }
  }
}

private operator fun Duration.div(divisor: Long) = dividedBy(divisor)
private operator fun Duration.times(multiplier: Long) = multipliedBy(multiplier)
