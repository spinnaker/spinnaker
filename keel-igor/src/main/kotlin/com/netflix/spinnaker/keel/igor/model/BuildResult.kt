package com.netflix.spinnaker.keel.igor.model

import com.fasterxml.jackson.annotation.JsonProperty

/**
 * The result of a build which can be posted to a commit.
 */
data class BuildResult(
  val state: BuildState,
  val key: String,
  val url: String,
  val name: String? = key,
  val description: String? = null
)

/**
 * The status of a build which can be posted to a commit.
 */
enum class BuildState {
  @JsonProperty("INPROGRESS") IN_PROGRESS,
  SUCCESSFUL,
  FAILED
}
