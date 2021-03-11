package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.RELEASE
import com.netflix.spinnaker.keel.api.artifacts.BaseLabel
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.api.ec2.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.artifacts.BakedImage
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.ec2.NoArtifactVersionHasBeenApproved
import com.netflix.spinnaker.keel.ec2.NoImageFoundForRegions
import com.netflix.spinnaker.keel.persistence.BakedImageRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isFailure
import strikt.assertions.isNotNull
import strikt.java.propertiesAreEqualTo
import java.time.Instant
import io.mockk.coEvery as every

internal class ImageResolverTests : JUnit5Minutests {

  data class Fixture(
    val artifactReference: String?,
    val imageRegion: String = "ap-south-1",
    val resourceRegion: String = imageRegion
  ) {
    val artifact = DebianArtifact(
      name = "fnord",
      deliveryConfigName = "my-manifest",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf(imageRegion)),
      statuses = setOf(RELEASE),
      reference = "my-artifact"
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
    val imageService = mockk<ImageService>() {
      every { log } returns LoggerFactory.getLogger(ImageService::class.java)
    }
    val bakedImageRepository: BakedImageRepository = mockk(relaxUnitFun = true) {
      every { getByArtifactVersion(any(), any()) } returns null
    }
    private val subject = ImageResolver(
      dynamicConfigService,
      repository,
      imageService,
      bakedImageRepository
    )
    val images = listOf(
      NamedImage(
        imageName = "fnord-$version1",
        attributes = mapOf(
          "creationDate" to "2019-07-28T13:01:00.000Z"
        ),
        tagsByImageId = mapOf(
          "ami-1" to mapOf("appversion" to "fnord-$version1", "base_ami_version" to "nflx-base-5.464.0-h1473.31178a8", "base_ami_name" to "xenialbase-x86_64-202103092356-ebs")
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
          "ami-2" to mapOf("appversion" to "fnord-$version2", "base_ami_version" to "nflx-base-5.464.0-h1473.31178a8", "base_ami_name" to "xenialbase-x86_64-202103092356-ebs")
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
          "ami-3" to mapOf("appversion" to "fnord-$version3", "base_ami_version" to "nflx-base-5.464.0-h1473.31178a8", "base_ami_name" to "xenialbase-x86_64-202103092356-ebs")
        ),
        accounts = setOf(account),
        amis = mapOf(
          imageRegion to listOf("ami-3")
        )
      )
    )

    val bakedImage = BakedImage(
      name = "fnord-$version2",
      baseLabel = BaseLabel.RELEASE,
      baseOs = "bionic-classic",
      vmType = "hvm",
      cloudProvider = "aws",
      appVersion = "fnord-$version2",
      baseAmiName = "xenialbase-x86_64-201811142132-ebs",
      amiIdsByRegion = mapOf(
        imageRegion to "ami-2"
      ),
      timestamp = Instant.ofEpochMilli(1614893256845)
    )

    val resource = resource(
      kind = EC2_CLUSTER_V1_1.kind,
      spec = ClusterSpec(
        moniker = Moniker("fnord"),
        artifactReference = artifactReference,
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

  fun tests() = rootContext<Fixture> {
    context("no image provider") {
      fixture { Fixture(null) }

      test("returns the original spec unchanged") {
        expectThat(resolve())
          .propertiesAreEqualTo(resource)
      }
    }

    context("an image derived from an artifact") {
      fixture {
        Fixture("my-artifact")
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
              imageService.getLatestNamedImage(AppVersion.parseName("${artifact.name}-$version2"), any(), resourceRegion)
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
              .isA<NoArtifactVersionHasBeenApproved>()
          }
        }

        context("no image is found for the artifact version in clouddriver") {
          before {
            every { repository.latestVersionApprovedIn(deliveryConfig, artifact, "test") } returns "${artifact.name}-$version2"
            every {
              imageService.getLatestNamedImage(AppVersion.parseName("${artifact.name}-$version2"), any(), resourceRegion)
            } returns null
          }

          test("throws an exception") {
            expectCatching { resolve() }
              .isFailure()
              .isA<NoImageFoundForRegions>()
          }

          context("an image is found in our list of images") {
            before {
              every {
                bakedImageRepository.getByArtifactVersion("${artifact.name}-$version2", artifact)
              } returns bakedImage
            }

            test("returns the ami of the image") {
              val resolved = resolve()
              expectThat(resolved.spec.overrides[imageRegion]?.launchConfiguration?.image)
                .isNotNull()
                .and {
                  get { appVersion }.isEqualTo("fnord-$version2")
                  get { id }.isEqualTo("ami-2")
                }
            }
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
              imageService.getLatestNamedImage(AppVersion.parseName("${artifact.name}-$version2"), any(), any())
            } answers {
              if (thirdArg<String>() == imageRegion) {
                images.lastOrNull { AppVersion.parseName(it.appVersion).version == firstArg<AppVersion>().version }
              } else {
                null
              }
            }
          }

          test("throws an exception") {
            expectCatching { resolve() }
              .isFailure()
              .isA<NoImageFoundForRegions>()
          }

          context("all regions are found in our list of images") {
            before {
              every {
                bakedImageRepository.getByArtifactVersion("${artifact.name}-$version2", artifact)
              } returns bakedImage.copy(amiIdsByRegion = bakedImage.amiIdsByRegion + mapOf("cn-north-1" to "ami-2"))
            }

            test("returns the ami of the image") {
              val resolved = resolve()
              expectThat(resolved.spec.overrides["cn-north-1"]?.launchConfiguration?.image)
                .isNotNull()
                .and {
                  get { appVersion }.isEqualTo("fnord-$version2")
                  get { id }.isEqualTo("ami-2")
                }
            }
          }
        }
      }
    }
  }
}
