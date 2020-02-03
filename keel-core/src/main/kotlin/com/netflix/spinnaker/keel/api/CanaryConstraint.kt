package com.netflix.spinnaker.keel.api

import com.fasterxml.jackson.annotation.JsonIgnore
import java.time.Duration

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
