package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.histogram.PercentileTimer
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

private val spectatorLogger = LoggerFactory.getLogger("com.netflix.keel.spinnaker.telemetry.spectator")

fun Counter.safeIncrement() =
  try {
    increment()
  } catch (ex: Exception) {
    spectatorLogger.error("Exception incrementing {} counter: {}", id().name(), ex.message)
  }

fun Registry.recordDurationPercentile(metricName: String, clock:Clock, startTime: Instant, tags: Set<BasicTag> = emptySet()) =
  PercentileTimer
    .builder(this)
    .withName(metricName)
    .withTags(tags)
    .build()
    .record(Duration.between(startTime, clock.instant()))

fun Registry.recordDuration(metricName: String, clock:Clock, startTime: Instant, tags: Set<BasicTag> = emptySet()) =
  timer(metricName, tags).record(Duration.between(startTime, clock.instant()))
