package com.netflix.spinnaker.keel.notifications.scm

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.igor.ScmService
import com.netflix.spinnaker.keel.igor.model.BuildResult
import com.netflix.spinnaker.keel.igor.model.BuildState
import com.netflix.spinnaker.keel.igor.model.BuildState.SUCCESSFUL
import com.netflix.spinnaker.keel.igor.model.Comment
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus.FAILED
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus.SUCCEEDED
import com.netflix.spinnaker.keel.notifications.slack.handlers.GitDataGenerator
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Encapsulates utility functions to notify users via comments on SCM objects like pull requests.
 */
@Component
class ScmNotifier(
  private val scmService: ScmService,
  private val front50Cache: Front50Cache,
  private val gitDataGenerator: GitDataGenerator
) {
  companion object {
    private val log by lazy { LoggerFactory.getLogger(ScmNotifier::class.java) }
  }

  /**
   * Posts a comment to the PR associated with the (preview) [Environment].
   */
  fun commentOnPullRequest(config: DeliveryConfig, environment: Environment, comment: String) {
    if (!environment.isPreview) return

    if (!environment.hasValidPullRequestId)  {
      log.debug("Skipping notifying artifact deployed for preview environment ${environment.name} as PR ID is missing.")
      return
    }

    runBlocking {
      try {
        val app = front50Cache.applicationByName(config.application)
        if (app.repoType != null && app.repoProjectKey != null && app.repoSlug != null) {
          scmService.commentOnPullRequest(
            scmType = app.repoType!!,
            projectKey = app.repoProjectKey!!,
            repoSlug = app.repoSlug!!,
            pullRequestId = environment.pullRequestId!!,
            comment = Comment(comment)
          )
          log.debug("Posted comment on PR ${environment.pullRequestId} for ${app.repoType}/${app.repoProjectKey}/${app.repoSlug}: $comment")
        }
      } catch (e: Exception) {
        log.error("Failed to comment on pull request for preview environment ${environment.name} in ${config.application}: $e", e)
      }
    }
  }

  /**
   * Posts a "build result" to the commit associated with the [publishedArtifact] representing the status of its deployment.
   */
  fun postDeploymentStatusToCommit(
    config: DeliveryConfig,
    environment: Environment,
    publishedArtifact: PublishedArtifact,
    status: DeploymentStatus
  ) {
    if (!environment.isPreview) return

    val commitHash = publishedArtifact.commitHash

    if (commitHash == null) {
      log.debug("Can't post deployment status to SCM as commit hash is not known for artifact $publishedArtifact.")
      return
    }

    if (commitHash.length < 40) {
      log.debug("Can't post deployment status to SCM with short commit hash for artifact $publishedArtifact")
      return
    }

    runBlocking {
      try {
        val app = front50Cache.applicationByName(config.application)
        if (app.repoType != null) {
          scmService.postBuildResultToCommit(
            scmType = app.repoType!!,
            commitHash = commitHash,
            buildResult = BuildResult(
              state = status.toBuildState(),
              key = "Preview environment deployment",
              url = gitDataGenerator.generateShaUrl(config.application, commitHash.substring(0, 7)),
              description = "${if (status == SUCCEEDED) "Successful" else "Failed"} deployment from branch ${environment.branch}"
            )
          )
          log.debug("Posted deployment $status on commit hash $commitHash for ${app.repoType}/${app.repoProjectKey}/${app.repoSlug}")
        }
      } catch (e: Exception) {
        log.error("Failed to post deployment status for preview environment ${environment.name} in ${config.application}: $e", e)
      }
    }
  }

  private fun DeploymentStatus.toBuildState() =
    when (this) {
      SUCCEEDED -> SUCCESSFUL
      FAILED -> BuildState.FAILED
    }
}