package com.netflix.spinnaker.keel.notifications

import java.time.Duration

fun friendlyDuration(duration: Duration): String {
  val parts = mutableListOf("about")
  when {
    duration.toDays() == 1L -> parts.add("${duration.toDays()} day")
    duration.toDays() > 0 -> parts.add("${duration.toDays()} days")
    duration.toHours() == 1L -> parts.add("${duration.toHours()} hour")
    duration.toHours() > 0 -> parts.add("${duration.toHours()} hours")
    duration.toMinutes() == 1L -> parts.add("${duration.toMinutes()} minute")
    duration.toMinutes() > 0 -> parts.add("${duration.toMinutes()} minutes")
    else -> {
      parts.clear()
      parts.add("less than a minute")
    }
  }
  return parts.joinToString(" ")
}

fun friendlyTime(duration: String): String {
  val time = duration.removePrefix("PT")
  return when {
    time.endsWith("M") -> time.removeSuffix("M") + " minutes"
    time.endsWith("H") -> time.removeSuffix("H") + " hours"
    else -> time
  }
}