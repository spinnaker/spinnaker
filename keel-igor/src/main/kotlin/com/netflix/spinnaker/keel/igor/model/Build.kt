package com.netflix.spinnaker.keel.igor.model

import com.fasterxml.jackson.annotation.JsonInclude
import java.time.Instant

/**
 * An immutable data class that represents a published software artifact in the Spinnaker ecosystem.
 *
 * This class mirrors [com.netflix.spinnaker.igor.build.model.GenericBuild], but without all the Jackson baggage.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class Build(
  val building: Boolean = false,
  val fullDisplayName: String? = null,
  val name: String? = null,
  val number: Int = 0,
  val duration: Long? = null,
  /** String representation of time in nanoseconds since Unix epoch  */
  val timestamp: String? = null,

  val result: Result? = null,
  val url: String? = null,
  val id: String? = null,

  val scm: List<GenericGitRevision>? = null,
  val properties: Map<String, Any?>? = null
)

@JsonInclude(JsonInclude.Include.NON_NULL)
data class GenericGitRevision(
  val name: String? = null,
  val branch: String? = null,
  val sha1: String? = null,
  val committer: String? = null,
  val compareUrl: String? = null,
  val message: String? = null,
  val timestamp: Instant? = null,
  val remoteUrl: String? = null
)
enum class Result {
  SUCCESS, UNSTABLE, BUILDING, ABORTED, FAILURE, NOT_BUILT
}

enum class CompletionStatus {

  INCOMPLETE,
  SUCCEEDED,
  FAILED,
  ABORTED;

  fun allTerminalStatuses() = listOf(SUCCEEDED, FAILED, ABORTED)
}
