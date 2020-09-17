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
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import kotlinx.coroutines.TimeoutCancellationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import retrofit2.HttpException
import java.time.Duration
import java.util.concurrent.CancellationException

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
      log.debug("artifact metadata buildList is null or empty, for build $buildNumber and commit $commitId")
      return null
    }

    log.debug("received artifact metadata $buildList for build $buildNumber and commit $commitId")
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


  private suspend fun getArtifactMetadataWithRetries(commitId: String, buildNumber: String): List<Build>? {
    val retry = Retry.of(
      "get artifact metadata",
      RetryConfig.custom<List<Build>?>()
        .maxAttempts(3)
        .waitDuration(Duration.ofMillis(100))
        .retryOnException { t: Throwable ->
          // https://github.com/resilience4j/resilience4j/issues/688
          val retryFilter = when (t) {
            is TimeoutCancellationException -> true
            else -> t is HttpException
          }
          log.debug("retry filter = $retryFilter for exception ${t::class.java.name}")
          retryFilter
        }
        .build()
    )

    return retry.executeSuspendFunction {
      buildService.getArtifactMetadata(commitId = commitId.trim(), buildNumber = buildNumber.trim())
    }

  }


  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
