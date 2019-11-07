package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.TimeWindowConstraint
import com.netflix.spinnaker.keel.constraints.ConstraintEvaluator.Companion.getConstraintForEnvironment
import com.netflix.spinnaker.keel.exceptions.InvalidConstraintException
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import org.springframework.stereotype.Component
import java.text.ParsePosition
import java.time.Clock
import java.time.DayOfWeek
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/**
 * An environment promotion constraint to gate promotions to time windows.
 * Multiple windows can be specified.
 *
 * Hours and days parameters support a mix of ranges and comma separated values.
 *
 * Days can be specified using full or short names according to the default JVM locale.
 *
 * A timezone can be set in the constraint and a system-wide default can by set via
 * the dynamic property "default.time-zone" which defaults to UTC.
 *
 * Example env constraint:
 *
 * constraints:
 *  - type: allowed-times
 *    windows:
 *      - days: Monday,Tuesday-Friday
 *        hours: 11-16
 *        tz: America/Los_Angeles
 *      - days: Wed
 *        hours: 12-14
 *        tz: America/Los_Angeles
 */
@Component
class AllowedTimesConstraintEvaluator(
  private val clock: Clock,
  private val dynamicConfigService: DynamicConfigService
) : ConstraintEvaluator<TimeWindowConstraint> {
  companion object {
    val whiteSpace = """\s""".toRegex()
    val intOnly = """^\d+$""".toRegex()
    val intRange = """^\d+\-\d+$""".toRegex()

    val fullDayFormatter: DateTimeFormatter = DateTimeFormatter
      .ofPattern("EEEE", Locale.getDefault())
    val shortDayFormatter: DateTimeFormatter = DateTimeFormatter
      .ofPattern("EEE", Locale.getDefault())

    val daysOfWeek = DayOfWeek.values()
      .map {
        listOf(
          it.toString().toLowerCase(),
          it.getDisplayName(TextStyle.SHORT, Locale.getDefault()).toLowerCase()
        )
      }
      .flatten()
      .toMutableSet()
  }

  override val constraintType = TimeWindowConstraint::class.java

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: String
  ): Boolean {
    val constraint = getConstraintForEnvironment(deliveryConfig, targetEnvironment, constraintType)

    val tz: ZoneId = if (constraint.tz != null) {
      ZoneId.of(constraint.tz)
    } else {
      defaultTimeZone()
    }

    val now = clock
      .instant()
      .atZone(tz)

    constraint.windows.forEach {
      val hours = parseHours(it.hours, deliveryConfig, targetEnvironment)
      val hoursMatch = hours.isEmpty() || hours.contains(now.hour)

      val today = now.dayOfWeek
        .toString()
        .toLowerCase()
      val todayShort = now.dayOfWeek
        .getDisplayName(TextStyle.SHORT, Locale.getDefault())

      val days = parseDays(it.days, deliveryConfig, targetEnvironment)
      val daysMatch = days.isEmpty() || days.contains(today) || days.contains(todayShort)

      if (hoursMatch && daysMatch) {
        return true
      }
    }

    return false
  }

  private fun parseHours(hourConfig: String?, deliveryConfig: DeliveryConfig, targetEnvironment: String): Set<Int> {
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
        else -> throw InvalidConstraintException(
          "Invalid allowed-times constraint ($it) on deliveryConfig: ${deliveryConfig.name}, " +
            "application: ${deliveryConfig.application}, environment: $targetEnvironment")
      }
    }

    return hours
  }

  private fun parseDays(dayConfig: String?, deliveryConfig: DeliveryConfig, targetEnvironment: String): Set<String> {
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
        else -> throw InvalidConstraintException(
          "Invalid allowed-times constraint ($it) on deliveryConfig: ${deliveryConfig.name}, " +
            "application: ${deliveryConfig.application}, environment: $targetEnvironment")
      }
    }

    return days
  }

  private fun String.isInt(): Boolean = this.matches(intOnly)

  private fun String.isIntRange(): Boolean = this.matches(intRange)

  private fun String.hourRange(): Set<Int> {
    val hours = this.split("-")
    return if (hours[1] > hours[0]) {
      // i.e. 10-18
      IntRange(hours[0].toInt(), hours[1].toInt()).toSet()
    } else {
      // i.e. 18-04 == between 18-23 || 0-4
      IntRange(hours[0].toInt(), 23).toSet() +
        IntRange(0, hours[1].toInt()).toSet()
    }
  }

  private fun String.isDay(): Boolean = daysOfWeek.contains(this)

  private fun String.isDayAlias(): Boolean =
    this == "weekends" || this == "weekdays"

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
      else -> throw InvalidConstraintException("Failed parsing day alias $this")
    }

  private fun String.dayRange(): Set<String> {
    /**
     * Convert Mon-Fri or Monday-Friday to [DayOfWeek] integers to compute
     * a range, then back to a set of individual days that today can be
     * matched against.
     */
    val days = this.split("-").map { it.capitalize() }
    val day1Temporal = fullDayFormatter.parseUnresolved(days[0], ParsePosition(0))
      ?: shortDayFormatter.parseUnresolved(days[0], ParsePosition(0))
      ?: throw InvalidConstraintException("Failed parsing day range $this")
    val day2Temporal = fullDayFormatter.parseUnresolved(days[1], ParsePosition(0))
      ?: shortDayFormatter.parseUnresolved(days[1], ParsePosition(0))
      ?: throw InvalidConstraintException("Failed parsing day range $this")

    val day1 = DayOfWeek.from(day1Temporal).value
    val day2 = DayOfWeek.from(day2Temporal).value

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

  private fun defaultTimeZone(): ZoneId =
    ZoneId.of(
      dynamicConfigService.getConfig(String::class.java, "default.time-zone", "UTC")
    )
}
