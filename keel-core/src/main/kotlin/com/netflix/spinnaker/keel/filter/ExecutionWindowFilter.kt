/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.filter

import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.attribute.ExecutionWindow
import com.netflix.spinnaker.keel.attribute.ExecutionWindowAttribute
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.stereotype.Component
import java.time.Clock
import java.util.*

private const val ACTION_PROCEED = "proceeding as if inside of execution window"
private const val ACTION_HALT = "assuming outside of execution window"

/**
 * Filters out Intents that are not in their defined execution windows (configured via the ExecutionWindowAttribute).
 */
@Component
@EnableConfigurationProperties(ExecutionWindowFilter.Configuration::class)
class ExecutionWindowFilter
@Autowired constructor(
  private val config: Configuration,
  private val clock: Clock
): Filter {

  private val DAY_START_HOUR = 0
  private val DAY_START_MIN = 0
  private val DAY_END_HOUR = 23
  private val DAY_END_MIN = 59

  private val log = LoggerFactory.getLogger(javaClass)

  override fun getOrder() = 50

  override fun filter(intent: Intent<IntentSpec>): Boolean {
    if (!intent.hasAttribute(ExecutionWindowAttribute::class)) {
      return true
    }

    val executionWindow = intent.getAttribute(ExecutionWindowAttribute::class)!!
    log.info("Calculating scheduled time for ${intent.id}; $executionWindow")

    val now = Date.from(clock.instant())
    val scheduledTime: Date
    try {
      scheduledTime = getTimeInWindow(executionWindow.value, now)
    } catch (e: Exception) {
      val action = if (config.allowExecutionOnFailure) ACTION_PROCEED else ACTION_HALT
      log.error("Exception occurred while calculating time window, $action: ${e.message}")
      return config.allowExecutionOnFailure
    }

    log.debug("Calculated schedule time for ${intent.id}: $scheduledTime")

    return now >= scheduledTime
  }

  @ConfigurationProperties
  open class Configuration {
    val allowExecutionOnFailure: Boolean = false
  }

  private fun getTimeInWindow(executionWindow: ExecutionWindow, scheduledTime: Date): Date {
    val timeWindows = executionWindow.timeWindows.map {
      TimeWindow(HourMinute(it.startHour, it.startMin), HourMinute(it.endHour, it.endMin))
    }
    return calculateScheduledTime(scheduledTime, timeWindows, executionWindow.days)
  }

  private fun calculateScheduledTime(scheduledTime: Date,
                                     whitelistWindows: List<TimeWindow>,
                                     whitelistDays: List<Int>)
    = calculateScheduledTime(scheduledTime, whitelistWindows, whitelistDays, false)

  private fun calculateScheduledTime(scheduledTime: Date,
                                     timeWindows: List<TimeWindow>,
                                     whitelistDays: List<Int>,
                                     dayIncremented: Boolean): Date {
    val whitelistWindows = normalizeTimeWindows(
      if (timeWindows.isEmpty() && whitelistDays.isNotEmpty())
        listOf(TimeWindow(HourMinute(0, 0), HourMinute(23, 59))) else timeWindows.sorted()
    )

    val calendar = Calendar.getInstance()
    calendar.timeZone = TimeZone.getTimeZone(clock.zone)
    calendar.time = scheduledTime

    var inWindow = false
    var todayIsValid = true

    if (whitelistDays.isNotEmpty()) {
      var daysIncremented = 0
      while (daysIncremented < 7) {
        var nextDayFound = false
        if (whitelistDays.contains(calendar.get(Calendar.DAY_OF_WEEK))) {
          nextDayFound = true
          todayIsValid = daysIncremented == 0
        }
        if (nextDayFound) {
          break
        }
        calendar.add(Calendar.DAY_OF_MONTH, 1)
        resetToTomorrow(calendar)
        daysIncremented++
      }
    }
    if (todayIsValid) {
      for (timeWindow in whitelistWindows) {
        val hourMin = HourMinute(calendar[Calendar.HOUR_OF_DAY], calendar[Calendar.MINUTE])
        val index = timeWindow.indexOf(hourMin)
        if (index == -1) {
          calendar[Calendar.HOUR_OF_DAY] = timeWindow.start.hour
          calendar[Calendar.MINUTE] = timeWindow.start.min
          calendar[Calendar.SECOND] = 0
          inWindow = true
          break
        } else if (index == 0) {
          inWindow = true
          break
        }
      }
    }

    if (!inWindow) {
      if (!dayIncremented) {
        resetToTomorrow(calendar)
        return calculateScheduledTime(scheduledTime, whitelistWindows, whitelistDays, true)
      } else {
        throw IncorrectTimeWindowsException("Couldn't calculate a suitable time within the given time windows")
      }
    }

    if (dayIncremented) {
      calendar.add(Calendar.DAY_OF_MONTH, 1)
    }

    return calendar.time
  }

  private fun normalizeTimeWindows(timeWindows: List<TimeWindow>): List<TimeWindow> {
    return timeWindows.flatMap {
      return@flatMap if (it.start.hour > it.end.hour) {
        listOf(
          TimeWindow(HourMinute(it.start.hour, it.start.min), HourMinute(DAY_END_HOUR, DAY_END_MIN)),
          TimeWindow(HourMinute(DAY_START_HOUR, DAY_START_MIN), HourMinute(it.end.hour, it.end.min))
        )
      } else {
        listOf(it)
      }
    }.sorted()
  }

  private fun resetToTomorrow(calendar: Calendar) {
    calendar[Calendar.HOUR_OF_DAY] = DAY_START_HOUR
    calendar[Calendar.MINUTE] = DAY_START_MIN
    calendar[Calendar.SECOND] = 0
  }
}

private class HourMinute(
  val hour: Int,
  val min: Int
) : Comparable<HourMinute> {

  fun before(that: HourMinute)
    = when {
        hour < that.hour -> true
        hour > that.hour -> false
        min < that.min -> true
        else -> false
      }

  fun after(that: HourMinute) = !before(that)

  override fun compareTo(other: HourMinute) = if (before(other)) -1 else if (after(other)) 1 else 0

  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (javaClass != other?.javaClass) return false

    other as HourMinute

    if (hour != other.hour) return false
    if (min != other.min) return false

    return true
  }

  override fun hashCode(): Int {
    var result = hour
    result = 31 * result + min
    return result
  }
}

private class TimeWindow(
  val start: HourMinute,
  val end: HourMinute
) : Comparable<TimeWindow> {

  override fun compareTo(other: TimeWindow): Int = start.compareTo(other.start)

  fun indexOf(current: HourMinute): Int {
    if (current.before(start)) {
      return -1
    } else if ((current.after(start) || current == start) && (current.before(end) || current == end)) {
      return 0
    }
    return 1
  }
}

private class IncorrectTimeWindowsException(message: String) : Exception(message)
