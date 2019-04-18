package com.netflix.spinnaker.keel.artifact

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifactVersion
import com.netflix.spinnaker.keel.events.ArtifactEvent
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.net.URI

internal class ArtifactListenerTests : JUnit5Minutests {
  data class Fixture(
    val event: ArtifactEvent,
    val artifact: DeliveryArtifact,
    val repository: ArtifactRepository = mockk(relaxUnitFun = true)
  ) {
    val listener: ArtifactListener = ArtifactListener(repository)
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
              "https://my.jenkins.master/job/fnord-release/58",
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
        every { repository.get(any(), any()) } returns null

        listener.onArtifactEvent(event)
      }

      test("the event is ignored") {
        verify(exactly = 0) { repository.store(any<DeliveryArtifactVersion>()) }
      }
    }

    context("the artifact is registered") {
      before {
        every { repository.get(artifact.name, artifact.type) } returns artifact

        listener.onArtifactEvent(event)
      }

      test("a new artifact version is stored") {
        verify {
          repository.store(
            DeliveryArtifactVersion(
              artifact,
              event.artifacts.first().version,
              event.artifacts.first().provenance.let(::URI)
            )
          )
        }
      }
    }
  }
}
