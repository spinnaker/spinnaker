package com.netflix.spinnaker.keel

import com.netflix.spinnaker.keel.notifications.friendlyDuration
import com.netflix.spinnaker.keel.notifications.friendlyTime
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import java.time.Duration

class FriendlyTimeTests : JUnit5Minutests {

  fun tests() = rootContext<Unit> {
    context("friendly duration") {
      test("day"){
        val duration = Duration.ofDays(1)
        expectThat(friendlyDuration(duration)).isEqualTo("about 1 day")
      }
      test("days"){
        val duration = Duration.ofDays(2)
        expectThat(friendlyDuration(duration)).isEqualTo("about 2 days")
      }
      test("hour"){
        val duration = Duration.ofHours(1)
        expectThat(friendlyDuration(duration)).isEqualTo("about 1 hour")
      }
      test("hours"){
        val duration = Duration.ofHours(2)
        expectThat(friendlyDuration(duration)).isEqualTo("about 2 hours")
      }
      test("minute"){
        val duration = Duration.ofMinutes(1)
        expectThat(friendlyDuration(duration)).isEqualTo("about 1 minute")
      }
      test("minutes"){
        val duration = Duration.ofMinutes(2)
        expectThat(friendlyDuration(duration)).isEqualTo("about 2 minutes")
      }
      test("seconds"){
        val duration = Duration.ofSeconds(2)
        expectThat(friendlyDuration(duration)).isEqualTo("less than a minute")
      }

      test("more than one day") {
        val duration = Duration.ofDays(1).plusHours(1)
        expectThat(friendlyDuration(duration)).isEqualTo("about 1 day")
      }
      test("more than one hour") {
        val duration = Duration.ofHours(3).plusMinutes(1)
        expectThat(friendlyDuration(duration)).isEqualTo("about 3 hours")
      }
    }

    context("friendly time") {
      test("minutes"){
        val duration = "PT1M"
        expectThat(friendlyTime(duration)).isEqualTo("1 minutes")
      }
      test("hours"){
        val duration = "PT1H"
        expectThat(friendlyTime(duration)).isEqualTo("1 hours")
      }
    }
  }
}