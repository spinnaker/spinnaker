/*
 *
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package com.netflix.spinnaker.keel.ec2.image

import com.netflix.spinnaker.keel.api.ArtifactType
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.cluster.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.cluster.Location
import com.netflix.spinnaker.keel.api.ec2.image.ArtifactAlreadyDeployedEvent
import com.netflix.spinnaker.keel.api.ec2.image.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.image.CurrentlyDeployedImageApprover
import com.netflix.spinnaker.keel.api.ec2.image.IdImageProvider
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.ec2.actuation.ArtifactPromotionListenerTests
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify

internal class CurrentlyDeployedImageApproverTests : JUnit5Minutests {

  object Fixture {
    const val appVersion = "fnord-0.214.0-h127.4a44d09"
    const val imageId = "ami-0662ddfa402bb25d9"
    val ami = NamedImage(
      "$appVersion-x86_64-20190730165302-xenial-hvm-sriov-ebs",
      mapOf(
        "virtualizationType" to "hvm",
        "creationDate" to "2019-07-30T16:57:37.000Z"
      ),
      mapOf(
        imageId to mapOf(
          "appversion" to "$appVersion/JENKINS-rocket-package-fnord/127"
        )
      ),
      setOf("test"),
      mapOf("ap-south-1" to listOf(imageId))
    )

    val artifact = DeliveryArtifact(
      name = "fnord",
      type = ArtifactType.DEB
    )

    val nonArtifactCluster = resource(
      apiVersion = SPINNAKER_API_V1.subApi("ec2"),
      kind = "cluster",
      spec = ClusterSpec(
        moniker = Moniker("fnord", "api"),
        location = Location("test", "ap-south-1", "internal (vpc0)", setOf("ap-south1-a", "ap-south1-b", "ap-south1-c")),
        launchConfiguration = LaunchConfigurationSpec(
          imageProvider = IdImageProvider(imageId = imageId),
          instanceType = "m4.2xlarge",
          ebsOptimized = true,
          iamRole = "fnordInstanceProfile",
          keyPair = "fnordKeyPair"
        )
      )
    )

    val artifactCluster = resource(
      apiVersion = SPINNAKER_API_V1.subApi("ec2"),
      kind = "cluster",
      spec = ClusterSpec(
        moniker = Moniker("fnord", "api"),
        location = Location("test", "ap-south-1", "internal (vpc0)", setOf("ap-south1-a", "ap-south1-b", "ap-south1-c")),
        launchConfiguration = LaunchConfigurationSpec(
          imageProvider = ArtifactImageProvider(deliveryArtifact = artifact),
          instanceType = "m4.2xlarge",
          ebsOptimized = true,
          iamRole = "fnordInstanceProfile",
          keyPair = "fnordKeyPair"
        )
      )
    )

    val testEnv = Environment(name = "test", resources = setOf(artifactCluster))

    val deliveryConfig = DeliveryConfig(
      name = "manifest",
      application = "fnord",
      artifacts = setOf(artifact),
      environments = setOf(testEnv)
    )

    val deliveryConfigRepository = InMemoryDeliveryConfigRepository()
    val resourceRepository = InMemoryResourceRepository()
    val artifactRepository = mockk<ArtifactRepository>(relaxUnitFun = true)
    val cloudDriverService = mockk<CloudDriverService> {
      coEvery { namedImages(ArtifactPromotionListenerTests.Fixture.imageId, "test", "ap-south-1") } returns listOf(ArtifactPromotionListenerTests.Fixture.ami)
    }

    val subject = CurrentlyDeployedImageApprover(
      cloudDriverService = cloudDriverService,
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

    context("cluster does not have an artifact image provider") {
      before {
        deliveryConfigRepository.store(deliveryConfig)
        resourceRepository.store(nonArtifactCluster)
        val event = ArtifactAlreadyDeployedEvent(nonArtifactCluster.id.toString(), imageId)
        subject.artifactAlreadyDeployedEventHandler(event)
      }

      test("nothing happens") {
        verify { artifactRepository wasNot Called }
      }
    }

    context("cluster has artifact image provider and didn't deploy the version") {

      before {
        deliveryConfigRepository.store(deliveryConfig)
        resourceRepository.store(artifactCluster)

        every { artifactRepository.isApprovedFor(deliveryConfig, artifact, appVersion, testEnv.name) } returns true
        every { artifactRepository.wasSuccessfullyDeployedTo(deliveryConfig, artifact, appVersion, testEnv.name) } returns false
        coEvery { cloudDriverService.namedImages(any(), imageId, null) } returns listOf(ami)

        val event = ArtifactAlreadyDeployedEvent(artifactCluster.id.toString(), imageId)
        subject.artifactAlreadyDeployedEventHandler(event)
      }

      test("running version is the latest version") {
        verify { artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, appVersion, testEnv.name) }
      }
    }

    context("cluster has artifact image provider and did deploy the version") {
      before {
        deliveryConfigRepository.store(deliveryConfig)
        resourceRepository.store(artifactCluster)

        every { artifactRepository.isApprovedFor(deliveryConfig, artifact, appVersion, testEnv.name) } returns true
        every { artifactRepository.wasSuccessfullyDeployedTo(deliveryConfig, artifact, appVersion, testEnv.name) } returns true
        coEvery { cloudDriverService.namedImages(any(), imageId, null) } returns listOf(ami)

        val event = ArtifactAlreadyDeployedEvent(artifactCluster.id.toString(), imageId)
        subject.artifactAlreadyDeployedEventHandler(event)
      }

      test("version was already marked as deployed") {
        verify(exactly = 0) { artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, appVersion, testEnv.name) }
      }
    }

    context("cluster has artifact image provider but version wasn't approved") {
      before {
        deliveryConfigRepository.store(deliveryConfig)
        resourceRepository.store(artifactCluster)

        every { artifactRepository.isApprovedFor(deliveryConfig, artifact, appVersion, testEnv.name) } returns false
        every { artifactRepository.wasSuccessfullyDeployedTo(deliveryConfig, artifact, appVersion, testEnv.name) } returns false
        coEvery { cloudDriverService.namedImages(any(), imageId, null) } returns listOf(ami)

        val event = ArtifactAlreadyDeployedEvent(artifactCluster.id.toString(), imageId)
        subject.artifactAlreadyDeployedEventHandler(event)
      }

      test("does nothing because version wasn't approved for that env yet") {
        verify(exactly = 0) { artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, appVersion, testEnv.name) }
      }
    }
  }
}
