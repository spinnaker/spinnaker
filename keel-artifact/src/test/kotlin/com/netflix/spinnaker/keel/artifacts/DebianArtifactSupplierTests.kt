package com.netflix.spinnaker.keel.artifacts

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.SNAPSHOT
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.api.plugins.SupportedVersioningStrategy
import com.netflix.spinnaker.keel.api.support.SpringEventPublisherBridge
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.test.deliveryConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery as every
import io.mockk.coVerify as verify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
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
    val debianArtifactSupplier = DebianArtifactSupplier(eventBridge, artifactService)
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
      }

      test("supports Debian artifacts") {
        expectThat(debianArtifactSupplier.supportedArtifact).isEqualTo(
          SupportedArtifact(DEBIAN, DebianArtifact::class.java)
        )
      }

      test("supports Debian semver versioning strategy") {
        expectThat(debianArtifactSupplier.supportedVersioningStrategy)
          .isEqualTo(
            SupportedVersioningStrategy(DEBIAN, NetflixSemVerVersioningStrategy::class.java)
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

      test("returns full version string in the form {name}-{version}") {
        expectThat(debianArtifactSupplier.getFullVersionString(latestArtifact))
          .isEqualTo("${latestArtifact.name}-${latestArtifact.version}")
      }

      test("returns release status based on artifact metadata") {
        expectThat(debianArtifactSupplier.getReleaseStatus(latestArtifact))
          .isEqualTo(SNAPSHOT)
      }

      test("returns git metadata based on frigga parser") {
        val gitMeta = GitMetadata(commit = AppVersion.parseName(latestArtifact.version)!!.commit)
        expectThat(debianArtifactSupplier.getGitMetadata(latestArtifact, debianArtifact.versioningStrategy))
          .isEqualTo(gitMeta)
      }

      test("returns build metadata based on frigga parser") {
        val buildMeta = BuildMetadata(id = AppVersion.parseName(latestArtifact.version)!!.buildNumber.toInt())
        expectThat(debianArtifactSupplier.getBuildMetadata(latestArtifact, debianArtifact.versioningStrategy))
          .isEqualTo(buildMeta)
      }
    }
  }
}
