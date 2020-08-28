package com.netflix.spinnaker.keel.artifacts

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.events.ArtifactVersionDeploying
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import io.mockk.verify

internal class ArtifactDeployingListenerTests : JUnit5Minutests {
  class Fixture {
    val resource = resource()
    val resourceSpy: Resource<DummyResourceSpec> = spyk(resource)
    val config = deliveryConfig(resource = resourceSpy)
    val artifact = config.artifacts.first()
    val repository = mockk<KeelRepository>(relaxUnitFun = true)

    val event = ArtifactVersionDeploying(
      resourceId = resourceSpy.id,
      artifactVersion = "1.1.1"
    )

    val subject = ArtifactDeployingListener(repository)
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    before {
      every { repository.getResource(resource.id) } returns resourceSpy
      every { repository.deliveryConfigFor(resource.id) } returns config
      every { repository.environmentFor(resource.id) } returns config.environments.first()
    }

    context("no artifact associated with the resource") {
      test("nothing is marked deploying") {
        subject.onArtifactVersionDeploying(event)
        verify(exactly = 0) { repository.isApprovedFor(config, any(), event.artifactVersion, any()) }
        verify(exactly = 0) { repository.markAsDeployingTo(config, any(), event.artifactVersion, any()) }
      }
    }

    context("an artifact is associated with a resource") {
      before {
        every { resourceSpy.findAssociatedArtifact(config) } returns artifact
      }
      context("artifact is approved for env") {
        before {
          every { repository.isApprovedFor(any(), any(), event.artifactVersion, any()) } returns true
        }

        test("version is marked deploying") {
          subject.onArtifactVersionDeploying(event)
          verify(exactly = 1) { repository.markAsDeployingTo(any(), any(), event.artifactVersion, any()) }
        }
      }

      context("artifact is not approved for env") {
        before {
          every { repository.isApprovedFor(any(), any(), event.artifactVersion, any()) } returns false
        }
        test("nothing is marked as deploying") {
          subject.onArtifactVersionDeploying(event)
          verify(exactly = 0) { repository.markAsDeployingTo(config, any(), event.artifactVersion, any()) }
        }
      }
    }
  }
}
