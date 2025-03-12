package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.time.Clock
import java.time.Duration
import strikt.api.expect
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isTrue

abstract class AgentLockRepositoryTests<T : AgentLockRepository> : JUnit5Minutests {

  abstract fun factory(clock: Clock): T
  open fun T.flush() {}

  val clock = MutableClock()

  data class Fixture<T : AgentLockRepository>(
    val repository: T
  )

  fun tests() = rootContext<Fixture<T>> {
    fixture {
      Fixture(repository = factory(clock))
    }

    after { repository.flush() }

    context("basic agent locking creation") {

      test("try to acquire lock with single agent") {
        val locked = repository.tryAcquireLock("someAgent", 1)
        expect {
          that(locked).isTrue()
          that(repository.getLockedAgents().size).isEqualTo(1)
        }
      }
    }

    context("advanced agent locking tests") {
      before {
        repository.tryAcquireLock("someAgent", 11)
      }

      test("try to acquire the same lock before expiration time ended") {
        val locked = repository.tryAcquireLock("someAgent", 1)
        expect {
          that(locked).isFalse()
          that(repository.getLockedAgents().size).isEqualTo(1)
        }
      }

      test("move the clock forward and try to acquire the same lock again") {
        clock.incrementBy(Duration.ofMinutes(1))
        val locked = repository.tryAcquireLock("someAgent", 10)
        expect {
          that(locked).isTrue()
          that(repository.getLockedAgents().size).isEqualTo(1)
        }
      }

      test("creating another lock") {
        val locked = repository.tryAcquireLock("someAgent2", 10)
        expect {
          that(locked).isTrue()
          that(repository.getLockedAgents().size).isEqualTo(2)
        }
      }
    }
  }
}
