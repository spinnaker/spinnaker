package com.netflix.spinnaker.keel.constraints

import com.netflix.spinnaker.keel.api.constraints.ConstraintStateAttributes
import java.time.Instant

data class PipelineConstraintStateAttributes(
  val executionId: String? = null,
  val attempt: Int,
  val latestAttempt: Instant,
  val lastExecutionStatus: String? = null
) : ConstraintStateAttributes("pipeline")
