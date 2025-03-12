package com.netflix.spinnaker.keel.notifications.scm

import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.model.Application
import com.netflix.spinnaker.keel.igor.ScmService
import com.netflix.spinnaker.keel.igor.model.BuildResult
import com.netflix.spinnaker.keel.igor.model.BuildState.SUCCESSFUL
import com.netflix.spinnaker.keel.igor.model.Comment
import com.netflix.spinnaker.keel.notifications.slack.DeploymentStatus.SUCCEEDED
import com.netflix.spinnaker.keel.notifications.slack.handlers.GitDataGenerator
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.dockerArtifact
import com.netflix.spinnaker.keel.test.previewEnvironment
import io.mockk.coEvery as every
import io.mockk.coVerify as verify
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import retrofit2.Response
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess
import java.lang.RuntimeException

class ScmNotifierTests {
  private val scmService: ScmService = mockk()
  private val gitDataGenerator: GitDataGenerator = mockk()
  private val front50Cache: Front50Cache = mockk()
  private val subject = ScmNotifier(scmService, front50Cache, gitDataGenerator)
  private val comment = slot<Comment>()
  private val buildResult = slot<BuildResult>()
  private val previewEnv = previewEnvironment()
  private val deliveryConfig = deliveryConfig(env = previewEnv)
  private val app = Application(
    name = deliveryConfig.application,
    email = "keel@keel.io",
    repoType = previewEnv.repoType,
    repoProjectKey = previewEnv.projectKey,
    repoSlug = previewEnv.repoSlug
  )
  private val publishedArtifact = dockerArtifact()
    .toArtifactVersion("1.0.0", gitMetadata = GitMetadata("3366e8822d916777129d35e22acd5499e4326a23"))

  @BeforeEach
  fun setup() {
    every {
      front50Cache.applicationByName(deliveryConfig.application)
    } returns app

    every {
      scmService.commentOnPullRequest(any(), any(), any(), any(), any())
    } just runs

    every {
      scmService.postBuildResultToCommit(any(), any(), any())
    } returns Response.success(null)

    every {
      gitDataGenerator.generateShaUrl(any(), any())
    } returns "https://link"
  }

  @Test
  fun `does not post a PR comment if PR ID is missing`() {
    expectCatching {
      subject.commentOnPullRequest(deliveryConfig, previewEnv, "blah")
    }.isSuccess()

    verify(exactly = 0) {
      scmService.commentOnPullRequest(previewEnv.repoType!!, previewEnv.projectKey!!, previewEnv.repoSlug!!, any(), any())
    }
  }

  @Test
  fun `does not post a PR comment if app SCM config is missing`() {
    every {
      front50Cache.applicationByName(app.name)
    } returns app.copy(repoSlug = null)

    expectCatching {
      subject.commentOnPullRequest(deliveryConfig, previewEnv.withPullRequestId(), "blah")
    }.isSuccess()

    verify(exactly = 0) {
      scmService.commentOnPullRequest(previewEnv.repoType!!, previewEnv.projectKey!!, previewEnv.repoSlug!!, any(), any())
    }
  }

  @Test
  fun `posts a PR comment if all metadata is available`() {
    expectCatching {
      subject.commentOnPullRequest(deliveryConfig, previewEnv.withPullRequestId(), "blah")
    }.isSuccess()

    verify {
      scmService.commentOnPullRequest(previewEnv.repoType!!, previewEnv.projectKey!!, previewEnv.repoSlug!!, "42", capture(comment))
    }

    expectThat(comment.captured.text).isEqualTo("blah")
  }

  @Test
  fun `does not bubble up exceptions when posting PR comments`() {
    every {
      front50Cache.applicationByName(app.name)
    } throws RuntimeException("oh noes!")

    expectCatching {
      subject.commentOnPullRequest(deliveryConfig, previewEnv.withPullRequestId(), "blah")
    }.isSuccess()
  }

  @Test
  fun `does not post deployment status if commit hash missing`() {
    expectCatching {
      subject.postDeploymentStatusToCommit(deliveryConfig, previewEnv, publishedArtifact.withoutCommitHash(), SUCCEEDED)
    }.isSuccess()

    verify(exactly = 0) {
      scmService.postBuildResultToCommit(previewEnv.repoType!!, any(), any())
    }
  }

  @Test
  fun `does not post deployment status if only short commit is available`() {
    expectCatching {
      subject.postDeploymentStatusToCommit(deliveryConfig, previewEnv, publishedArtifact.withShortCommitHash(), SUCCEEDED)
    }.isSuccess()

    verify(exactly = 0) {
      scmService.postBuildResultToCommit(previewEnv.repoType!!, publishedArtifact.commitHash!!, any())
    }
  }

  @Test
  fun `posts deployment status to SCM if all metadata is available`() {
    expectCatching {
      subject.postDeploymentStatusToCommit(deliveryConfig, previewEnv, publishedArtifact, SUCCEEDED)
    }.isSuccess()

    verify {
      scmService.postBuildResultToCommit(previewEnv.repoType!!, publishedArtifact.commitHash!!, capture(buildResult))
    }

    expectThat(buildResult.captured.state).isEqualTo(SUCCESSFUL)
  }


  @Test
  fun `does not bubble up exceptions when posting deployment status`() {
    every {
      front50Cache.applicationByName(app.name)
    } throws RuntimeException("oh noes!")

    expectCatching {
      subject.postDeploymentStatusToCommit(deliveryConfig, previewEnv, publishedArtifact, SUCCEEDED)
    }.isSuccess()
  }

  private fun Environment.withPullRequestId() =
    addMetadata("pullRequestId" to "42")

  private fun PublishedArtifact.withoutCommitHash() =
    copy(gitMetadata = null)

  private fun PublishedArtifact.withShortCommitHash() =
    copy(gitMetadata = GitMetadata("3366e88"))
}