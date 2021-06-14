package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.constraints.ConstraintRepository
import com.netflix.spinnaker.keel.api.constraints.ConstraintState
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.FAIL
import com.netflix.spinnaker.keel.api.constraints.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.constraints.StatefulConstraintEvaluator
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintAttributesType
import com.netflix.spinnaker.keel.api.constraints.SupportedConstraintType
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.core.api.TimeWindowConstraint
import com.netflix.spinnaker.keel.core.api.TimeWindowNumeric
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
 * ```
 * constraints:
 *  - type: allowed-times
 *    windows:
 *      - days: Monday,Tuesday-Friday
 *        hours: 11-16
 *      - days: Wed
 *        hours: 12-14
 *    tz: America/Los_Angeles
 * ```
 */
@Component
class AllowedTimesConstraintEvaluator(
  private val clock: Clock,
  private val dynamicConfigService: DynamicConfigService,
  override val eventPublisher: EventPublisher,
  override val repository: ConstraintRepository
) : StatefulConstraintEvaluator<TimeWindowConstraint, AllowedTimesConstraintAttributes> {
  override val attributeType = SupportedConstraintAttributesType<AllowedTimesConstraintAttributes>("allowed-times")

  companion object {
    const val CONSTRAINT_NAME = "allowed-times"

    val whiteSpace =
      """\s""".toRegex()
    val intOnly =
      """^\d+$""".toRegex()
    val intRange =
      """^\d+\-\d+$""".toRegex()
    val separators =
      """[\s,\-]""".toRegex()
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

    val dayAliases = setOf("weekdays", "weekends")

    fun validateHours(hours: String): Boolean {
      return hours.split(separators).all {
        it.matches(intOnly) && it.toInt() >= 0 && it.toInt() <= 23
      }
    }

    fun validateDays(days: String): Boolean {
      return days.toLowerCase().split(separators).all {
        daysOfWeek.contains(it) || dayAliases.contains(it)
      }
    }

    fun parseHours(hourConfig: String?): Set<Int> {
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
        else -> throw InvalidConstraintException(CONSTRAINT_NAME, "Failed parsing day alias $this")
      }

    private fun String.toDayOfWeek(): Int =
      (fullDayFormatter.parseUnresolved(this.capitalize(), ParsePosition(0))
        ?: shortDayFormatter.parseUnresolved(this.capitalize(), ParsePosition(0)))
        ?.let { DayOfWeek.from(it).value }
        ?: throw InvalidConstraintException(CONSTRAINT_NAME, "Failed parsing day '$this'")

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

    fun toNumericTimeWindows(constraint: TimeWindowConstraint): List<TimeWindowNumeric> =
      constraint.windows.map {
        TimeWindowNumeric(
          days = parseDays(it.days),
          hours = parseHours(it.hours)
        )
      }
  }

  override val supportedType = SupportedConstraintType<TimeWindowConstraint>("allowed-times")

  private fun currentlyPassing(constraint: TimeWindowConstraint, deliveryConfig: DeliveryConfig, targetEnvironment: Environment): Boolean {
    val tz: ZoneId = if (constraint.tz != null) {
      ZoneId.of(constraint.tz)
    } else {
      defaultTimeZone()
    }

    val now = clock
      .instant()
      .atZone(tz)

    constraint.windows.forEach {
      val hours = parseHours(it.hours)
      val hoursMatch = hours.isEmpty() || hours.contains(now.hour)

      val today = now.dayOfWeek
        .toString()
        .toLowerCase()
      val todayShort = now.dayOfWeek
        .getDisplayName(TextStyle.SHORT, Locale.getDefault())

      val days = parseDays(it.days, deliveryConfig, targetEnvironment.name)
      val daysMatch = days.isEmpty() || days.contains(today) || days.contains(todayShort)

      if (hoursMatch && daysMatch) {
        return true
      }
    }

    return false
  }

  override fun canPromote(
    artifact: DeliveryArtifact,
    version: String,
    deliveryConfig: DeliveryConfig,
    targetEnvironment: Environment,
    constraint: TimeWindowConstraint,
    state: ConstraintState
  ): Boolean {

    if (state.judgedByUser()) {
      // if a user has judged this constraint, always take that judgement.
      if (state.failed()) {
        return false
      } else if (state.passed()) {
        return true
      }
    }

    val currentlyPassing = currentlyPassing(constraint, deliveryConfig, targetEnvironment)
    val status = if (currentlyPassing) {
      PASS
    } else {
      FAIL
    }

    if (status != state.status) {
      // change the stored state if the current calculated status is different than what's saved.
      val attributes = AllowedTimesConstraintAttributes(
        allowedTimes = toNumericTimeWindows(constraint),
        timezone = constraint.tz ?: dynamicConfigService.getConfig(String::class.java, "default.time-zone", "America/Los_Angeles"),
        currentlyPassing = currentlyPassing
      )
      repository.storeConstraintState(
        state.copy(
          attributes = attributes,
          status = status,
          judgedAt = clock.instant(),
          judgedBy = "Spinnaker"
        )
      )
    }

    return currentlyPassing
  }

  private fun parseDays(dayConfig: String?, deliveryConfig: DeliveryConfig, envName: String): Set<String> {
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
          CONSTRAINT_NAME,
          "Invalid allowed-times constraint ($it) on deliveryConfig: ${deliveryConfig.name}, " +
            "application: ${deliveryConfig.application}, environment: $envName"
        )
      }
    }

    return days
  }

  private fun defaultTimeZone(): ZoneId =
    ZoneId.of(
      dynamicConfigService.getConfig(String::class.java, "default.time-zone", "America/Los_Angeles")
    )
}
