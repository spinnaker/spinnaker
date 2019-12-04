package com.netflix.spinnaker.keel.artifact

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.ArtifactStatus.FINAL
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
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
  val korkArtifact = Artifact.builder()
    .type("DEB")
    .customKind(false)
    .name("fnord")
    .version("0.156.0-h58.f67fe09")
    .reference("debian-local:pool/f/fnord/fnord_0.156.0-h58.f67fe09_all.deb")
    .metadata(mapOf("releaseStatus" to FINAL))
    .provenance("https://my.jenkins.master/jobs/fnord-release/58")
    .build()

  data class ArtifactFixture(
    val event: ArtifactEvent,
    val artifact: DeliveryArtifact,
    val repository: ArtifactRepository = mockk(relaxUnitFun = true),
    val artifactService: ArtifactService = mockk(relaxUnitFun = true),
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  ) {
    val listener: ArtifactListener = ArtifactListener(repository, artifactService, publisher)
  }

  fun artifactEventTests() = rootContext<ArtifactFixture> {
    fixture {
      ArtifactFixture(
        event = ArtifactEvent(
          artifacts = listOf(korkArtifact),
          details = emptyMap()
        ),
        artifact = DeliveryArtifact(
          name = "fnord",
          type = DEB
        )
      )
    }

    context("the artifact is not something we're tracking") {
      before {
        every { repository.isRegistered(any(), any()) } returns false
        every { repository.versions(any()) } returns listOf("0.227.0-h141.bd97556")

        listener.onArtifactEvent(event)
      }

      test("the event is ignored") {
        verify(exactly = 0) { repository.store(any(), any(), any()) }
      }

      test("no telemetry is recorded") {
        verify { publisher wasNot Called }
      }
    }

    context("the artifact is registered with versions") {
      before {
        every { repository.isRegistered(artifact.name, artifact.type) } returns true
        every { repository.versions(artifact) } returns listOf("0.227.0-h141.bd97556")
      }

      context("the version was already known") {
        before {
          every { repository.store(any(), any(), any()) } returns false

          listener.onArtifactEvent(event)
        }

        test("no telemetry is recorded") {
          verify { publisher wasNot Called }
        }
      }

      context("the version is new") {
        before {
          every { repository.store(any(), any(), any()) } returns true

          listener.onArtifactEvent(event)
        }

        test("a new artifact version is stored") {
          verify {
            repository.store(artifact, "fnord-0.156.0-h58.f67fe09", FINAL)
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
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  ) {
    val listener: ArtifactListener = ArtifactListener(repository, artifactService, publisher)
  }

  fun artifactRegisteredEventTests() = rootContext<RegisteredFixture> {
    fixture {
      RegisteredFixture(
        event = ArtifactRegisteredEvent(
          DeliveryArtifact(
            name = "fnord",
            type = DEB
          )
        ),
        artifact = DeliveryArtifact(
          name = "fnord",
          type = DEB
        )
      )
    }

    context("artifact is already registered") {
      before {
        every { repository.isRegistered("fnord", DEB) } returns true
        every { repository.versions(any()) } returns listOf("0.227.0-h141.bd97556")
        listener.onArtifactRegisteredEvent(event)
      }

      test("nothing is done") {
        verify(exactly = 0) { repository.store(any(), any(), any()) }
      }
    }

    context("the artifact is not already registered") {
      before {
        every { repository.isRegistered("fnord", DEB) } returns false
      }

      context("there are versions of the artifact") {
        before {
          every { repository.store(any(), any(), any()) } returns false
          every { repository.versions(any()) } returns emptyList()
          coEvery { artifactService.getVersions("fnord") } returns
            listOf(
              "0.227.0-h141.bd97556",
              "0.226.0-h140.705469b",
              "0.225.0-h139.f5c2ec7",
              "0.224.0-h138.0320b6c"
            )
          coEvery { artifactService.getArtifact("fnord", "0.227.0-h141.bd97556") } returns korkArtifact

          listener.onArtifactRegisteredEvent(event)
        }

        test("the newest version is saved") {
          verify(exactly = 1) {
            repository.store(DeliveryArtifact("fnord", DEB), "fnord-0.227.0-h141.bd97556", FINAL)
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

  val newerKorkArtifact = Artifact.builder()
    .type("DEB")
    .customKind(false)
    .name("fnord")
    .version("0.161.0-h61.116f116")
    .reference("debian-local:pool/f/fnord/fnord_0.161.0-h61.116f116_all.deb")
    .metadata(mapOf("releaseStatus" to FINAL))
    .provenance("https://my.jenkins.master/jobs/fnord-release/60")
    .build()

  data class SyncArtifactsFixture(
    val artifact: DeliveryArtifact,
    val repository: ArtifactRepository = mockk(relaxUnitFun = true),
    val artifactService: ArtifactService = mockk(relaxUnitFun = true),
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  ) {
    val listener: ArtifactListener = ArtifactListener(repository, artifactService, publisher)
  }

  fun syncArtifactsFixture() = rootContext<SyncArtifactsFixture> {
    fixture {
      SyncArtifactsFixture(
        artifact = DeliveryArtifact(
          name = "fnord",
          type = DEB
        )
      )
    }

    context("we don't have any versions of an artifact") {
      before {
        every { repository.getAll(DEB) } returns listOf(artifact)
        every { repository.versions(artifact) } returns listOf()
        coEvery { artifactService.getVersions(artifact.name) } returns listOf("0.161.0-h61.116f116", "0.160.0-h60.f67f671")
        coEvery { artifactService.getArtifact(artifact.name, "0.161.0-h61.116f116") } returns newerKorkArtifact
        every { repository.store(artifact, "${artifact.name}-0.161.0-h61.116f116", FINAL) } returns true
      }

      test("new version is stored") {
        listener.syncDebArtifactVersions()
        verify { repository.store(artifact, "${artifact.name}-0.161.0-h61.116f116", FINAL) }
      }
    }

    context("there is one artifact with one version stored") {
      before {
        every { repository.getAll(DEB) } returns listOf(artifact)
        every { repository.versions(artifact) } returns listOf("${artifact.name}-0.156.0-h58.f67fe09")
      }

      context("a new version") {
        before {
          coEvery { artifactService.getVersions(artifact.name) } returns listOf("0.161.0-h61.116f116", "0.160.0-h60.f67f671")
          coEvery { artifactService.getArtifact(artifact.name, "0.161.0-h61.116f116") } returns newerKorkArtifact
          every { repository.store(artifact, "${artifact.name}-0.161.0-h61.116f116", FINAL) } returns true
        }

        test("new version stored") {
          listener.syncDebArtifactVersions()
          verify { repository.store(artifact, "${artifact.name}-0.161.0-h61.116f116", FINAL) }
        }
      }

      context("no new version") {
        before {
          coEvery { artifactService.getVersions(artifact.name) } returns listOf("0.156.0-h58.f67fe09")
        }

        test("store not called") {
          listener.syncDebArtifactVersions()
          verify(exactly = 0) { repository.store(artifact, any(), FINAL) }
        }
      }

      context("no version information ") {
        before {
          coEvery { artifactService.getVersions(artifact.name) } returns listOf()
        }

        test("store not called") {
          listener.syncDebArtifactVersions()
          verify(exactly = 0) { repository.store(artifact, any(), FINAL) }
        }
      }
    }
  }
}
