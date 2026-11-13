package com.netflix.spinnaker.keel.telemetry

import com.netflix.spectator.api.BasicTag
import com.netflix.spectator.api.Counter
import com.netflix.spectator.api.Registry
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

fun Registry.recordDuration(metricName: String, clock:Clock, startTime: Instant, tags: Set<BasicTag> = emptySet()) =
  timer(metricName, tags).record(Duration.between(startTime, clock.instant()))
