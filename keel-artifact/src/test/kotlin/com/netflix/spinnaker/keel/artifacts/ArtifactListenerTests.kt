package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.artifacts.ArtifactMetadata
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.FINAL
import com.netflix.spinnaker.keel.api.artifacts.BuildMetadata
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.GitMetadata
import com.netflix.spinnaker.keel.api.artifacts.ArtifactVersion
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.events.ArtifactPublishedEvent
import com.netflix.spinnaker.keel.api.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.api.plugins.SupportedArtifact
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionUpdated
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.mockk
import io.mockk.slot
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import io.mockk.coEvery as every
import io.mockk.coVerify as verify

internal class ArtifactListenerTests : JUnit5Minutests {
  val publishedDeb = ArtifactVersion(
    type = DEBIAN,
    customKind = false,
    name = "fnord",
    version = "0.156.0-h58.f67fe09",
    reference = "debian-local:pool/f/fnord/fnord_0.156.0-h58.f67fe09_all.deb",
    metadata = mapOf("releaseStatus" to FINAL),
    provenance = "https://my.jenkins.master/jobs/fnord-release/58"
  ).normalized()

  val newerPublishedDeb = ArtifactVersion(
    type = DEBIAN,
    customKind = false,
    name = "fnord",
    version = "0.161.0-h61.116f116",
    reference = "debian-local:pool/f/fnord/fnord_0.161.0-h61.116f116_all.deb",
    metadata = mapOf("releaseStatus" to FINAL),
    provenance = "https://my.jenkins.master/jobs/fnord-release/60"
  ).normalized()

  val publishedDocker = ArtifactVersion(
    type = DOCKER,
    customKind = false,
    name = "fnord/myimage",
    version = "master-h5.blahblah",
    reference = "fnord"
  )

  val newerPublishedDocker = publishedDocker.copy(version = "master-h6.hehehe")

  val debianArtifact = DebianArtifact(name = "fnord", deliveryConfigName = "fnord-config", vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")))
  val dockerArtifact = DockerArtifact(name = "fnord/myimage", tagVersionStrategy = BRANCH_JOB_COMMIT_BY_JOB, deliveryConfigName = "fnord-config")
  val deliveryConfig = DeliveryConfig(name = "fnord-config", application = "fnord", serviceAccount = "keel", artifacts = setOf(debianArtifact, dockerArtifact))

  val artifactMetadata = ArtifactMetadata(
    gitMetadata = GitMetadata(commit = "f00baah", author = "joesmith", branch = "master"),
    buildMetadata = BuildMetadata(id = 1, status = "SUCCEEDED")
  )

  val artifactVersion = slot<ArtifactVersion>()

  abstract class ArtifactListenerFixture {
    val repository: KeelRepository = mockk(relaxUnitFun = true)
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
    val dockerArtifactSupplier: DockerArtifactSupplier = mockk(relaxUnitFun = true)
    val debianArtifactSupplier: DebianArtifactSupplier = mockk(relaxUnitFun = true)
    val listener: ArtifactListener = ArtifactListener(repository, publisher,
      listOf(debianArtifactSupplier, dockerArtifactSupplier))
  }

  data class ArtifactPublishedFixture(
    val event: ArtifactPublishedEvent,
    val artifact: DeliveryArtifact
  ) : ArtifactListenerFixture()

  fun artifactEventTests() = rootContext<ArtifactPublishedFixture> {
    fixture {
      ArtifactPublishedFixture(
        event = ArtifactPublishedEvent(
          artifacts = listOf(publishedDeb),
          details = emptyMap()
        ),
        artifact = debianArtifact
      )
    }

    before {
      every { repository.getDeliveryConfig(any()) } returns deliveryConfig
      every { debianArtifactSupplier.supportedArtifact } returns SupportedArtifact(DEBIAN, DebianArtifact::class.java)
      every { dockerArtifactSupplier.supportedArtifact } returns SupportedArtifact(DOCKER, DockerArtifact::class.java)
    }

    context("the artifact is not something we're tracking") {
      before {
        every { repository.isRegistered(any(), any()) } returns false
        listener.onArtifactPublished(event)
      }

      test("the event is ignored") {
        verify(exactly = 0) { repository.storeArtifactVersion(any()) }
      }

      test("no telemetry is recorded") {
        verify { publisher wasNot Called }
      }
    }

    context("the artifact is registered with versions") {
      before {
        every { repository.isRegistered(artifact.name, artifact.type) } returns true
        every {
          debianArtifactSupplier.getLatestArtifact(deliveryConfig, artifact)
        } returns publishedDeb
        every {
          debianArtifactSupplier.getArtifactMetadata(publishedDeb)
        } returns artifactMetadata
      }

      context("the version was already known") {
        before {
          every { repository.storeArtifactVersion(any()) } returns false

          listener.onArtifactPublished(event)
        }

        test("no telemetry is recorded") {
          verify { publisher wasNot Called }
        }
      }

      context("the version is new") {
        before {
          every {
            debianArtifactSupplier.getArtifactMetadata(newerPublishedDeb)
          } returns artifactMetadata

          every { repository.storeArtifactVersion(any()) } returns true

          listener.onArtifactPublished(
            event.copy(artifacts = listOf(newerPublishedDeb))
          )
        }

        test("a new artifact version is stored") {
          verify {repository.storeArtifactVersion(capture(artifactVersion)) }

          with(artifactVersion.captured) {
            expectThat(name).isEqualTo(artifact.name)
            expectThat(type).isEqualTo(artifact.type)
            expectThat(version).isEqualTo(newerPublishedDeb.version)
            expectThat(status).isEqualTo(FINAL)
          }
        }

        test("artifact metadata is added before storing") {
          verify(exactly = 1) {
            debianArtifactSupplier.getArtifactMetadata(newerPublishedDeb)
          }

          with(artifactVersion.captured) {
            expectThat(gitMetadata).isEqualTo(artifactMetadata.gitMetadata)
            expectThat(buildMetadata).isEqualTo(artifactMetadata.buildMetadata)
          }
        }

        test("a telemetry event is recorded") {
          verify { publisher.publishEvent(any<ArtifactVersionUpdated>()) }
        }
      }
    }
  }

  data class RegisteredFixture(
    val event: ArtifactRegisteredEvent,
    val artifact: DeliveryArtifact
  ) : ArtifactListenerFixture()

  fun artifactRegisteredEventTests() = rootContext<RegisteredFixture> {
    fixture {
      DebianArtifact(
        name = "fnord",
        vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")),
        deliveryConfigName = "fnord-config"
      ).let {
        RegisteredFixture(
          event = ArtifactRegisteredEvent(it),
          artifact = it
        )
      }
    }

    before {
      every { repository.getDeliveryConfig(any()) } returns deliveryConfig
      every { debianArtifactSupplier.supportedArtifact } returns SupportedArtifact(DEBIAN, DebianArtifact::class.java)
      every { dockerArtifactSupplier.supportedArtifact } returns SupportedArtifact(DOCKER, DockerArtifact::class.java)
    }

    context("artifact already has saved versions") {
      before {
        every { repository.artifactVersions(event.artifact) } returns listOf(publishedDeb.version)
        listener.onArtifactRegisteredEvent(event)
      }

      test("nothing is done") {
        verify(exactly = 0) { repository.storeArtifactVersion(any()) }
      }
    }

    context("the artifact does not have any versions stored") {
      before {
        every { repository.artifactVersions(event.artifact) } returns emptyList()
      }

      context("there are available versions of the artifact") {
        before {
          every { repository.storeArtifactVersion(any()) } returns false
          every {
            debianArtifactSupplier.getLatestArtifact(deliveryConfig, artifact)
          } returns publishedDeb
          every {
            debianArtifactSupplier.getArtifactMetadata(publishedDeb)
          } returns artifactMetadata

          listener.onArtifactRegisteredEvent(event)
        }

        test("the newest version is saved") {
          verify(exactly = 1) {
            repository.storeArtifactVersion(capture(artifactVersion))
          }
          with(artifactVersion.captured) {
            expectThat(name).isEqualTo(artifact.name)
            expectThat(type).isEqualTo(artifact.type)
            expectThat(version).isEqualTo(publishedDeb.version)
            expectThat(status).isEqualTo(FINAL)
          }
        }

        test("artifact metadata is added before storing") {
          verify(exactly = 1) {
            debianArtifactSupplier.getArtifactMetadata(publishedDeb)
          }

          with(artifactVersion.captured) {
            expectThat(gitMetadata).isEqualTo(artifactMetadata.gitMetadata)
            expectThat(buildMetadata).isEqualTo(artifactMetadata.buildMetadata)
          }
        }
      }

      context("there are no versions of the artifact available") {
        before {
          every {
            debianArtifactSupplier.getLatestArtifact(deliveryConfig, artifact)
          } returns null

          listener.onArtifactRegisteredEvent(event)
        }

        test("no versions are persisted") {
          verify(exactly = 0) {
            repository.storeArtifactVersion(any())
          }
        }
      }
    }
  }

  data class SyncArtifactsFixture(
    val debArtifact: DeliveryArtifact,
    val dockerArtifact: DockerArtifact
  ) : ArtifactListenerFixture()

  fun syncArtifactsFixture() = rootContext<SyncArtifactsFixture> {
    fixture {
      SyncArtifactsFixture(
        debArtifact = debianArtifact,
        dockerArtifact = dockerArtifact
      )
    }

    before {
      every { repository.getDeliveryConfig(any()) } returns deliveryConfig
      every { debianArtifactSupplier.supportedArtifact } returns SupportedArtifact(DEBIAN, DebianArtifact::class.java)
      every { dockerArtifactSupplier.supportedArtifact } returns SupportedArtifact(DOCKER, DockerArtifact::class.java)
      every { debianArtifactSupplier.getArtifactMetadata(any()) } returns artifactMetadata
      every { dockerArtifactSupplier.getArtifactMetadata(any()) } returns artifactMetadata
      every { repository.storeArtifactVersion(any()) } returns true
      listener.onApplicationUp()
    }

    context("we don't have any versions of the artifacts") {
      before {
        every { repository.getAllArtifacts() } returns listOf(debArtifact, dockerArtifact)
        every { repository.artifactVersions(debArtifact) } returns emptyList()
        every { repository.artifactVersions(dockerArtifact) } returns emptyList()
      }

      context("versions are available") {
        before {
          every {
            debianArtifactSupplier.getLatestArtifact(deliveryConfig, debArtifact)
          } returns publishedDeb

          every {
            dockerArtifactSupplier.getLatestArtifact(deliveryConfig, dockerArtifact)
          } returns publishedDocker
        }

        test("latest versions are stored") {
          listener.syncArtifactVersions()

          val artifactVersions = mutableListOf<ArtifactVersion>()

          verify (exactly = 2) {
            repository.storeArtifactVersion(capture(artifactVersions))
          }

          with(artifactVersions[0]) {
            expectThat(name).isEqualTo(dockerArtifact.name)
            expectThat(type).isEqualTo(dockerArtifact.type)
            expectThat(version).isEqualTo(publishedDocker.version)
            expectThat(gitMetadata).isEqualTo(artifactMetadata.gitMetadata)
            expectThat(buildMetadata).isEqualTo(artifactMetadata.buildMetadata)
          }

          with(artifactVersions[1]) {
            expectThat(name).isEqualTo(debArtifact.name)
            expectThat(type).isEqualTo(debArtifact.type)
            expectThat(version).isEqualTo(publishedDeb.version)
            expectThat(status).isEqualTo(FINAL)
            expectThat(gitMetadata).isEqualTo(artifactMetadata.gitMetadata)
            expectThat(buildMetadata).isEqualTo(artifactMetadata.buildMetadata)
          }
        }
      }
    }

    context("there are artifacts with versions stored") {
      before {
        every { repository.getAllArtifacts() } returns listOf(debArtifact, dockerArtifact)
        every { repository.artifactVersions(debArtifact) } returns listOf(publishedDeb.version)
        every { repository.artifactVersions(dockerArtifact) } returns listOf(publishedDocker.version)
      }

      context("no newer versions are available") {
        before {
          every {
            debianArtifactSupplier.getLatestArtifact(deliveryConfig, debArtifact)
          } returns publishedDeb

          every {
            dockerArtifactSupplier.getLatestArtifact(deliveryConfig, dockerArtifact)
          } returns publishedDocker
        }

        test("store not called") {
          listener.syncArtifactVersions()
          verify(exactly = 0) { repository.storeArtifactVersion(any()) }
        }
      }

      context("newer versions are available") {
        before {
          every {
            debianArtifactSupplier.getLatestArtifact(deliveryConfig, debArtifact)
          } returns newerPublishedDeb

          every {
            dockerArtifactSupplier.getLatestArtifact(deliveryConfig, dockerArtifact)
          } returns newerPublishedDocker
        }

        test("new versions are stored") {
          listener.syncArtifactVersions()

          val artifactVersions = mutableListOf<ArtifactVersion>()

          verify (exactly = 2) {
            repository.storeArtifactVersion(capture(artifactVersions))
          }

          with(artifactVersions[0]) {
            expectThat(name).isEqualTo(dockerArtifact.name)
            expectThat(type).isEqualTo(dockerArtifact.type)
            expectThat(version).isEqualTo(newerPublishedDocker.version)
            expectThat(gitMetadata).isEqualTo(artifactMetadata.gitMetadata)
            expectThat(buildMetadata).isEqualTo(artifactMetadata.buildMetadata)
          }

          with(artifactVersions[1]) {
            expectThat(name).isEqualTo(debArtifact.name)
            expectThat(type).isEqualTo(debArtifact.type)
            expectThat(version).isEqualTo(newerPublishedDeb.version)
            expectThat(status).isEqualTo(FINAL)
            expectThat(gitMetadata).isEqualTo(artifactMetadata.gitMetadata)
            expectThat(buildMetadata).isEqualTo(artifactMetadata.buildMetadata)
          }
        }
      }
    }
  }
}
