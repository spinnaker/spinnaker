package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.api.TaskStatus
import java.time.Instant

/**
 * Provides the data needed by the UI to visualize a task
 */
interface ExecutionSummaryService {

  fun getSummary(executionId: String): ExecutionSummary
}

data class ExecutionSummary(
  val name: String,
  val id: String,
  val status: TaskStatus,
  val currentStage: Stage?, // null if finished
  val stages: List<Stage>,
  val deployTargets: List<RolloutTargetWithStatus>,
  val error: String? = null
)

// simplified representation of a stage from orca,
// we can add more detail as needed
data class Stage(
  val id: String,
  val type: String,
  val name: String,
  val startTime: Instant?,
  val endTime: Instant?,
  val status: TaskStatus,
  val refId: String, //this is a short code for the stage, used in ordering
  val requisiteStageRefIds: List<String> //this is a coded form of what stage goes after another stage/belongs to a stage
)

data class RolloutTargetWithStatus(
  val rolloutTarget: RolloutTarget,
  val status: RolloutStatus
)

//todo eb: take from buoy?
data class RolloutTarget(
  val cloudProvider: String,
  val location: RolloutLocation,
)

enum class RolloutStatus {
  NOT_STARTED, RUNNING, SUCCEEDED, FAILED
}

data class RolloutLocation(
  val account: String,
  val region: String,
  val sublocations: List<String> = emptyList()
)

data class RolloutStep(
  val id: String,
  val targets: List<RolloutTarget>
)
