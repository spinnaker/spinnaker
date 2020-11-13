package com.netflix.spinnaker.keel.artifacts

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
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
import com.netflix.spinnaker.keel.api.plugins.SupportedSortingStrategy
import com.netflix.spinnaker.keel.api.support.SpringEventPublisherBridge
import com.netflix.spinnaker.keel.services.ArtifactMetadataService
import com.netflix.spinnaker.keel.test.deliveryConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.springframework.core.env.Environment
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isFalse
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

internal class DebianArtifactSupplierTests : JUnit5Minutests {
  class Fixture {
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

    val actualVersionInPublishedArtifact = PublishedArtifact(
      name = debianArtifact.name,
      type = debianArtifact.type,
      reference = debianArtifact.reference,
      version = "0.72.0-h242.32775e4",
      metadata = mapOf("releaseStatus" to ArtifactStatus.RELEASE, "buildNumber" to "1", "commitId" to "a15p0")
    )

    val artifactWithInvalidVersion = PublishedArtifact(
      name = debianArtifact.name,
      type = debianArtifact.type,
      reference = debianArtifact.reference,
      version = "etcd-3.4.10-hlocal.856d0b1",
      metadata = mapOf("releaseStatus" to SNAPSHOT, "buildNumber" to "1", "commitId" to "a15p0")
    )

    val artifactWithoutStatus = PublishedArtifact (
      name = debianArtifact.name,
      type = debianArtifact.type,
      reference = debianArtifact.reference,
      version = "${debianArtifact.name}-${versions.last()}"
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

    val springEnv: Environment = mockk(relaxed = true)

    val debianArtifactSupplier = DebianArtifactSupplier(eventBridge, artifactService, artifactMetadataService, springEnv)
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("DebianArtifactSupplier") {
      val versionSlot = slot<String>()
      before {
        every {
          artifactService.getVersions(debianArtifact.name, listOf(SNAPSHOT.name), DEBIAN)
        } returns versions

        every {
          artifactService.getArtifact(debianArtifact.name, capture(versionSlot), DEBIAN)
        } answers {
          PublishedArtifact(
            name = debianArtifact.name,
            type = debianArtifact.type,
            reference = debianArtifact.reference,
            version = "${debianArtifact.name}-${versionSlot.captured}",
            metadata = emptyMap()
          )
        }

        every {
          artifactMetadataService.getArtifactMetadata("1", "a15p0")
        } returns artifactMetadata
      }

      test("supports Debian artifacts") {
        expectThat(debianArtifactSupplier.supportedArtifact).isEqualTo(
          SupportedArtifact(DEBIAN, DebianArtifact::class.java)
        )
      }

      test("supports Debian version sorting strategy") {
        expectThat(debianArtifactSupplier.supportedSortingStrategy)
          .isEqualTo(
            SupportedSortingStrategy(DEBIAN, DebianVersionSortingStrategy::class.java)
          )
      }

      context("retrieving latest artifact version") {
        context("with forced sorting by version") {
          before {
            every {
              springEnv.getProperty("keel.artifacts.debian.forceSortByVersion", Boolean::class.java, false)
            } returns true
          }

          test("calls igor a single time for the details of the specified version") {
            val result = runBlocking {
              debianArtifactSupplier.getLatestArtifact(deliveryConfig, debianArtifact)
            }
            expectThat(result?.version).isNotNull().isEqualTo(latestArtifact.version)
            verify(exactly = 1) {
              artifactService.getVersions(debianArtifact.name, listOf(SNAPSHOT.name), DEBIAN)
              artifactService.getArtifact(debianArtifact.name, any(), DEBIAN)
            }
          }
        }

        context("with configured sorting strategy") {
          before {
            every {
              springEnv.getProperty("keel.artifacts.debian.forceSortByVersion", Boolean::class.java, false)
            } returns false
          }

          test("calls igor multiple times for the details of all known versions") {
            val result = runBlocking {
              debianArtifactSupplier.getLatestArtifact(deliveryConfig, debianArtifact)
            }
            expectThat(result?.version).isNotNull().isEqualTo(latestArtifact.version)
            verify(exactly = 1) {
              artifactService.getVersions(debianArtifact.name, listOf(SNAPSHOT.name), DEBIAN)
            }
            verify(exactly = versions.size) {
              artifactService.getArtifact(debianArtifact.name, any(), DEBIAN)
            }
          }
        }
      }

      test("returns git metadata based on frigga parser") {
        val gitMeta = GitMetadata(commit = AppVersion.parseName(latestArtifact.version)!!.commit)
        expectThat(debianArtifactSupplier.parseDefaultGitMetadata(latestArtifact, debianArtifact.sortingStrategy))
          .isEqualTo(gitMeta)
      }

      test("returns build metadata based on frigga parser") {
        val buildMeta = BuildMetadata(id = AppVersion.parseName(latestArtifact.version)!!.buildNumber.toInt())
        expectThat(debianArtifactSupplier.parseDefaultBuildMetadata(latestArtifact, debianArtifact.sortingStrategy))
          .isEqualTo(buildMeta)
      }

      test("frigga parser can't parse this version") {
        expectThat(debianArtifactSupplier.parseDefaultBuildMetadata(artifactWithInvalidVersion, debianArtifact.sortingStrategy))
          .isNull()
      }

      test("returns artifact metadata based on ci provider") {
        val results = runBlocking {
          debianArtifactSupplier.getArtifactMetadata(latestArtifact)
        }
        expectThat(results)
          .isEqualTo(artifactMetadata)
      }

      test ("should process artifact successfully") {
        expectThat(debianArtifactSupplier.shouldProcessArtifact(latestArtifact))
          .isTrue()
      }

      test ("should process artifact successfully2") {
        expectThat(debianArtifactSupplier.shouldProcessArtifact(actualVersionInPublishedArtifact))
          .isTrue()
      }

      test ("should not process artifact with local in its version string") {
        expectThat(debianArtifactSupplier.shouldProcessArtifact(artifactWithInvalidVersion))
          .isFalse()
      }

      test ("should not process artifact without a status") {
        expectThat(debianArtifactSupplier.shouldProcessArtifact(artifactWithoutStatus))
          .isFalse()
      }
    }
  }
}
