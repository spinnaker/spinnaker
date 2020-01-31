package com.netflix.spinnaker.keel.titus.image

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DockerArtifact
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.titus.SPINNAKER_TITUS_API_V1
import com.netflix.spinnaker.keel.api.titus.cluster.TitusClusterSpec
import com.netflix.spinnaker.keel.api.titus.cluster.TitusServerGroupSpec
import com.netflix.spinnaker.keel.api.titus.image.CurrentlyDeployedDockerImageApprover
import com.netflix.spinnaker.keel.docker.DigestProvider
import com.netflix.spinnaker.keel.docker.ReferenceProvider
import com.netflix.spinnaker.keel.events.ArtifactVersionDeployed
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

internal class CurrentlyDeployedDockerImageApproverTests : JUnit5Minutests {
  object Fixture {
    const val tag = "master-h12.bbb"

    val artifact = DockerArtifact(
      name = "org/myimage",
      tagVersionStrategy = BRANCH_JOB_COMMIT_BY_JOB,
      reference = "myart",
      deliveryConfigName = "manifest"
    )

    val referenceCluster = resource(
      apiVersion = SPINNAKER_TITUS_API_V1,
      kind = "cluster",
      spec = TitusClusterSpec(
        moniker = Moniker("waffles", "api"),
        locations = SimpleLocations(account = "test", regions = setOf(SimpleRegionSpec("us-east-1"))),
        _defaults = TitusServerGroupSpec(
          container = ReferenceProvider(
            reference = "myart"
          )
        )
      )
    )

    val digestCluster = resource(
      apiVersion = SPINNAKER_TITUS_API_V1,
      kind = "cluster",
      spec = TitusClusterSpec(
        moniker = Moniker("waffles", "api"),
        locations = SimpleLocations(account = "test", regions = setOf(SimpleRegionSpec("us-east-1"))),
        _defaults = TitusServerGroupSpec(
          container = DigestProvider(
            organization = artifact.organization,
            image = artifact.image,
            digest = "imadigestyup"
          )
        )
      )
    )

    val testEnv = Environment(name = "test", resources = setOf(referenceCluster))
    val deliveryConfig = DeliveryConfig(
      name = "manifest",
      application = "waffles",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = setOf(testEnv)
    )

    val deliveryConfigRepository = InMemoryDeliveryConfigRepository()
    val resourceRepository = InMemoryResourceRepository()
    val artifactRepository = mockk<ArtifactRepository>(relaxUnitFun = true)

    val subject = CurrentlyDeployedDockerImageApprover(
      artifactRepository = artifactRepository,
      resourceRepository = resourceRepository,
      deliveryConfigRepository = deliveryConfigRepository
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    after {
      deliveryConfigRepository.dropAll()
      resourceRepository.dropAll()
      clearAllMocks()
    }

    context("cluster has a digest provider") {
      before {
        deliveryConfigRepository.store(deliveryConfig)
        resourceRepository.store(digestCluster)
        val event = ArtifactVersionDeployed(digestCluster.id, tag)
        subject.onArtifactVersionDeployed(event)
      }

      test("nothing happens") {
        verify { artifactRepository wasNot Called }
      }
    }

    context("cluster has reference provider and didn't deploy the version") {

      before {
        deliveryConfigRepository.store(deliveryConfig)
        resourceRepository.store(referenceCluster)

        every { artifactRepository.isApprovedFor(deliveryConfig, artifact, tag, testEnv.name) } returns true
        every { artifactRepository.wasSuccessfullyDeployedTo(deliveryConfig, artifact, tag, testEnv.name) } returns false

        val event = ArtifactVersionDeployed(referenceCluster.id, tag)
        subject.onArtifactVersionDeployed(event)
      }

      test("running version is the latest version") {
        verify { artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, tag, testEnv.name) }
      }
    }

    context("cluster has reference provider and did deploy the version") {
      before {
        deliveryConfigRepository.store(deliveryConfig)
        resourceRepository.store(referenceCluster)

        every { artifactRepository.isApprovedFor(deliveryConfig, artifact, tag, testEnv.name) } returns true
        every { artifactRepository.wasSuccessfullyDeployedTo(deliveryConfig, artifact, tag, testEnv.name) } returns true

        val event = ArtifactVersionDeployed(referenceCluster.id, tag)
        subject.onArtifactVersionDeployed(event)
      }

      test("version was already marked as deployed") {
        verify(exactly = 0) { artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, tag, testEnv.name) }
      }
    }

    context("cluster has reference provider but version wasn't approved") {
      before {
        deliveryConfigRepository.store(deliveryConfig)
        resourceRepository.store(referenceCluster)

        every { artifactRepository.isApprovedFor(deliveryConfig, artifact, tag, testEnv.name) } returns false
        every { artifactRepository.wasSuccessfullyDeployedTo(deliveryConfig, artifact, tag, testEnv.name) } returns false

        val event = ArtifactVersionDeployed(referenceCluster.id, tag)
        subject.onArtifactVersionDeployed(event)
      }

      test("does nothing because version wasn't approved for that env yet") {
        verify(exactly = 0) { artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, tag, testEnv.name) }
      }
    }
  }
}
