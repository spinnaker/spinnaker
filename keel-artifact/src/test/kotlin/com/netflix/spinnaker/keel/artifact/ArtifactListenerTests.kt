package com.netflix.spinnaker.keel.artifact

import com.netflix.spinnaker.igor.ArtifactService
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
          artifacts = listOf(
            Artifact(
              "DEB",
              false,
              "fnord",
              "0.156.0-h58.f67fe09",
              null,
              "debian-local:pool/f/fnord/fnord_0.156.0-h58.f67fe09_all.deb",
              mapOf(),
              null,
              "https://my.jenkins.master/jobs/fnord-release/58",
              null
            )
          ),
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

        listener.onArtifactEvent(event)
      }

      test("the event is ignored") {
        verify(exactly = 0) { repository.store(any(), any()) }
      }

      test("no telemetry is recorded") {
        verify { publisher wasNot Called }
      }
    }

    context("the artifact is registered") {
      before {
        every { repository.isRegistered(artifact.name, artifact.type) } returns true
      }

      context("the version was already known") {
        before {
          every { repository.store(any(), any()) } returns false

          listener.onArtifactEvent(event)
        }

        test("no telemetry is recorded") {
          verify { publisher wasNot Called }
        }
      }

      context("the version is new") {
        before {
          every { repository.store(any(), any()) } returns true

          listener.onArtifactEvent(event)
        }

        test("a new artifact version is stored") {
          verify {
            repository.store(artifact, "fnord-0.156.0-h58.f67fe09")
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
        listener.onArtifactRegisteredEvent(event)
      }

      test("nothing is done") {
        verify(exactly = 0) { repository.store(any(), any()) }
      }
    }

    context("the artifact is not already registered") {
      before {
        every { repository.isRegistered("fnord", DEB) } returns false
      }

      context("there are versions of the artifact") {
        before {
          every { repository.store(any(), any()) } returns false
          coEvery { artifactService.getVersions("fnord") } returns
            listOf(
              "0.227.0-h141.bd97556",
              "0.226.0-h140.705469b",
              "0.225.0-h139.f5c2ec7",
              "0.224.0-h138.0320b6c"
            )

          listener.onArtifactRegisteredEvent(event)
        }

        test("the newest version is saved") {
          verify(exactly = 1) {
            repository.store(DeliveryArtifact("fnord", DEB), "fnord-0.227.0-h141.bd97556")
          }
        }
      }

      context("there no versions of the artifact") {
        before {
          coEvery { artifactService.getVersions("fnord") } returns listOf()

          listener.onArtifactRegisteredEvent(event)
        }

        test("no versions are persisted") {
          verify(exactly = 0) {
            repository.store(any(), any())
          }
        }
      }
    }
  }
}
