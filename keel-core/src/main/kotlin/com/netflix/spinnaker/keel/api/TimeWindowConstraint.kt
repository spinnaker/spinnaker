package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.constraints.AllowedTimesConstraintEvaluator
import java.time.DateTimeException
import java.time.ZoneId
import java.time.zone.ZoneRulesException

/**
 * A constraint that requires the current time to fall within an allowed window
 */
data class TimeWindowConstraint(
  val windows: List<TimeWindow>,
  val tz: String? = null
) : Constraint("allowed-times") {
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

data class TimeWindow(
  val days: String? = null,
  val hours: String? = null
) {
  init {
    if (hours != null) {
      require(AllowedTimesConstraintEvaluator.validateHours(hours)) {
        "allowed-times hours must only contains hours 0-23 that are comma separated " +
          "and/or containing dashes to denote a range"
      }
    }

    if (days != null) {
      require(AllowedTimesConstraintEvaluator.validateDays(days)) {
        "allowed-times days must only contain any of " +
          "${AllowedTimesConstraintEvaluator.dayAliases + AllowedTimesConstraintEvaluator.daysOfWeek} " +
          "that are comma separated and/or containing dashes to denote a range"
      }
    }
  }
}
