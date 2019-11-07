package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(
    include = JsonTypeInfo.As.EXISTING_PROPERTY,
  use = JsonTypeInfo.Id.NAME,
  property = "type")
@JsonSubTypes(
  Type(value = DependsOnConstraint::class, name = "depends-on"),
  Type(value = TimeWindowConstraint::class, name = "allowed-times")
)
sealed class Constraint(val type: String)

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
) : Constraint("allowed-times")

data class TimeWindow(
  val days: String? = null,
  val hours: String? = null
)
