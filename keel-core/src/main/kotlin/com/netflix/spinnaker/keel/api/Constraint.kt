package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.annotation.JsonIgnore
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
  Type(value = ManualJudgementConstraint::class, name = "manual-judgement"),
  Type(value = PipelineConstraint::class, name = "pipeline"),
  Type(value = CanaryConstraint::class, name = "canary")
)
abstract class Constraint(val type: String)

abstract class StatefulConstraint(type: String) : Constraint(type)

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
  val timeout: Duration = Duration.ofDays(7)
) : StatefulConstraint("manual-judgement")

data class PipelineConstraint(
  val timeout: Duration = Duration.ofHours(2),
  val pipelineId: String,
  val retries: Int = 0,
  val parameters: Map<String, Any?> = emptyMap()
) : StatefulConstraint("pipeline")

// TODO: doesn't support sliding look-back, custom metric scope, or extended scope parameters (will default to asg)
data class CanaryConstraint(
  val timeout: Duration = Duration.ofHours(24),
  // TODO: resolve config name -> id via kayenta/canaryConfig?application=application
  val canaryConfigId: String,
  val beginAnalysisAfter: Duration = Duration.ofMinutes(10),
  val canaryAnalysisInterval: Duration = Duration.ofMinutes(30),
  val cleanupDelay: Duration = Duration.ZERO,
  val lifetime: Duration,
  val metricsAccount: String? = null,
  val storageAccount: String? = null,
  val marginalScore: Int,
  val passScore: Int,
  val source: CanarySource,
  val regions: Set<String>,
  val capacity: Int,
  // If true, the failure of a canary in one region triggers the immediate cancellation of other regions
  val failureCancelsRunningRegions: Boolean = true,
  // If set to >0, a multi-region canary only has to pass in this number of regions for the constraint to pass
  val minSuccessfulRegions: Int = 0
) : StatefulConstraint("canary") {
  init {
    require(minSuccessfulRegions <= regions.size) {
      "passIfSucceedsInNRegions = $minSuccessfulRegions but only ${regions.size} regions are specified"
    }

    if (regions.size < 2) {
      require(minSuccessfulRegions == 0) {
        "passIfSucceedsInNRegions can only be set on a multi-region constraint"
      }
    }
  }

  @JsonIgnore
  val allowedFailures: Int = when (minSuccessfulRegions) {
    0 -> 0
    else -> regions.size - minSuccessfulRegions
  }
}

data class CanarySource(
  val account: String,
  val cloudProvider: String,
  val cluster: String
)

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
