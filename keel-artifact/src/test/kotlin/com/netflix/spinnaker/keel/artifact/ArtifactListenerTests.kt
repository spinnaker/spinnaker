package com.netflix.spinnaker.keel.artifact

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.events.ArtifactEvent
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.telemetry.ArtifactVersionUpdated
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.springframework.context.ApplicationEventPublisher

internal class ArtifactListenerTests : JUnit5Minutests {
  data class Fixture(
    val event: ArtifactEvent,
    val artifact: DeliveryArtifact,
    val repository: ArtifactRepository = mockk(relaxUnitFun = true),
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
  ) {
    val listener: ArtifactListener = ArtifactListener(repository, publisher)
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture(
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
            repository.store(artifact, "fnord-0.156.0-h58.f67fe09/fnord-release/58")
          }
        }

        test("a telemetry event is recorded") {
          verify { publisher.publishEvent(any<ArtifactVersionUpdated>()) }
        }
      }
    }
  }
}
