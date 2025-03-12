package com.netflix.spinnaker.keel.constraints

data class RegionalExecutionId(
  val region: String,
  val executionId: String
)
