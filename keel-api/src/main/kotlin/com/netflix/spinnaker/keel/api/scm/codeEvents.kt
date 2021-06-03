package com.netflix.spinnaker.keel.api.scm

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.kork.exceptions.SystemException

/**
 * An event from an SCM system.
 */
abstract class CodeEvent(
  open val type: String,
  open val repoKey: String,
  open val targetBranch: String,
) {
  private val repoParts: List<String> by lazy { repoKey.split("/") }

  fun validate() {
    if (repoParts.size < 3) {
      throw InvalidCodeEvent("Commit event with malformed git repository key: $repoKey " +
        "(expected {repoType}/{projectKey}/{repoSlug})")
    }
  }

  val repoType: String
    get() = repoParts[0]

  val projectKey: String
    get() = repoParts[1]

  val repoSlug: String
    get() = repoParts[2]
}

/**
 * Event that signals the creation of a PR.
 */
data class PrCreatedEvent(
  override val type: String = "pr.created",
  override val repoKey: String,
  override val targetBranch: String,
  val sourceBranch: String
) : CodeEvent(type, repoKey, targetBranch) {
  init { validate() }
}

/**
 * Event that signals a PR was merged.
 */
data class PrMergedEvent(
  override val type: String = "pr.merged",
  override val repoKey: String,
  override val targetBranch: String,
  val sourceBranch: String
) : CodeEvent(type, repoKey, targetBranch) {
  init { validate() }
}

/**
 * Event that signals a commit was created (i.e. pushed to a repo).
 */
data class CommitCreatedEvent(
  override val type: String = "commit.created",
  override val repoKey: String,
  override val targetBranch: String,
  val commitHash: String,
) : CodeEvent(type, repoKey, targetBranch) {
  init { validate() }
}

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

class InvalidCodeEvent(message: String) : SystemException(message)