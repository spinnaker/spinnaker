package com.netflix.spinnaker.keel.scm

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.kork.exceptions.SystemException
import org.slf4j.LoggerFactory

/**
 * An event from an SCM system.
 */
@JsonTypeInfo(
  use = JsonTypeInfo.Id.NAME,
  property = "type",
  include = JsonTypeInfo.As.PROPERTY
)
@JsonSubTypes(
  JsonSubTypes.Type(value = PrOpenedEvent::class, name = "pr.created"),
  JsonSubTypes.Type(value = PrUpdatedEvent::class, name = "pr.updated"),
  JsonSubTypes.Type(value = PrMergedEvent::class, name = "pr.merged"),
  JsonSubTypes.Type(value = PrDeclinedEvent::class, name = "pr.declined"),
  JsonSubTypes.Type(value = PrDeletedEvent::class, name = "pr.deleted"),
  JsonSubTypes.Type(value = CommitCreatedEvent::class, name = "commit.created"),
)
abstract class CodeEvent(
  open val repoKey: String,
  open val targetBranch: String,
  open val commitHash: String? = null,
  open val pullRequestId: String? = null,
  open val authorName: String? = null,
  open val authorEmail: String? = null,
  open val message: String? = null
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
  open val pullRequestBranch: String,
  override val authorName: String? = null,
  override val authorEmail: String? = null,
  override val message: String? = null
) : CodeEvent(repoKey, targetBranch, pullRequestId, authorName, authorEmail, message) {

  val String.headOfBranch: String
    get() = if (this.startsWith("refs/heads/")) this else "refs/heads/$this"
}

/**
 * Event that signals the creation of a PR.
 */
data class PrOpenedEvent(
  override val repoKey: String,
  override val targetBranch: String,
  override val pullRequestId: String,
  override val pullRequestBranch: String,
  override val authorName: String? = null,
  override val authorEmail: String? = null,
  override val message: String? = null
) : PrEvent(repoKey, targetBranch, pullRequestId, pullRequestBranch, authorName, authorEmail, message) {
  override val type: String = "pr.created"
  init { validate() }
}

/**
 * Event that signals an update of a PR.
 */
data class PrUpdatedEvent(
  override val repoKey: String,
  override val targetBranch: String,
  override val pullRequestId: String,
  override val pullRequestBranch: String,
  override val authorName: String? = null,
  override val authorEmail: String? = null,
  override val message: String? = null
) : PrEvent(repoKey, targetBranch, pullRequestId, pullRequestBranch, authorName, authorEmail, message) {
  override val type: String = "pr.updated"
  init { validate() }
}

/**
 * Event that signals a PR was merged.
 */
data class PrMergedEvent(
  override val repoKey: String,
  override val targetBranch: String,
  override val pullRequestId: String,
  override val pullRequestBranch: String,
  override val commitHash: String,
  override val authorName: String? = null,
  override val authorEmail: String? = null,
  override val message: String? = null
) : PrEvent(repoKey, targetBranch, pullRequestId, pullRequestBranch, authorName, authorEmail, message) {
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
  override val pullRequestBranch: String,
  override val authorName: String? = null,
  override val authorEmail: String? = null,
  override val message: String? = null
) : PrEvent(repoKey, targetBranch, pullRequestId, pullRequestBranch, authorName, authorEmail, message) {
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
  override val pullRequestBranch: String,
  override val authorName: String? = null,
  override val authorEmail: String? = null,
  override val message: String? = null
) : PrEvent(repoKey, targetBranch, pullRequestId, pullRequestBranch, authorName, authorEmail, message) {
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
  override val commitHash: String,
  override val authorName: String? = null,
  override val authorEmail: String? = null,
  override val message: String? = null
) : CodeEvent(repoKey, targetBranch, pullRequestId, authorName, authorEmail, message) {
  override val type: String = "commit.created"
  init { validate() }
}

/**
 * @return a [CodeEvent] based on the properties of this "artifact" definition from an Echo event.
 */
fun PublishedArtifact.toCodeEvent(): CodeEvent? {
  val log by lazy { LoggerFactory.getLogger(javaClass) }
  return when(type) {
    "create_commit" -> CommitCreatedEvent(
      repoKey = repoKey,
      targetBranch = targetBranch,
      commitHash = sha,
      pullRequestId = pullRequestId,
      authorName = authorName,
      authorEmail = authorEmail,
      message = message
    )
    "pr_opened" -> PrOpenedEvent(
      repoKey = repoKey,
      targetBranch = targetBranch,
      pullRequestId = pullRequestId,
      pullRequestBranch = pullRequestBranch,
      authorName = authorName,
      authorEmail = authorEmail,
      message = message
    )
    "pr_updated" -> PrUpdatedEvent(
      repoKey = repoKey,
      targetBranch = targetBranch,
      pullRequestId = pullRequestId,
      pullRequestBranch = pullRequestBranch,
      authorName = authorName,
      authorEmail = authorEmail,
      message = message
    )
    "pr_merged" -> PrMergedEvent(
      repoKey = repoKey,
      targetBranch = targetBranch,
      pullRequestId = pullRequestId,
      pullRequestBranch = pullRequestBranch,
      authorName = authorName,
      authorEmail = authorEmail,
      commitHash = sha,
      message = message
    )
    "pr_declined" -> PrDeclinedEvent(
      repoKey = repoKey,
      targetBranch = targetBranch,
      pullRequestId = pullRequestId,
      pullRequestBranch = pullRequestBranch,
      authorName = authorName,
      authorEmail = authorEmail,
      message = message
    )
    "pr_deleted" -> PrDeletedEvent(
      repoKey = repoKey,
      targetBranch = targetBranch,
      pullRequestId = pullRequestId,
      pullRequestBranch = pullRequestBranch,
      authorName = authorName,
      authorEmail = authorEmail,
      message = message
    )
    "create_tag" -> null // we just ignore tag creations
    else -> {
      log.warn("Unsupported code event type $type in $this")
      null
    }
  }
}

internal val KNOWN_ROCKET_CODE_EVENTS = setOf(
  "branch_launched",
  "create_commit",
  "create_tag",
  "pr_declined",
  "pr_deleted",
  "pr_merged",
  "pr_opened",
  "pr_relaunched",
  "pr_updated",
)

val PublishedArtifact.isCodeEvent: Boolean
  get() = type in KNOWN_ROCKET_CODE_EVENTS

private val PublishedArtifact.repoKey: String
  get() = metadata["repoKey"] as? String ?: throw MissingCodeEventDetails("repository", this)

private val PublishedArtifact.sourceBranch: String
  get() = metadata["sourceBranch"] as? String?: throw MissingCodeEventDetails("source branch", this)

private val PublishedArtifact.targetBranch: String
  get() = metadata["targetBranch"] as? String ?: throw MissingCodeEventDetails("target branch", this)

private val PublishedArtifact.sha: String
  get() = metadata["sha"] as? String ?: throw MissingCodeEventDetails("commit hash", this)

private val PublishedArtifact.pullRequestId: String
  get() = metadata["prId"] as? String ?: throw MissingCodeEventDetails("PR ID", this)

private val PublishedArtifact.pullRequestBranch: String
  get() = (metadata["prBranch"] as? String)?.let { it.replace("refs/heads/", "") }
    ?: throw MissingCodeEventDetails("PR branch", this)

private val PublishedArtifact.authorName: String?
  get() = metadata["authorName"] as? String

private val PublishedArtifact.message: String?
  get() = metadata["message"] as? String

private val PublishedArtifact.authorEmail: String?
  get() = metadata["authorEmail"] as? String

class MissingCodeEventDetails(what: String, event: PublishedArtifact) :
  SystemException("Missing $what information in code event: $event")

class InvalidCodeEvent(message: String) : SystemException(message)
