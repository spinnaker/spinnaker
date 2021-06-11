package com.netflix.spinnaker.keel.notifications.scm

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.events.ArtifactDeployedNotification
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.igor.ScmService
import com.netflix.spinnaker.keel.igor.model.Comment
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
) {
  companion object {
    private val log by lazy { LoggerFactory.getLogger(ScmNotifier::class.java) }
  }

  /**
   * Posts a comment to the preview environment PR associated with the (preview) [Environment].
   */
  fun commentOnPullRequest(config: DeliveryConfig, environment: Environment, comment: String) {
    if (!environment.isPreview) return

    if (environment.pullRequestId != null) {
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
          log.error("Failed to comment on pull request for preview environment ${environment.name} in ${config.application}: $e")
        }
      }
    } else {
      log.debug("Skipping notifying artifact deployed for preview environment ${environment.name} as PR ID is missing.")
    }
  }
}