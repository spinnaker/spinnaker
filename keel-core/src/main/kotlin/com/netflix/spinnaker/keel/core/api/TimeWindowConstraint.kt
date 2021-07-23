package com.netflix.spinnaker.keel.core.api

import com.netflix.spinnaker.keel.api.StatefulConstraint
import com.netflix.spinnaker.keel.exceptions.InvalidConstraintException
import java.text.ParsePosition
import java.time.DateTimeException
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.zone.ZoneRulesException
import java.util.Locale

const val ALLOWED_TIMES_CONSTRAINT_TYPE = "allowed-times"

/**
 * A constraint that requires the current time to fall within an allowed window
 */
data class TimeWindowConstraint(
  val windows: List<TimeWindow>,
  val tz: String? = null,
  val maxDeploysPerWindow: Int? = null
) : StatefulConstraint(ALLOWED_TIMES_CONSTRAINT_TYPE) {
  init {
    if (tz != null) {
      val zoneId: ZoneId? = try {
        ZoneId.of(tz)
      } catch (e: Exception) {
        when (e) {
          is DateTimeException, is ZoneRulesException -> null
          else -> throw e
        }
      }

      require(zoneId != null) {
        "tz must be a valid java parseable ZoneId"
      }
    }
  }
}

val TimeWindowConstraint.windowsNumeric: List<TimeWindowNumeric>
  get() = windows.toNumeric()

/**
 * Numeric day and hour representation of a time window.
 * This format is consistent regardless of how the window is defined in a string.
 */
data class TimeWindowNumeric(
  val days: Set<Int>,
  val hours: Set<Int>,
)

/**
 * The start hour of a time window.
 */
val TimeWindowNumeric.startHour
  get() = checkNotNull(hours.minOrNull())

/**
 * @param time must be a date/time that is inside the window or result may not be valid.
 * @return the most recent time the window started.
 */
fun TimeWindowNumeric.lastWindowStartBefore(time: ZonedDateTime): ZonedDateTime =
  time.withHour(startHour).truncatedTo(ChronoUnit.HOURS)


data class TimeWindow(
  val days: String? = null,
  val hours: String? = null
) {
  init {
    if (hours != null) {
      require(validateHours(hours)) {
        "allowed-times hours must only contains hours 0-23 that are comma separated " +
          "and/or containing dashes to denote a range"
      }
    }

    if (days != null) {
      require(validateDays(days)) {
        "allowed-times days must only contain any of " +
          "${dayAliases + daysOfWeek} " +
          "that are comma separated and/or containing dashes to denote a range"
      }
    }
  }
}

private val whiteSpace = """\s""".toRegex()
private val intOnly = """^\d+$""".toRegex()
private val intRange = """^\d+\-\d+$""".toRegex()
private val separators = """[\s,\-]""".toRegex()
private val fullDayFormatter = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
private val shortDayFormatter = DateTimeFormatter.ofPattern("EEE", Locale.getDefault())

private val daysOfWeek = DayOfWeek.values()
  .map {
    listOf(
      it.toString().toLowerCase(),
      it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).toLowerCase()
    )
  }
  .flatten()
  .toMutableSet()

private val dayAliases = setOf("weekdays", "weekends")

private fun validateHours(hours: String): Boolean {
  return hours.split(separators).all {
    it.matches(intOnly) && it.toInt() >= 0 && it.toInt() <= 23
  }
}

private fun validateDays(days: String): Boolean {
  return days.toLowerCase().split(separators).all {
    daysOfWeek.contains(it) || dayAliases.contains(it)
  }
}

private fun parseHours(hourConfig: String?): Set<Int> {
  if (hourConfig == null) {
    return emptySet()
  }

  val hours = mutableSetOf<Int>()
  val trimmed = hourConfig.replace(whiteSpace, "")
  val elements = trimmed.split(",")

  elements.forEach {
    when {
      it.isInt() -> hours.add(it.toInt())
      it.isIntRange() -> hours.addAll(it.hourRange())
    }
  }

  return hours
}

private fun String.isInt(): Boolean = this.matches(intOnly)

private fun String.isIntRange(): Boolean = this.matches(intRange)

private fun String.hourRange(): Set<Int> {
  val hours = this.split("-")
  return if (hours[1].toInt() > hours[0].toInt()) {
    // i.e. 10-18
    IntRange(hours[0].toInt(), hours[1].toInt()).toSet()
  } else {
    // i.e. 18-04 == between 18-23 || 0-4
    IntRange(hours[0].toInt(), 23).toSet() +
      IntRange(0, hours[1].toInt()).toSet()
  }
}

/**
 * Used for converting the day config into a list of numbers for the UI.
 */
private fun parseDays(dayConfig: String?): Set<Int> {
  if (dayConfig == null) {
    return emptySet()
  }

  val days = mutableSetOf<String>()
  val trimmed = dayConfig.replace(whiteSpace, "")
    .toLowerCase()
  val elements = trimmed.split(",")

  elements.forEach {
    when {
      it.isDayAlias() -> days.addAll(it.dayAlias())
      it.isDay() -> days.add(it)
      it.isDayRange() -> days.addAll(it.dayRange())
    }
  }

  return days.map { word ->
    word.toDayOfWeek()
  }.toSet()
}

private fun String.isDay(): Boolean = daysOfWeek.contains(this)

private fun String.isDayAlias(): Boolean = dayAliases.contains(this)

private fun String.isDayRange(): Boolean {
  val days = this.split("-")
  if (days.size != 2) {
    return false
  }

  return daysOfWeek.contains(days[0]) && daysOfWeek.contains(days[1])
}

private fun String.dayAlias(): Set<String> =
  when (this) {
    "weekdays" -> setOf("monday", "tuesday", "wednesday", "thursday", "friday")
    "weekends" -> setOf("saturday", "sunday")
    else -> throw InvalidConstraintException(ALLOWED_TIMES_CONSTRAINT_TYPE, "Failed parsing day alias $this")
  }

private fun String.toDayOfWeek(): Int =
  (fullDayFormatter.parseUnresolved(this.capitalize(), ParsePosition(0))
    ?: shortDayFormatter.parseUnresolved(this.capitalize(), ParsePosition(0)))
    ?.let { DayOfWeek.from(it).value }
    ?: throw InvalidConstraintException(ALLOWED_TIMES_CONSTRAINT_TYPE, "Failed parsing day '$this'")

private fun String.dayRange(): Set<String> {
  /**
   * Convert Mon-Fri or Monday-Friday to [DayOfWeek] integers to compute
   * a range, then back to a set of individual days that today can be
   * matched against.
   */
  val days = this.split("-").map { it.capitalize() }
  val day1 = days[0].toDayOfWeek()
  val day2 = days[1].toDayOfWeek()

  val intRange = if (day2 > day1) {
    // Mon - Fri
    IntRange(day1, day2).toList()
  } else {
    // Fri - Mon
    IntRange(day1, 7).toList() + IntRange(1, day2).toList()
  }

  return intRange.map {
    DayOfWeek.of(it)
      .toString()
      .toLowerCase()
  }
    .toSet()
}

private fun List<TimeWindow>.toNumeric(): List<TimeWindowNumeric> =
  map {
    TimeWindowNumeric(
      days = parseDays(it.days),
      hours = parseHours(it.hours)
    )
  }
