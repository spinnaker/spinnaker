package com.netflix.spinnaker.keel.titus.batch

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.spinnaker.keel.orca.ExecutionDetailResponse
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper

/**
 * The status of a Run Job
 */
data class JobStatus(
  val id: String, // uuid
  val name: String,
  val type: String,
  val createdTime: Long, // epoch millis
  val provider: String, // e.g., titus
  val account: String,
  val application: String,
  val region: String,
  val completionDetails: CompletionDetails,
  val jobState: String
) {
  companion object {
    val mapper = configuredObjectMapper()
  }
}

data class CompletionDetails(
  val taskId: String // uuid
)

fun ExecutionDetailResponse.getJobStatus() : JobStatus? =
  this.variables
    ?.firstOrNull { it.key == "jobStatus" }
    ?.let { JobStatus.mapper.convertValue(it.value)
    }
