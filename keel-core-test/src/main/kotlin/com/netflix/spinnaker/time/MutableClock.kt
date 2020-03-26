package com.netflix.spinnaker.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAmount

class MutableClock(
  private var instant: Instant = Instant.now(),
  private val zone: ZoneId = ZoneId.of("UTC"),
  val start: Instant = instant
) : Clock() {

  override fun withZone(zone: ZoneId): MutableClock {
    return MutableClock(instant, zone)
  }

  override fun getZone(): ZoneId {
    return zone
  }

  override fun instant(): Instant {
    return instant
  }

  fun incrementBy(amount: TemporalAmount) {
    instant = instant.plus(amount)
  }

  fun tickSeconds(seconds: Long) = incrementBy(Duration.ofSeconds(seconds)).let { instant }

  fun tickMinutes(minutes: Long) = incrementBy(Duration.ofMinutes(minutes)).let { instant }

  fun tickHours(hours: Long) = incrementBy(Duration.ofHours(hours)).let { instant }

  fun instant(newInstant: Instant) {
    instant = newInstant
  }

  fun reset() {
    instant = start
  }
}
