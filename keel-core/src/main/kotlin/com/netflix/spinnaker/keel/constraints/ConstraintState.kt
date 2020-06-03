package com.netflix.spinnaker.keel.constraints

import com.fasterxml.jackson.annotation.JsonTypeName
import com.netflix.spinnaker.keel.api.constraints.ConstraintStateAttributes
import java.time.Instant

@JsonTypeName("pipeline")
data class PipelineConstraintStateAttributes(
  val executionId: String? = null,
  val attempt: Int,
  val latestAttempt: Instant,
  val lastExecutionStatus: String? = null
) : ConstraintStateAttributes("pipeline")

@JsonTypeName("canary")
data class CanaryConstraintAttributes(
  val executions: Set<RegionalExecutionId> = emptySet(),
  val startAttempt: Int = 0,
  val status: Set<CanaryStatus> = emptySet()
) : ConstraintStateAttributes("canary")

data class RegionalExecutionId(
  val region: String,
  val executionId: String
)

data class CanaryStatus(
  val executionId: String,
  val region: String,
  val executionStatus: String,
  val scores: List<Double> = emptyList(),
  val scoreMessage: String?
)
