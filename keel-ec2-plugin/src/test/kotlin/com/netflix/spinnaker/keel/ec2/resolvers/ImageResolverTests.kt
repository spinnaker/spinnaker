package com.netflix.spinnaker.keel.ec2.resolvers

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.ArtifactStatus.FINAL
import com.netflix.spinnaker.keel.api.ArtifactStatus.SNAPSHOT
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Locations
import com.netflix.spinnaker.keel.api.NoImageFound
import com.netflix.spinnaker.keel.api.NoImageFoundForRegions
import com.netflix.spinnaker.keel.api.NoImageSatisfiesConstraints
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec.ServerGroupSpec
import com.netflix.spinnaker.keel.api.ec2.ImageProvider
import com.netflix.spinnaker.keel.api.ec2.get
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.failed
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.propertiesAreEqualTo

internal class ImageResolverTests : JUnit5Minutests {

  data class Fixture<T : ImageProvider>(
    val imageProvider: T?,
    val imageRegion: String = "ap-south-1",
    val resourceRegion: String = imageRegion
  ) {
    private val account = "test"
    val version1 = "1.0.0-123456"
    val version2 = "1.1.0-123456"
    val version3 = "1.2.0-123456"
    private val dynamicConfigService = mockk<DynamicConfigService>() {
      every {
        getConfig(String::class.java, "images.default-account", any())
      } returns account
    }
    val cloudDriverService = mockk<CloudDriverService>()
    val deliveryConfigRepository = InMemoryDeliveryConfigRepository()
    val artifactRepository = InMemoryArtifactRepository()
    val imageService = mockk<ImageService>()
    private val subject = ImageResolver(
      dynamicConfigService,
      deliveryConfigRepository,
      artifactRepository,
      imageService
    )
    val images = listOf(
      NamedImage(
        imageName = "fnord-$version1",
        attributes = mapOf(
          "creationDate" to "2019-07-28T13:01:00.000Z"
        ),
        tagsByImageId = mapOf(
          "ami-1" to mapOf("appversion" to "fnord-$version1")
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
          "ami-2" to mapOf("appversion" to "fnord-$version2")
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
          "ami-3" to mapOf("appversion" to "fnord-$version3")
        ),
        accounts = setOf(account),
        amis = mapOf(
          imageRegion to listOf("ami-3")
        )
      )
    )

    val artifact = DeliveryArtifact("fnord", DEB)

    val resource = resource(
      apiVersion = SPINNAKER_API_V1.subApi("ec2"),
      kind = "cluster",
      spec = ClusterSpec(
        moniker = Moniker("fnord"),
        imageProvider = imageProvider,
        locations = Locations(
          accountName = account,
          vpcName = "vpc0",
          regions = setOf(
            SubnetAwareRegionSpec(
              region = resourceRegion,
              subnet = "internal (vpc0)",
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
      "my-manifest",
      "fnord",
      setOf(artifact),
      setOf(
        Environment("test", setOf(resource))
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
      fixture {
        Fixture(
          ArtifactImageProvider(DeliveryArtifact("fnord", DEB), listOf(FINAL))
        )
      }

      context("the resource is not in an environment") {
        before {
          coEvery {
            imageService.getLatestNamedImage(artifact.name, any(), null)
          } answers {
            images.lastOrNull { it.appVersion.startsWith(firstArg<String>()) }
          }
        }

        test("returns the most recent version of the artifact") {
          val resolved = resolve()
          expectThat(resolved.spec.overrides[imageRegion]?.launchConfiguration?.image)
            .isNotNull()
            .and {
              get { appVersion }.isEqualTo("fnord-$version3")
              get { id }.isEqualTo("ami-3")
            }
        }
      }

      context("the resource is part of an environment in a delivery config manifest") {
        before {
          deliveryConfigRepository.store(deliveryConfig)
        }

        after {
          deliveryConfigRepository.dropAll()
        }

        context("a version of the artifact has been approved for the environment") {
          before {
            artifactRepository.register(artifact)
            artifactRepository.store(artifact, "${artifact.name}-$version2", FINAL)
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "${artifact.name}-$version2", "test")
            coEvery {
              imageService.getLatestNamedImage(AppVersion.parseName("${artifact.name}-$version2"), any(), null)
            } answers {
              images.lastOrNull { AppVersion.parseName(it.appVersion).version == firstArg<AppVersion>().version }
            }
          }

          after {
            artifactRepository.dropAll()
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
            artifactRepository.register(artifact)
            artifactRepository.store(artifact, "${artifact.name}-$version2", FINAL)
          }
          test("throws an exception") {
            expectCatching { resolve() }
              .failed()
              .isA<NoImageSatisfiesConstraints>()
          }
        }

        context("only an artifact with the wrong status has been approved for the environment") {
          before {
            artifactRepository.register(artifact)
            artifactRepository.store(artifact, "${artifact.name}-$version2", SNAPSHOT)
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "${artifact.name}-$version2", "test")
          }
          test("throws an exception") {
            expectCatching { resolve() }
              .failed()
              .isA<NoImageSatisfiesConstraints>()
          }
        }

        context("no image is found for the artifact version") {
          before {
            artifactRepository.register(artifact)
            artifactRepository.store(artifact, "${artifact.name}-$version2", FINAL)
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "${artifact.name}-$version2", "test")
            coEvery {
              imageService.getLatestNamedImage(AppVersion.parseName("${artifact.name}-$version2"), any(), null)
            } returns null
          }

          after {
            artifactRepository.dropAll()
          }

          test("throws an exception") {
            expectCatching { resolve() }
              .failed()
              .isA<NoImageFound>()
          }
        }

        context("no image is found for the artifact in one of the desired region") {
          deriveFixture {
            copy(resourceRegion = "cn-north-1")
          }

          // TODO: because it's a derived fixture we have to do this again, ugh
          before {
            deliveryConfigRepository.store(deliveryConfig)
          }

          after {
            deliveryConfigRepository.dropAll()
          }

          before {
            artifactRepository.register(artifact)
            artifactRepository.store(artifact, "${artifact.name}-$version2", FINAL)
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "${artifact.name}-$version2", "test")
            coEvery {
              imageService.getLatestNamedImage(AppVersion.parseName("${artifact.name}-$version2"), any(), null)
            } answers {
              images.lastOrNull { AppVersion.parseName(it.appVersion).version == firstArg<AppVersion>().version }
            }
          }

          after {
            artifactRepository.dropAll()
          }

          test("throws an exception") {
            expectCatching { resolve() }
              .failed()
              .isA<NoImageFoundForRegions>()
          }
        }
      }
    }
  }
}
