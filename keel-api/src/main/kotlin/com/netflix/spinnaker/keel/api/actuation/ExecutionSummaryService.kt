package com.netflix.spinnaker.keel.api.actuation

import com.netflix.spinnaker.keel.api.TaskStatus

/**
 * Provides the data needed by the UI to visualize a task
 */
interface ExecutionSummaryService {

  suspend fun getSummary(executionId: String): ExecutionSummary
}

data class ExecutionSummary(
  val name: String,
  val id: String,
  val status: TaskStatus,
  val currentStep: String?, // null if finished
  val summaryText: String,
  val error: String? = null
)
