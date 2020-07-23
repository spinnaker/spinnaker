package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.RELEASE
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1
import com.netflix.spinnaker.keel.api.ec2.ImageProvider
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.ec2.NoImageFoundForRegions
import com.netflix.spinnaker.keel.ec2.NoImageSatisfiesConstraints
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery as every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isNotNull
import strikt.assertions.propertiesAreEqualTo

internal class ImageResolverTests : JUnit5Minutests {

  data class Fixture<T : ImageProvider>(
    val imageProvider: T?,
    val imageRegion: String = "ap-south-1",
    val resourceRegion: String = imageRegion
  ) {
    val artifact = DebianArtifact(
      name = "fnord",
      deliveryConfigName = "my-manifest",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf(imageRegion)),
      statuses = setOf(RELEASE)
    )
    private val account = "test"
    val version1 = "1.0.0-123456"
    val version2 = "1.1.0-123456"
    val version3 = "1.2.0-123456"
    private val dynamicConfigService = mockk<DynamicConfigService>() {
      every {
        getConfig(String::class.java, "images.default-account", any())
      } returns account
    }
    val repository = mockk<KeelRepository>()
    val imageService = mockk<ImageService>()
    private val subject = ImageResolver(
      dynamicConfigService,
      repository,
      imageService
    )
    val images = listOf(
      NamedImage(
        imageName = "fnord-$version1",
        attributes = mapOf(
          "creationDate" to "2019-07-28T13:01:00.000Z"
        ),
        tagsByImageId = mapOf(
          "ami-1" to mapOf("appversion" to "fnord-$version1", "base_ami_version" to "nflx-base-5.464.0-h1473.31178a8")
        ),
        accounts = setOf(account),
        amis = mapOf(
          imageRegion to listOf("ami-1")
        )
      ),
      NamedImage(
        imageName = "fnord-$version2",
        attributes = mapOf(
          "creationDate" to "2019-07-29T13:01:00.000Z"
        ),
        tagsByImageId = mapOf(
          "ami-2" to mapOf("appversion" to "fnord-$version2", "base_ami_version" to "nflx-base-5.464.0-h1473.31178a8")
        ),
        accounts = setOf(account),
        amis = mapOf(
          imageRegion to listOf("ami-2")
        )
      ),
      NamedImage(
        imageName = "fnord-$version3",
        attributes = mapOf(
          "creationDate" to "2019-07-30T13:01:00.000Z"
        ),
        tagsByImageId = mapOf(
          "ami-3" to mapOf("appversion" to "fnord-$version3", "base_ami_version" to "nflx-base-5.464.0-h1473.31178a8")
        ),
        accounts = setOf(account),
        amis = mapOf(
          imageRegion to listOf("ami-3")
        )
      )
    )

    val resource = resource(
      kind = EC2_CLUSTER_V1.kind,
      spec = ClusterSpec(
        moniker = Moniker("fnord"),
        imageProvider = imageProvider,
        locations = SubnetAwareLocations(
          account = account,
          vpc = "vpc0",
          subnet = "internal (vpc0)",
          regions = setOf(
            SubnetAwareRegionSpec(
              name = resourceRegion,
              availabilityZones = setOf()
            )
          )
        ),
        _defaults = ServerGroupSpec(
          launchConfiguration = LaunchConfigurationSpec(
            instanceType = "m5.large",
            ebsOptimized = true,
            iamRole = "fnordIamRole",
            keyPair = "fnordKeyPair"
          )
        )
      )
    )

    val deliveryConfig = DeliveryConfig(
      name = "my-manifest",
      application = "fnord",
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = setOf(
        Environment(name = "test", resources = setOf(resource))
      )
    )

    fun resolve(): Resource<ClusterSpec> = runBlocking {
      subject.invoke(resource)
    }
  }

  fun tests() = rootContext<Fixture<*>> {
    context("no image provider") {
      fixture { Fixture(null) }

      test("returns the original spec unchanged") {
        expectThat(resolve())
          .propertiesAreEqualTo(resource)
      }
    }

    derivedContext<Fixture<ArtifactImageProvider>>("an image derived from an artifact") {
      val artifact = DebianArtifact(name = "fnord", deliveryConfigName = "my-manifest", vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2")), statuses = setOf(RELEASE))
      fixture {
        Fixture(
          ArtifactImageProvider(artifact, listOf(RELEASE))
        )
      }

      context("the resource is part of an environment in a delivery config manifest") {
        before {
          every { repository.deliveryConfigFor(resource.id) } returns deliveryConfig
          every { repository.environmentFor(resource.id) } returns deliveryConfig.environments.first()
        }

        context("a version of the artifact has been approved for the environment") {
          before {
            every { repository.latestVersionApprovedIn(deliveryConfig, artifact, "test") } returns "${artifact.name}-$version2"
            every {
              imageService.getLatestNamedImageWithAllRegionsForAppVersion(AppVersion.parseName("${artifact.name}-$version2"), any(), listOf(resourceRegion))
            } answers {
              images.lastOrNull { AppVersion.parseName(it.appVersion).version == firstArg<AppVersion>().version }
            }
          }

          test("returns the image id of the approved version") {
            val resolved = resolve()
            expectThat(resolved.spec.overrides[imageRegion]?.launchConfiguration?.image)
              .isNotNull()
              .and {
                get { appVersion }.isEqualTo("fnord-$version2")
                get { id }.isEqualTo("ami-2") // TODO: false moniker
              }
          }
        }

        context("no artifact version has been approved for the environment") {
          before {
            every { repository.latestVersionApprovedIn(any(), any(), any()) } returns null
          }
          test("throws an exception") {
            expectCatching { resolve() }
              .isFailure()
              .isA<NoImageSatisfiesConstraints>()
          }
        }

        context("no image is found for the artifact version") {
          before {
            every { repository.latestVersionApprovedIn(deliveryConfig, artifact, "test") } returns "${artifact.name}-$version2"
            every {
              imageService.getLatestNamedImageWithAllRegionsForAppVersion(AppVersion.parseName("${artifact.name}-$version2"), any(), listOf(resourceRegion))
            } returns null
          }

          test("throws an exception") {
            expectCatching { resolve() }
              .isFailure()
              .isA<NoImageFoundForRegions>()
          }
        }

        context("no image is found for the artifact in one of the desired region") {
          deriveFixture {
            copy(resourceRegion = "cn-north-1")
          }

          // TODO: because it's a derived fixture we have to do this again, ugh
          before {
            every { repository.deliveryConfigFor(resource.id) } returns deliveryConfig
            every { repository.environmentFor(resource.id) } returns deliveryConfig.environments.first()
          }

          before {
            every { repository.latestVersionApprovedIn(deliveryConfig, artifact, "test") } returns "${artifact.name}-$version2"
            every {
              imageService.getLatestNamedImageWithAllRegionsForAppVersion(AppVersion.parseName("${artifact.name}-$version2"), any(), listOf(resourceRegion))
            } answers {
              images.lastOrNull { AppVersion.parseName(it.appVersion).version == firstArg<AppVersion>().version }
            }
          }

          test("throws an exception") {
            expectCatching { resolve() }
              .isFailure()
              .isA<NoImageFoundForRegions>()
          }
        }
      }
    }
  }
}
