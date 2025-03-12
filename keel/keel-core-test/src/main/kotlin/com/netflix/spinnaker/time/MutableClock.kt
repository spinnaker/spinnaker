package com.netflix.spinnaker.time

import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit.MILLIS
import java.time.temporal.TemporalAmount

class MutableClock(

  /**
   * The [instant] field in this class has two invariants:
   *
   * 1. Cannot have greater than millisecond precision. i.e., this must always be true:
   *
   *          instant == instant.truncatedTo(MILLIS)
   *
   *    This is because the database uses millisecond precision timestamps (`timestamp(3)`), and so any tests
   *    that compare timestamps that came from the database will always only have millisecond precision. To make
   *    time-related test assertions simpler, we just ensure that all times are millisecond precision.
   *
   * 2. Cannot have a nanoseconds component of exactly 0. i.e., this must always be true:
   *
   *         instant.nano != 0
   *
   *    This is because of an interaction of how Instant serializes to a string and how we use the `str_to_date` mysql
   *    function for the `timestamp` generated column in the `event` table.
   *
   *    If the nanoseconds component is zero, then the Jackson serializing of the Instant object will not have the
   *    fractional second component.
   *
   *    Consider following two serialization: the first has fraction seconds, the second doesn't.
   *
   *    "2021-04-03T19:06:08.123Z"  <-- with fractional second (nano is non-zero)
   *    "2021-04-03T19:06:08Z"      <-- without fractional second (nano is zero)
   *
   *    The `event` table has a generated column that looks like this:
   *
   *    timestamp datetime(3) generated always as (str_to_date(json->>'$.timestamp', '%Y-%m-%dT%T.%fZ'));
   *
   *    The str_to_date function will fail if there's no fractional component, resulting in error that looks like this:
   *
   *       Incorrect datetime value: '2021-04-03T19:06:08Z' for function str_to_date
   *
   *    This is potentially a problem for production as well, but it's much less likely to happen because we don't
   *    truncate to millisecond in that case.
   */
  private var instant: Instant = systemUTC().instant().truncatedTo(MILLIS).let {
    when(it.nano) {
      0 -> it.plusMillis(1)
      else -> it
    }
  },

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

  fun tickDays(days: Long) = incrementBy(Duration.ofDays(days)).let { instant }

  fun tickHours(hours: Long) = incrementBy(Duration.ofHours(hours)).let { instant }

  fun instant(newInstant: Instant) {
    instant = newInstant
  }

  fun reset() {
    instant = start
  }
}
