package com.netflix.spinnaker.keel.telemetry

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Tag
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import java.time.Clock
import java.time.Duration
import java.time.Instant

private val spectatorLogger = LoggerFactory.getLogger("com.netflix.keel.spinnaker.telemetry.spectator")

fun Counter.safeIncrement() =
  try {
    increment()
  } catch (ex: Exception) {
    spectatorLogger.error("Exception incrementing {} counter: {}", id.name, ex.message)
  }

fun MeterRegistry.recordDurationPercentile(metricName: String, clock: Clock, startTime: Instant, tags: Set<Tag> = emptySet()) =
  Timer
    .builder(metricName)
    .tags(tags)
    .publishPercentileHistogram()
    .register(this)
    .record(Duration.between(startTime, clock.instant()))

fun MeterRegistry.recordDuration(metricName: String, clock: Clock, startTime: Instant, tags: Set<Tag> = emptySet()) =
  timer(metricName, tags).record(Duration.between(startTime, clock.instant()))
