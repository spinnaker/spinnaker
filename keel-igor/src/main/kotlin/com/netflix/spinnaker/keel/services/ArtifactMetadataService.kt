package com.netflix.spinnaker.keel.services

import com.netflix.spinnaker.igor.BuildService
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.Job
import com.netflix.spinnaker.keel.api.artifacts.PullRequest
import com.netflix.spinnaker.keel.api.artifacts.Repo
import com.netflix.spinnaker.model.Build
import io.github.resilience4j.retry.annotation.Retry
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

/**
 * Provides functionality to convert build metadata, which is coming from internal service, to artifact metadata (via igor).
 */
@Component
class ArtifactMetadataService(
  private val buildService: BuildService
) {

  /**
   * Returns additional metadata about the specified build and commit, if available. This call is configured
   * to auto-retry as it's not on a code path where any external retries would happen.
   */
  @Retry(name = "getArtifactMetadata", fallbackMethod = "fallback")
  suspend fun getArtifactMetadata(
    buildNumber: String,
    commitId: String
  ): ArtifactMetadata? {

      val builds =
        buildService.getArtifactMetadata(commitId = commitId, buildNumber = buildNumber)

      if (builds.isNullOrEmpty()) {
        return null
      }

      return builds.first().toArtifactMetadata(commitId)
  }

  private fun Build.toArtifactMetadata(commitId: String) =
    ArtifactMetadata(
      BuildMetadata(
        id = number,
        uid = id,
        job = Job(
          link = url,
          name = name
        ),
        startedAt = properties?.get("startedAt") as String?,
        completedAt = properties?.get("completedAt") as String?,
        number = number.toString(),
        status = result.toString()
      ),
      GitMetadata(
        commit = commitId,
        commitInfo = Commit(
          sha = scm?.first()?.sha1,
          link = scm?.first()?.compareUrl,
          message = scm?.first()?.message,
        ),
        author = scm?.first()?.committer,
        pullRequest = PullRequest(
          number = properties?.get("pullRequestNumber") as String?,
          url = properties?.get("pullRequestUrl") as String?
        ),
        repo = Repo(
          name = properties?.get("repoSlug") as String?,
          //TODO[gyardeni]: add link (will come from Igor)
          link = ""
        ),
        branch = scm?.first()?.branch,
        project = properties?.get("projectKey") as String?,
      )
    )


  // this method will be invoked whenever the retry will fail
  suspend fun fallback( buildNumber: String, commitId: String, e: Exception)
  : ArtifactMetadata? {
    log.error("fallback: received an error while calling artifact service for build number $buildNumber and commit id $commitId", e)
    throw e
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
