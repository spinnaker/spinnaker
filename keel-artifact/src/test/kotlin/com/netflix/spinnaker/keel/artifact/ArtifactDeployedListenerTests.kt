package com.netflix.spinnaker.keel.artifact

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.events.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.combinedMockRepository
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.spyk
import io.mockk.verify

class ArtifactDeployedListenerTests : JUnit5Minutests {
  class Fixture {
    val r: Resource<DummyResourceSpec> = spyk(resource())
    val config = deliveryConfig(resource = r)
    val artifact = config.artifacts.first()
    val repository = combinedMockRepository()

    val event = ArtifactVersionDeployed(
      resourceId = r.id,
      artifactVersion = "1.1.1"
    )

    val subject = ArtifactDeployedListener(repository)
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    before {
      every { repository.getResource(r.id) } returns r
      every { repository.deliveryConfigFor(r.id) } returns config
      every { repository.environmentFor(r.id) } returns config.environments.first()
    }

    context("no artifact associated with the resource") {
      test("nothing is marked as deployed") {
        subject.onArtifactVersionDeployed(event)
        verify(exactly = 0) { repository.isApprovedFor(config, any(), event.artifactVersion, any()) }
        verify(exactly = 0) { repository.markAsSuccessfullyDeployedTo(config, any(), event.artifactVersion, any()) }
      }
    }

    context("an artifact is associated with a resource") {
      before {
        every { r.findAssociatedArtifact(config) } returns artifact
      }
      context("artifact is approved for env") {
        before {
          every { repository.isApprovedFor(any(), any(), event.artifactVersion, any()) } returns true
        }

        test("version is marked as deployed") {
          subject.onArtifactVersionDeployed(event)
          verify(exactly = 1) { repository.markAsSuccessfullyDeployedTo(any(), any(), event.artifactVersion, any()) }
        }
      }

      context("artifact is not approved for env") {
        before {
          every { repository.isApprovedFor(any(), any(), event.artifactVersion, any()) } returns false
        }
        test("nothing is marked as deployed") {
          subject.onArtifactVersionDeployed(event)
          verify(exactly = 0) { repository.markAsDeployingTo(config, any(), event.artifactVersion, any()) }
        }
      }
    }
  }
}
