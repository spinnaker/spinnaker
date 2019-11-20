package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.netflix.spinnaker.keel.constraints.AllowedTimesConstraintEvaluator
import java.lang.Exception
import java.time.DateTimeException
import java.time.Duration
import java.time.ZoneId
import java.time.zone.ZoneRulesException

@JsonTypeInfo(
  include = JsonTypeInfo.As.EXISTING_PROPERTY,
  use = JsonTypeInfo.Id.NAME,
  property = "type")
@JsonSubTypes(
  Type(value = DependsOnConstraint::class, name = "depends-on"),
  Type(value = TimeWindowConstraint::class, name = "allowed-times"),
  Type(value = ManualJudgementConstraint::class, name = "manual-judgement")
)
sealed class Constraint(
  open val type: String
)

sealed class StatefulConstraint(
  override val type: String
) : Constraint(type)

/**
 * A constraint that requires that an artifact has been successfully promoted to a previous
 * environment first.
 */
data class DependsOnConstraint(
  val environment: String
) : Constraint("depends-on")

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

data class ManualJudgementConstraint(
  // TODO: process timeouts
  val timeout: Duration = Duration.ofDays(7)
) : StatefulConstraint("manual-judgement")

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
