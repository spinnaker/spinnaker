package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.CANDIDATE
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.Job
import com.netflix.spinnaker.keel.api.artifacts.NPM
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.PullRequest
import com.netflix.spinnaker.keel.api.artifacts.Repo
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedVersioningStrategy
import com.netflix.spinnaker.keel.api.support.SpringEventPublisherBridge
import com.netflix.spinnaker.keel.services.ArtifactMetadataService
import com.netflix.spinnaker.keel.test.deliveryConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

internal class NpmArtifactSupplierTests : JUnit5Minutests {
  object Fixture {
    val artifactService: ArtifactService = mockk(relaxUnitFun = true)
    val eventBridge: SpringEventPublisherBridge = mockk(relaxUnitFun = true)
    val artifactMetadataService: ArtifactMetadataService = mockk(relaxUnitFun = true)
    val deliveryConfig = deliveryConfig()
    val npmArtifact = NpmArtifact(
      name = "fnord",
      deliveryConfigName = deliveryConfig.name,
      statuses = setOf(CANDIDATE)
    )
    val versions = listOf("1.0.0-rc", "1.0.0-rc.1", "1.0.0", "1.0.1-5", "1.0.2-h6", "1.0.3-rc-h7.gc0c603")
    val latestArtifact = PublishedArtifact(
      name = npmArtifact.name,
      type = npmArtifact.type,
      reference = npmArtifact.reference,
      version = versions.last(),
      metadata = mapOf("releaseStatus" to CANDIDATE, "buildNumber" to "7", "commitId" to "gc0c603")
    )
    val npmArtifactSupplier = NpmArtifactSupplier(eventBridge, artifactService, artifactMetadataService)

    val artifactMetadata = ArtifactMetadata(
      BuildMetadata(
        id = 1,
        uid = "1234",
        startedAt = "yesterday",
        completedAt = "today",
        job = Job(
          name = "job bla bla",
          link = "enkins.com"
        ),
        number = "7"
      ),
      GitMetadata(
        commit = "gc0c603",
        author = "keel-user",
        repo = Repo(
          name = "keel",
          link = ""
        ),
        pullRequest = PullRequest(
          number = "111",
          url = "www.github.com/pr/111"
        ),
        commitInfo = Commit(
          sha = "gc0c603",
          message = "this is a commit message",
          link = ""
        ),
        project = "spkr"
      )
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    context("NpmArtifactSupplier") {
      before {
        every {
          artifactService.getVersions(npmArtifact.name, listOf(CANDIDATE.name), artifactType = NPM)
        } returns versions
        every {
          artifactService.getArtifact(npmArtifact.name, versions.last(), NPM)
        } returns latestArtifact
        every {
          artifactMetadataService.getArtifactMetadata("7", "gc0c603")
        } returns artifactMetadata
      }

      test("supports NPM artifacts") {
        expectThat(npmArtifactSupplier.supportedArtifact).isEqualTo(
          SupportedArtifact(NPM, NpmArtifact::class.java)
        )
      }

      test("supports Netflix semver versioning strategy") {
        expectThat(npmArtifactSupplier.supportedVersioningStrategy)
          .isEqualTo(
            SupportedVersioningStrategy(NPM, NetflixSemVerVersioningStrategy::class.java)
          )
      }

      test("looks up latest artifact from igor") {
        val result = runBlocking {
          npmArtifactSupplier.getLatestArtifact(deliveryConfig, npmArtifact)
        }
        expectThat(result).isEqualTo(latestArtifact)
        verify(exactly = 1) {
          artifactService.getVersions(npmArtifact.name, listOf(CANDIDATE.name), artifactType = NPM)
          artifactService.getArtifact(npmArtifact.name, versions.last(), NPM)
        }
      }

      test("returns default full version string") {
        expectThat(npmArtifactSupplier.getFullVersionString(latestArtifact))
          .isEqualTo(latestArtifact.version)
      }

      test("returns release status based on artifact metadata") {
        expectThat(npmArtifactSupplier.getReleaseStatus(latestArtifact))
          .isEqualTo(CANDIDATE)
      }

      test("returns version display name based on Netflix semver convention") {
        expectThat(npmArtifactSupplier.getVersionDisplayName(latestArtifact))
          .isEqualTo(NetflixSemVerVersioningStrategy.getVersionDisplayName(latestArtifact))
      }

      test("returns git metadata based on Netflix semver convention") {
        val gitMeta = GitMetadata(commit = NetflixSemVerVersioningStrategy.getCommitHash(latestArtifact)!!)
        expectThat(npmArtifactSupplier.parseDefaultGitMetadata(latestArtifact, npmArtifact.versioningStrategy))
          .isEqualTo(gitMeta)
      }

      test("returns build metadata based on Netflix semver convention") {
        val buildMeta = BuildMetadata(id = NetflixSemVerVersioningStrategy.getBuildNumber(latestArtifact)!!)
        expectThat(npmArtifactSupplier.parseDefaultBuildMetadata(latestArtifact, npmArtifact.versioningStrategy))
          .isEqualTo(buildMeta)
      }

      test("returns artifact metadata based on ci provider") {
        val results = runBlocking {
          npmArtifactSupplier.getArtifactMetadata(latestArtifact)
        }
        expectThat(results)
          .isEqualTo(artifactMetadata)
      }
    }
  }
}
