package com.netflix.spinnaker.keel.artifact

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.ArtifactStatus.FINAL
import com.netflix.spinnaker.keel.api.ArtifactType.deb
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DockerArtifact
import com.netflix.spinnaker.keel.api.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.events.ArtifactEvent
import com.netflix.spinnaker.keel.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionUpdated
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

internal class ArtifactListenerTests : JUnit5Minutests {
  val korkDeb = Artifact.builder()
    .type("DEB")
    .customKind(false)
    .name("fnord")
    .version("0.156.0-h58.f67fe09")
    .reference("debian-local:pool/f/fnord/fnord_0.156.0-h58.f67fe09_all.deb")
    .metadata(mapOf("releaseStatus" to FINAL))
    .provenance("https://my.jenkins.master/jobs/fnord-release/58")
    .build()

  val debianArtifact = DebianArtifact(name = "fnord", deliveryConfigName = "fnord-config")
  val dockerArtifact = DockerArtifact(name = "fnord/myimage", tagVersionStrategy = BRANCH_JOB_COMMIT_BY_JOB, deliveryConfigName = "fnord-config")

  data class ArtifactFixture(
    val event: ArtifactEvent,
    val artifact: DeliveryArtifact,
    val repository: ArtifactRepository = mockk(relaxUnitFun = true),
    val artifactService: ArtifactService = mockk(relaxUnitFun = true),
    val clouddriverService: CloudDriverService = mockk(relaxUnitFun = true),
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  ) {
    val listener: ArtifactListener = ArtifactListener(repository, artifactService, clouddriverService, publisher)
  }

  fun artifactEventTests() = rootContext<ArtifactFixture> {
    fixture {
      ArtifactFixture(
        event = ArtifactEvent(
          artifacts = listOf(korkDeb),
          details = emptyMap()
        ),
        artifact = DebianArtifact(name = "fnord", deliveryConfigName = "fnord-config")
      )
    }

    context("the artifact is not something we're tracking") {
      before {
        every { repository.isRegistered(any(), any()) } returns false
        every { repository.versions(any()) } returns listOf("0.227.0-h141.bd97556")

        listener.onArtifactEvent(event)
      }

      test("the event is ignored") {
        verify(exactly = 0) { repository.store(any(), any(), any(), any()) }
      }

      test("no telemetry is recorded") {
        verify { publisher wasNot Called }
      }
    }

    context("the artifact is registered with versions") {
      before {
        every { repository.isRegistered(artifact.name, artifact.type) } returns true
        every { repository.get(artifact.name, artifact.type, "fnord-config") } returns listOf(artifact)
        every { repository.versions(artifact) } returns listOf("0.227.0-h141.bd97556")
      }

      context("the version was already known") {
        before {
          every { repository.store(any(), any(), any(), any()) } returns false

          listener.onArtifactEvent(event)
        }

        test("no telemetry is recorded") {
          verify { publisher wasNot Called }
        }
      }

      context("the version is new") {
        before {
          every { repository.store(any(), any(), any(), any()) } returns true

          listener.onArtifactEvent(event)
        }

        test("a new artifact version is stored") {
          verify {
            repository.store(artifact.name, artifact.type, "fnord-0.156.0-h58.f67fe09", FINAL)
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
    val artifact: DeliveryArtifact,
    val repository: ArtifactRepository = mockk(relaxUnitFun = true),
    val artifactService: ArtifactService = mockk(relaxUnitFun = true),
    val clouddriverService: CloudDriverService = mockk(relaxUnitFun = true),
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  ) {
    val listener: ArtifactListener = ArtifactListener(repository, artifactService, clouddriverService, publisher)
  }

  fun artifactRegisteredEventTests() = rootContext<RegisteredFixture> {
    fixture {
      RegisteredFixture(
        event = ArtifactRegisteredEvent(
          DebianArtifact(name = "fnord")
        ),
        artifact = DebianArtifact(name = "fnord")
      )
    }

    context("artifact is already registered") {
      before {
        every { repository.isRegistered("fnord", deb) } returns true
        every { repository.get(artifact.name, artifact.type, "fnord-config") } returns listOf(artifact)
        every { repository.versions(any()) } returns listOf("0.227.0-h141.bd97556")
        listener.onArtifactRegisteredEvent(event)
      }

      test("nothing is done") {
        verify(exactly = 0) { repository.store(any(), any(), any()) }
      }
    }

    context("the artifact is not already registered") {
      before {
        every { repository.isRegistered("fnord", deb) } returns false
      }

      context("there are versions of the artifact") {
        before {
          every { repository.store(any(), any(), any(), any()) } returns false
          every { repository.versions(any()) } returns emptyList()
          coEvery { artifactService.getVersions("fnord") } returns
            listOf(
              "0.227.0-h141.bd97556",
              "0.226.0-h140.705469b",
              "0.225.0-h139.f5c2ec7",
              "0.224.0-h138.0320b6c"
            )
          coEvery { artifactService.getArtifact("fnord", "0.227.0-h141.bd97556") } returns korkDeb

          listener.onArtifactRegisteredEvent(event)
        }

        test("the newest version is saved") {
          verify(exactly = 1) {
            repository.store("fnord", deb, "fnord-0.227.0-h141.bd97556", FINAL)
          }
        }
      }

      context("there no versions of the artifact") {
        before {
          coEvery { artifactService.getVersions("fnord") } returns listOf()
          coEvery { repository.versions(any()) } returns listOf()

          listener.onArtifactRegisteredEvent(event)
        }

        test("no versions are persisted") {
          verify(exactly = 0) {
            repository.store(any(), any(), any())
          }
        }
      }
    }
  }

  val newerKorkDeb: Artifact = Artifact.builder()
    .type("DEB")
    .customKind(false)
    .name("fnord")
    .version("0.161.0-h61.116f116")
    .reference("debian-local:pool/f/fnord/fnord_0.161.0-h61.116f116_all.deb")
    .metadata(mapOf("releaseStatus" to FINAL))
    .provenance("https://my.jenkins.master/jobs/fnord-release/60")
    .build()

  data class SyncArtifactsFixture(
    val debArtifact: DeliveryArtifact,
    val dockerArtifact: DockerArtifact,
    val repository: ArtifactRepository = mockk(relaxUnitFun = true),
    val artifactService: ArtifactService = mockk(relaxUnitFun = true),
    val clouddriverService: CloudDriverService = mockk(relaxUnitFun = true),
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  ) {
    val listener: ArtifactListener = ArtifactListener(repository, artifactService, clouddriverService, publisher)
  }

  fun syncArtifactsFixture() = rootContext<SyncArtifactsFixture> {
    fixture {
      SyncArtifactsFixture(
        debArtifact = debianArtifact,
        dockerArtifact = dockerArtifact
      )
    }

    context("we don't have any versions of an artifact") {
      before {
        every { repository.getAll() } returns listOf(debArtifact, dockerArtifact)
        every { repository.versions(debArtifact) } returns listOf()
        every { repository.versions(dockerArtifact) } returns listOf()
        coEvery { artifactService.getVersions(debArtifact.name) } returns listOf("0.161.0-h61.116f116", "0.160.0-h60.f67f671")
        coEvery { clouddriverService.findDockerTagsForImage("*", dockerArtifact.name) } returns listOf("master-h5.blahblah")
        coEvery { artifactService.getArtifact(debArtifact.name, "0.161.0-h61.116f116") } returns newerKorkDeb
        every { repository.store(debArtifact.name, debArtifact.type, "${debArtifact.name}-0.161.0-h61.116f116", FINAL) } returns true
        every { repository.store(dockerArtifact.name, dockerArtifact.type, "master-h5.blahblah", null) } returns true
      }

      test("new version is stored") {
        listener.syncArtifactVersions()
        verify { repository.store(debArtifact.name, debArtifact.type, "${debArtifact.name}-0.161.0-h61.116f116", FINAL) }
        verify { repository.store(dockerArtifact.name, dockerArtifact.type, "master-h5.blahblah", null) }
      }
    }

    context("there is one artifact with one version stored") {
      before {
        every { repository.getAll() } returns listOf(debArtifact, dockerArtifact)
        every { repository.versions(debArtifact) } returns listOf("${debArtifact.name}-0.156.0-h58.f67fe09")
        every { repository.versions(dockerArtifact) } returns listOf("master-h5.blahblah")
      }

      context("a new version") {
        before {
          coEvery { artifactService.getVersions(debArtifact.name) } returns listOf("0.161.0-h61.116f116", "0.160.0-h60.f67f671")
          coEvery { clouddriverService.findDockerTagsForImage("*", dockerArtifact.name) } returns listOf("master-h6.hehehe")
          coEvery { artifactService.getArtifact(debArtifact.name, "0.161.0-h61.116f116") } returns newerKorkDeb
          every { repository.store(debArtifact.name, debArtifact.type, "${debArtifact.name}-0.161.0-h61.116f116", FINAL) } returns true
          every { repository.store(dockerArtifact.name, dockerArtifact.type, "master-h6.hehehe", null) } returns true
        }

        test("new version stored") {
          listener.syncArtifactVersions()
          verify { repository.store(debArtifact.name, debArtifact.type, "${debArtifact.name}-0.161.0-h61.116f116", FINAL) }
          verify { repository.store(dockerArtifact.name, dockerArtifact.type, "master-h6.hehehe", null) }
        }
      }

      context("no new version") {
        before {
          coEvery { artifactService.getVersions(debArtifact.name) } returns listOf("0.156.0-h58.f67fe09")
          coEvery { clouddriverService.findDockerTagsForImage("*", dockerArtifact.name) } returns listOf("master-h5.blahblah")
        }

        test("store not called") {
          listener.syncArtifactVersions()
          verify(exactly = 0) { repository.store(debArtifact.name, debArtifact.type, any(), FINAL) }
          verify(exactly = 0) { repository.store(dockerArtifact.name, dockerArtifact.type, any(), FINAL) }
        }
      }

      context("no version information ") {
        before {
          coEvery { artifactService.getVersions(debArtifact.name) } returns listOf()
          coEvery { clouddriverService.findDockerTagsForImage("*", dockerArtifact.name) } returns listOf()
        }

        test("store not called") {
          listener.syncArtifactVersions()
          verify(exactly = 0) { repository.store(debArtifact.name, debArtifact.type, any(), FINAL) }
          verify(exactly = 0) { repository.store(dockerArtifact.name, dockerArtifact.type, any(), FINAL) }
        }
      }
    }
  }
}
