package com.netflix.spinnaker.keel.api.scm

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.kork.exceptions.SystemException

/**
 * An event from an SCM system.
 */
sealed class CodeEvent(
  open val repoKey: String,
  open val targetBranch: String
)

/**
 * Event that signals the creation of a PR.
 */
data class PrCreatedEvent(
  override val repoKey: String,
  override val targetBranch: String,
  val sourceBranch: String
) : CodeEvent(repoKey, targetBranch)

/**
 * Event that signals a PR was merged.
 */
data class PrMergedEvent(
  override val repoKey: String,
  override val targetBranch: String,
  val sourceBranch: String
) : CodeEvent(repoKey, targetBranch)

/**
 * Event that signals a commit was created (i.e. pushed to a repo).
 */
data class CommitCreatedEvent(
  override val repoKey: String,
  override val targetBranch: String,
  val commitHash: String
) : CodeEvent(repoKey, targetBranch)

/**
 * @return a [CodeEvent] based on the properties of this "artifact" definition from an Echo event.
 */
fun PublishedArtifact.toCodeEvent(): CodeEvent {
  return when(type) {
    "create_commit" -> CommitCreatedEvent(
      repoKey = metadata["repoKey"] as? String ?: throw MissingCodeEventDetails("repository", this),
      targetBranch = metadata["branch"] as? String ?: throw MissingCodeEventDetails("branch", this),
      commitHash = metadata["sha"] as? String ?: throw MissingCodeEventDetails("commit hash", this)
    )
    "pr_opened" -> PrCreatedEvent(
      repoKey = metadata["repoKey"] as? String ?: throw MissingCodeEventDetails("repository", this),
      targetBranch = metadata["branch"] as? String ?: throw MissingCodeEventDetails("branch", this),
      // TODO: currently Echo does not relay the source branch info
      sourceBranch = "N/A"
    )
    "pr_merged" -> PrMergedEvent(
      repoKey = metadata["repoKey"] as? String ?: throw MissingCodeEventDetails("repository", this),
      targetBranch = metadata["branch"] as? String ?: throw MissingCodeEventDetails("branch", this),
      // TODO: currently Echo does not relay the source branch info
      sourceBranch = "N/A"
    )
    else -> error("Unsupported code event type $type in $this")
  }
}

internal val INTERESTING_CODE_EVENTS = setOf("create_commit", "pr_opened", "pr_merged")

val PublishedArtifact.isCodeEvent: Boolean
  get() = type in INTERESTING_CODE_EVENTS

class MissingCodeEventDetails(what: String, event: PublishedArtifact) :
  SystemException("Missing $what information in code event: $event")



