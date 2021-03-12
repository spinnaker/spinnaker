package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.constraints.ConstraintStateAttributes
import com.netflix.spinnaker.keel.core.api.TimeWindowNumeric

data class AllowedTimesConstraintAttributes(
  val allowedTimes: List<TimeWindowNumeric>,
  val timezone: String? = null,
  val currentlyPassing: Boolean = true
) : ConstraintStateAttributes("allowed-times")
