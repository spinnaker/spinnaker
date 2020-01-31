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

import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.VirtualMachineImage
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
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

    val artifact = DebianArtifact(name = "fnord")

    val nonArtifactCluster = resource(
      apiVersion = SPINNAKER_EC2_API_V1,
      kind = "cluster",
      spec = ClusterSpec(
        moniker = Moniker("fnord", "api"),
        locations = SubnetAwareLocations(
          account = "test",
          vpc = "vpc0",
          subnet = "internal (vpc0)",
          regions = setOf(
            SubnetAwareRegionSpec(
              name = "ap-south-1",
              availabilityZones = setOf("ap-south1-a", "ap-south1-b", "ap-south1-c")
            )
          )
        ),
        _defaults = ServerGroupSpec(
          launchConfiguration = LaunchConfigurationSpec(
            image = VirtualMachineImage(
              id = imageId,
              appVersion = "fnord-0.477.0-h623.787afd7",
              baseImageVersion = "nflx-base-5.308.0-h1044.b4b3f78"
            ),
            instanceType = "m4.2xlarge",
            ebsOptimized = true,
            iamRole = "fnordInstanceProfile",
            keyPair = "fnordKeyPair"
          )
        )
      )
    )

    val artifactCluster = resource(
      apiVersion = SPINNAKER_EC2_API_V1,
      kind = "cluster",
      spec = ClusterSpec(
        moniker = Moniker("fnord", "api"),
        imageProvider = ArtifactImageProvider(deliveryArtifact = artifact),
        locations = SubnetAwareLocations(
          account = "test",
          vpc = "vpc0",
          subnet = "internal (vpc0)",
          regions = setOf(
            SubnetAwareRegionSpec(
              name = "ap-south-1",
              availabilityZones = setOf("ap-south1-a", "ap-south1-b", "ap-south1-c")
            )
          )
        ),
        _defaults = ServerGroupSpec(
          launchConfiguration = LaunchConfigurationSpec(
            instanceType = "m4.2xlarge",
            ebsOptimized = true,
            iamRole = "fnordInstanceProfile",
            keyPair = "fnordKeyPair"
          )
        )
      )
    )

    val testEnv = Environment(name = "test", resources = setOf(artifactCluster))

    val deliveryConfig = DeliveryConfig(
      name = "manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = setOf(testEnv)
    )

    val deliveryConfigRepository = InMemoryDeliveryConfigRepository()
    val resourceRepository = InMemoryResourceRepository()
    val artifactRepository = mockk<ArtifactRepository>(relaxUnitFun = true)

    val subject = CurrentlyDeployedImageApprover(
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
        val event = ArtifactVersionDeployed(nonArtifactCluster.id, appVersion)
        subject.onArtifactVersionDeployed(event)
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

        val event = ArtifactVersionDeployed(artifactCluster.id, appVersion)
        subject.onArtifactVersionDeployed(event)
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

        val event = ArtifactVersionDeployed(artifactCluster.id, appVersion)
        subject.onArtifactVersionDeployed(event)
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

        val event = ArtifactVersionDeployed(artifactCluster.id, appVersion)
        subject.onArtifactVersionDeployed(event)
      }

      test("does nothing because version wasn't approved for that env yet") {
        verify(exactly = 0) { artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, appVersion, testEnv.name) }
      }
    }
  }
}
