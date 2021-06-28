package com.netflix.spinnaker.keel.api.scm

import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.kork.exceptions.SystemException

/**
 * An event from an SCM system.
 */
abstract class CodeEvent(
  open val repoKey: String,
  open val targetBranch: String,
  open val pullRequestId: String? = null
) {
  abstract val type: String

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
 * Base class for events that relates to a PR.
 */
abstract class PrEvent(
  override val repoKey: String,
  override val targetBranch: String,
  override val pullRequestId: String,
  open val sourceBranch: String
) : CodeEvent(repoKey, targetBranch, pullRequestId)

/**
 * Event that signals the creation of a PR.
 */
data class PrCreatedEvent(
  override val repoKey: String,
  override val targetBranch: String,
  override val pullRequestId: String,
  override val sourceBranch: String
) : PrEvent(repoKey, targetBranch, pullRequestId, sourceBranch) {
  override val type: String = "pr.created"
  init { validate() }
}

/**
 * Event that signals a PR was merged.
 */
data class PrMergedEvent(
  override val repoKey: String,
  override val targetBranch: String,
  override val pullRequestId: String,
  override val sourceBranch: String
) : PrEvent(repoKey, targetBranch, pullRequestId, sourceBranch) {
  override val type: String = "pr.merged"
  init { validate() }
}

/**
 * Event that signals a PR was declined.
 */
data class PrDeclinedEvent(
  override val repoKey: String,
  override val targetBranch: String,
  override val pullRequestId: String,
  override val sourceBranch: String
) : PrEvent(repoKey, targetBranch, pullRequestId, sourceBranch) {
  override val type: String = "pr.declined"
  init { validate() }
}

/**
 * Event that signals a PR was deleted.
 */
data class PrDeletedEvent(
  override val repoKey: String,
  override val targetBranch: String,
  override val pullRequestId: String,
  override val sourceBranch: String
) : PrEvent(repoKey, targetBranch, pullRequestId, sourceBranch) {
  override val type: String = "pr.deleted"
  init { validate() }
}

/**
 * Event that signals a commit was created (i.e. pushed to a repo).
 */
data class CommitCreatedEvent(
  override val repoKey: String,
  override val targetBranch: String,
  override val pullRequestId: String? = null,
  val commitHash: String
) : CodeEvent(repoKey, targetBranch, pullRequestId) {
  override val type: String = "commit.created"
  init { validate() }
}

/**
 * @return a [CodeEvent] based on the properties of this "artifact" definition from an Echo event.
 */
fun PublishedArtifact.toCodeEvent(): CodeEvent {
  return when(type) {
    "create_commit" -> CommitCreatedEvent(
      repoKey = repoKey,
      targetBranch = targetBranch,
      commitHash = sha,
      pullRequestId = pullRequestId
    )
    "pr_opened" -> PrCreatedEvent(
      repoKey = repoKey,
      targetBranch = targetBranch,
      pullRequestId = pullRequestId,
      sourceBranch = sourceBranch
    )
    "pr_merged" -> PrMergedEvent(
      repoKey = repoKey,
      targetBranch = targetBranch,
      pullRequestId = pullRequestId,
      sourceBranch = sourceBranch
    )
    "pr_declined" -> PrDeclinedEvent(
      repoKey = repoKey,
      targetBranch = targetBranch,
      pullRequestId = pullRequestId,
      sourceBranch = sourceBranch
    )
    "pr_deleted" -> PrDeletedEvent(
      repoKey = repoKey,
      targetBranch = targetBranch,
      pullRequestId = pullRequestId,
      sourceBranch = sourceBranch
    )
    else -> error("Unsupported code event type $type in $this")
  }
}

internal val INTERESTING_CODE_EVENTS = setOf("create_commit", "pr_opened", "pr_merged")

val PublishedArtifact.isCodeEvent: Boolean
  get() = type in INTERESTING_CODE_EVENTS

private val PublishedArtifact.repoKey: String
  get() = metadata["repoKey"] as? String ?: throw MissingCodeEventDetails("repository", this)

private val PublishedArtifact.sourceBranch: String
  get() = metadata["sourceBranch"] as? String?: throw MissingCodeEventDetails("source branch", this)

private val PublishedArtifact.targetBranch: String
  get() = metadata["targetBranch"] as? String ?: throw MissingCodeEventDetails("target branch", this)

private val PublishedArtifact.sha: String
  get() = metadata["sha"] as? String ?: throw MissingCodeEventDetails("commit hash", this)

private val PublishedArtifact.pullRequestId: String
  get() = metadata["prId"] as? String?: throw MissingCodeEventDetails("PR ID", this)

class MissingCodeEventDetails(what: String, event: PublishedArtifact) :
  SystemException("Missing $what information in code event: $event")

class InvalidCodeEvent(message: String) : SystemException(message)