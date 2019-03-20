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
  protected abstract fun subject(clock: Clock): T
  protected open fun flush() {}
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
        clock.incrementBy(duration * 2)
      }

      test("can acquire the lock") {
        expectThat(tryAcquire("lock1", duration)).isTrue()
      }
    }

    context("a pre-existing lock that has not expired") {
      before {
        tryAcquire("lock1", duration)
        clock.incrementBy(duration / 2)
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
