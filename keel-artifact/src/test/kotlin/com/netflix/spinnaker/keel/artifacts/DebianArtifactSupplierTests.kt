package com.netflix.spinnaker.keel.artifacts

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.SNAPSHOT
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.Commit
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.Job
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.PullRequest
import com.netflix.spinnaker.keel.api.artifacts.Repo
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedVersioningStrategy
import com.netflix.spinnaker.keel.api.support.SpringEventPublisherBridge
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.services.ArtifactMetadataService
import com.netflix.spinnaker.keel.test.deliveryConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNull
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

internal class DebianArtifactSupplierTests : JUnit5Minutests {
  object Fixture {
    val artifactService: ArtifactService = mockk(relaxUnitFun = true)
    val eventBridge: SpringEventPublisherBridge = mockk(relaxUnitFun = true)
    val artifactMetadataService: ArtifactMetadataService = mockk(relaxUnitFun = true)
    val deliveryConfig = deliveryConfig()
    val debianArtifact = DebianArtifact(
      name = "fnord",
      deliveryConfigName = deliveryConfig.name,
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
      statuses = setOf(SNAPSHOT)
    )
    val versions = listOf("2.0.0-h120.608bd90", "2.1.0-h130.18ed1dc")
    val latestArtifact = PublishedArtifact(
      name = debianArtifact.name,
      type = debianArtifact.type,
      reference = debianArtifact.reference,
      version = "${debianArtifact.name}-${versions.last()}",
      metadata = mapOf("releaseStatus" to SNAPSHOT, "buildNumber" to "1", "commitId" to "a15p0")
    )

    val artifactWithInvalidVersion = PublishedArtifact(
      name = debianArtifact.name,
      type = debianArtifact.type,
      reference = debianArtifact.reference,
      version = "etcd-3.4.10-hlocal.856d0b1",
      metadata = mapOf("releaseStatus" to SNAPSHOT, "buildNumber" to "1", "commitId" to "a15p0")
    )

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
        number = "1"
      ),
      GitMetadata(
        commit = "a15p0",
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
          sha = "a15p0",
          message = "this is a commit message",
          link = ""
        ),
        project = "spkr",
        branch = "master"
      )
    )
    val debianArtifactSupplier = DebianArtifactSupplier(eventBridge, artifactService, artifactMetadataService)
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    context("DebianArtifactSupplier") {
      before {
        every {
          artifactService.getVersions(debianArtifact.name, listOf(SNAPSHOT.name), DEBIAN)
        } returns versions

        every {
          artifactService.getArtifact(debianArtifact.name, versions.last(), DEBIAN)
        } returns latestArtifact

        every {
          artifactMetadataService.getArtifactMetadata("1", "a15p0")
        } returns artifactMetadata
      }

      test("supports Debian artifacts") {
        expectThat(debianArtifactSupplier.supportedArtifact).isEqualTo(
          SupportedArtifact(DEBIAN, DebianArtifact::class.java)
        )
      }

      test("supports Debian versioning strategy") {
        expectThat(debianArtifactSupplier.supportedVersioningStrategy)
          .isEqualTo(
            SupportedVersioningStrategy(DEBIAN, DebianVersioningStrategy::class.java)
          )
      }

      test("looks up latest artifact from igor") {
        val result = runBlocking {
          debianArtifactSupplier.getLatestArtifact(deliveryConfig, debianArtifact)
        }
        expectThat(result).isEqualTo(latestArtifact)
        verify(exactly = 1) {
          artifactService.getVersions(debianArtifact.name, listOf(SNAPSHOT.name), DEBIAN)
          artifactService.getArtifact(debianArtifact.name, versions.last(), DEBIAN)
        }
      }

      test("returns git metadata based on frigga parser") {
        val gitMeta = GitMetadata(commit = AppVersion.parseName(latestArtifact.version)!!.commit)
        expectThat(debianArtifactSupplier.parseDefaultGitMetadata(latestArtifact, debianArtifact.versioningStrategy))
          .isEqualTo(gitMeta)
      }

      test("returns build metadata based on frigga parser") {
        val buildMeta = BuildMetadata(id = AppVersion.parseName(latestArtifact.version)!!.buildNumber.toInt())
        expectThat(debianArtifactSupplier.parseDefaultBuildMetadata(latestArtifact, debianArtifact.versioningStrategy))
          .isEqualTo(buildMeta)
      }

      test("frigga parser can't parse this version") {
        expectThat(debianArtifactSupplier.parseDefaultBuildMetadata(artifactWithInvalidVersion, debianArtifact.versioningStrategy))
          .isNull()
      }

      test("returns artifact metadata based on ci provider") {
        val results = runBlocking {
          debianArtifactSupplier.getArtifactMetadata(latestArtifact)
        }
        expectThat(results)
          .isEqualTo(artifactMetadata)
      }
    }
  }
}
