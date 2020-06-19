package com.netflix.spinnaker.keel.artifact

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.SNAPSHOT
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedVersioningStrategy
import com.netflix.spinnaker.keel.api.support.SpringEventPublisherBridge
import com.netflix.spinnaker.keel.artifacts.DEBIAN
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DebianSemVerVersioningStrategy
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.test.deliveryConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery as every
import io.mockk.coVerify as verify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo

internal class DebianArtifactSupplierTests : JUnit5Minutests {
  object Fixture {
    val artifactService: ArtifactService = mockk(relaxUnitFun = true)
    val clouddriverService: CloudDriverService = mockk(relaxUnitFun = true)
    val eventBridge: SpringEventPublisherBridge = mockk(relaxUnitFun = true)
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
      metadata = mapOf("releaseStatus" to SNAPSHOT)
    )
    val debianArtifactPublisher = DebianArtifactSupplier(eventBridge, artifactService)
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    context("DebianArtifactPublisher") {
      before {
        every {
          artifactService.getVersions(debianArtifact.name)
        } returns versions
        every {
          artifactService.getArtifact(debianArtifact.name, versions.last())
        } returns latestArtifact
      }

      test("supports Debian artifacts") {
        expectThat(debianArtifactPublisher.supportedArtifact).isEqualTo(
          SupportedArtifact(DEBIAN, DebianArtifact::class.java)
        )
      }

      test("supports Debian semver versioning strategy") {
        expectThat(debianArtifactPublisher.supportedVersioningStrategies)
          .hasSize(1)
          .containsExactly(
            SupportedVersioningStrategy(DEBIAN, DebianSemVerVersioningStrategy::class.java)
          )
      }

      test("looks up latest artifact from igor") {
        val result = runBlocking {
          debianArtifactPublisher.getLatestArtifact(deliveryConfig, debianArtifact)
        }
        expectThat(result).isEqualTo(latestArtifact)
        verify(exactly = 1) {
          artifactService.getVersions(debianArtifact.name)
          artifactService.getArtifact(debianArtifact.name, versions.last())
        }
      }

      test("returns full version string in the form {name}-{version}") {
        expectThat(debianArtifactPublisher.getFullVersionString(latestArtifact))
          .isEqualTo("${latestArtifact.name}-${latestArtifact.version}")
      }

      test("returns release status based on artifact metadata") {
        expectThat(debianArtifactPublisher.getReleaseStatus(latestArtifact))
          .isEqualTo(SNAPSHOT)
      }

      test("returns git metadata based on frigga parser") {
        val gitMeta = GitMetadata(commit = AppVersion.parseName(latestArtifact.version)!!.commit)
        expectThat(debianArtifactPublisher.getGitMetadata(latestArtifact, debianArtifact.versioningStrategy))
          .isEqualTo(gitMeta)
      }

      test("returns build metadata based on frigga parser") {
        val buildMeta = BuildMetadata(id = AppVersion.parseName(latestArtifact.version)!!.buildNumber.toInt())
        expectThat(debianArtifactPublisher.getBuildMetadata(latestArtifact, debianArtifact.versioningStrategy))
          .isEqualTo(buildMeta)
      }
    }
  }
}
