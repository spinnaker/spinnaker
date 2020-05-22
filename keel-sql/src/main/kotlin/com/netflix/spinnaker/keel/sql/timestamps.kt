package com.netflix.spinnaker.keel.sql

import java.time.Clock
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset.UTC

fun Instant.toTimestamp(): LocalDateTime = atZone(UTC).toLocalDateTime()
fun Clock.timestamp(): LocalDateTime = instant().toTimestamp()
