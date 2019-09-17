package com.netflix.spinnaker.keel.ec2.actuation

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.cluster.ServerGroup
import com.netflix.spinnaker.keel.api.ec2.cluster.LaunchConfiguration
import com.netflix.spinnaker.keel.api.ec2.cluster.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.cluster.Location
import com.netflix.spinnaker.keel.api.ec2.image.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.image.IdImageProvider
import com.netflix.spinnaker.keel.api.ec2.securityGroup.SecurityGroup
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.persistence.ArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify

internal class ArtifactPromotionListenerTests : JUnit5Minutests {

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

    private val deliveryConfigRepository = InMemoryDeliveryConfigRepository()
    val artifactRepository = mockk<ArtifactRepository>(relaxUnitFun = true)
    private val subject = ArtifactPromotionListener(deliveryConfigRepository, artifactRepository)

    val artifact = DeliveryArtifact(
      name = "fnord",
      type = DEB
    )

    val securityGroup = resource(
      apiVersion = SPINNAKER_API_V1.subApi("ec2"),
      kind = "security-group",
      spec = SecurityGroup(
        moniker = Moniker("fnord"),
        accountName = "test",
        region = "ap-south-1",
        vpcName = "internal (vpc0)",
        description = "some security group"
      )
    )

    val nonArtifactCluster = resource(
      apiVersion = SPINNAKER_API_V1.subApi("ec2"),
      kind = "cluster",
      spec = ServerGroupSpec(
        moniker = Moniker("fnord", "api"),
        location = Location("test", "ap-south-1", "internal (vpc0)", setOf("ap-south1-a", "ap-south1-b", "ap-south1-c")),
        launchConfiguration = LaunchConfigurationSpec(
          imageProvider = IdImageProvider(
            imageId = imageId
          ),
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
      spec = ServerGroupSpec(
        moniker = Moniker("fnord", "api"),
        location = Location("test", "ap-south-1", "internal (vpc0)", setOf("ap-south1-a", "ap-south1-b", "ap-south1-c")),
        launchConfiguration = LaunchConfigurationSpec(
          imageProvider = ArtifactImageProvider(
            deliveryArtifact = artifact
          ),
          instanceType = "m4.2xlarge",
          ebsOptimized = true,
          iamRole = "fnordInstanceProfile",
          keyPair = "fnordKeyPair"
        )
      )
    )

    private fun ServerGroupSpec.toCurrent() = ServerGroup(
      moniker,
      location,
      LaunchConfiguration(
        imageId,
        appVersion,
        launchConfiguration.instanceType,
        launchConfiguration.ebsOptimized,
        launchConfiguration.iamRole,
        launchConfiguration.keyPair
      )
    )

    fun triggerEvent(resource: Resource<*>) {
      val current = when (val spec = resource.spec) {
        is SecurityGroup -> spec
        is ServerGroupSpec -> spec.toCurrent()
        else -> error("Unsupported spec type ${spec.javaClass.simpleName}")
      }
      subject.onDeltaResolved(ResourceDeltaResolved(resource, current))
    }

    val deliveryConfig = DeliveryConfig(
      name = "manifest",
      application = "fnord",
      artifacts = setOf(artifact),
      environments = setOf(
        Environment(
          name = "test",
          resources = setOf(securityGroup, nonArtifactCluster, artifactCluster)
        )
      )
    ).also {
      deliveryConfigRepository.store(it)
    }
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }
    context("delta is resolved on a non-cluster resource") {
      before {
        triggerEvent(securityGroup)
      }

      test("nothing is done with artifact promotion") {
        verify { artifactRepository wasNot Called }
      }
    }

    context("delta is resolved on a cluster that does not use an artifact") {
      before {
        triggerEvent(nonArtifactCluster)
      }

      test("nothing is done with artifact promotion") {
        verify { artifactRepository wasNot Called }
      }
    }

    context("delta is resolved on a cluster that uses an artifact") {
      before {
        triggerEvent(artifactCluster)
      }

      test("the artifact version is marked as deployed") {
        verify {
          artifactRepository.markAsSuccessfullyDeployedTo(
            deliveryConfig,
            artifact,
            appVersion,
            deliveryConfig.environments.first().name
          )
        }
      }
    }
  }
}
