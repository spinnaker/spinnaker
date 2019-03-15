package com.netflix.spinnaker.time

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.TemporalAmount

class MutableClock(
  private var instant: Instant = Instant.now(),
  private val zone: ZoneId = ZoneId.systemDefault()
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

  fun instant(newInstant: Instant) {
    instant = newInstant
  }
}
