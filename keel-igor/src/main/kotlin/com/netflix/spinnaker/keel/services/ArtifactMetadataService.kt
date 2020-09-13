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
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import io.vavr.control.Try
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import retrofit2.HttpException
import java.time.Duration

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
  suspend fun getArtifactMetadata(
    buildNumber: String,
    commitId: String
  ): ArtifactMetadata? {

    val buildList = getArtifactMetadataWithRetries(commitId, buildNumber)

    if (buildList.isNullOrEmpty()) {
      return null
    }

    return buildList.first().toArtifactMetadata(commitId)
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
        commit = let {
          if (commitId.length > 7)
            commitId.substring(0, 7)
          else {
            commitId
          }
        },
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



  private fun getArtifactMetadataWithRetries(commitId: String, buildNumber: String): List<Build>? {
    val retry = Retry.of(
      "get artifact metadata",
      RetryConfig.custom<List<Build>?>()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(100))
        .retryOnException { e: Throwable? -> e is HttpException }
        .build()
    )
    return Try.ofSupplier(Retry.decorateSupplier(retry
    ) {
      runBlocking {
        buildService.getArtifactMetadata(commitId = commitId, buildNumber = buildNumber)
      }
    }).get()
  }
}
