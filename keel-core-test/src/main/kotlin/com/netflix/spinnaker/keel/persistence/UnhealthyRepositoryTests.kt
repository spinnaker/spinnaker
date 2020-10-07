package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expect
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess
import java.time.Clock
import java.time.Duration

abstract class UnhealthyRepositoryTests<T : UnhealthyRepository> : JUnit5Minutests {

  abstract fun factory(clock: Clock): T

  open fun T.flush() {}

  val clock = MutableClock()
  val resourceId = "ec2:securityGroup:test:us-west-2:keeldemo-managed"
  data class Fixture<T : UnhealthyRepository>(
    val subject: T
  )
  fun tests() = rootContext<Fixture<T>> {
    fixture {
      Fixture(subject = factory(clock))
    }

    after { subject.flush() }

    context("nothing is unhealthy") {
      test("marking healthy works") {
        expectCatching {subject.markUnhealthy(resourceId) }.isSuccess()
      }
      test("marking unhealthy works") {
        expectCatching { subject.markHealthy(resourceId) }.isSuccess()
      }
      test("getting duration works") {
        expectThat(subject.durationUnhealthy(resourceId)).isEqualTo(Duration.ZERO)
      }
    }

    context("marked unhealthy 10 minutes ago") {
      before {
        subject.markUnhealthy(resourceId)
        clock.incrementBy(Duration.ofMinutes(10))
      }

      test("duration is correct") {
        expect {
          that(subject.durationUnhealthy(resourceId)).isEqualTo(Duration.ofMinutes(10))
        }

      }
    }
  }
}
