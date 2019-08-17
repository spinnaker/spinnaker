package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.NoImageFound
import com.netflix.spinnaker.keel.api.NoImageFoundForRegion
import com.netflix.spinnaker.keel.api.NoImageSatisfiesConstraints
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.cluster.LaunchConfigurationSpec
import com.netflix.spinnaker.keel.api.ec2.cluster.Location
import com.netflix.spinnaker.keel.api.ec2.image.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.image.IdImageProvider
import com.netflix.spinnaker.keel.api.ec2.image.ImageProvider
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.clouddriver.model.appVersion
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
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

internal class ImageResolverTests : JUnit5Minutests {

  data class Fixture<T : ImageProvider>(
    val imageProvider: T,
    val imageRegion: String = "ap-south-1",
    val resourceRegion: String = imageRegion
  ) {
    private val account = "test"
    private val dynamicConfigService = mockk<DynamicConfigService>() {
      every {
        getConfig(String::class.java, "images.default-account", any())
      } returns account
    }
    val deliveryConfigRepository = InMemoryDeliveryConfigRepository()
    val artifactRepository = InMemoryArtifactRepository()
    val imageService = mockk<ImageService>()
    val cloudDriverService = mockk<CloudDriverService>()
    private val subject = ImageResolver(
      dynamicConfigService,
      cloudDriverService,
      deliveryConfigRepository,
      artifactRepository,
      imageService
    )
    val images = listOf(
      NamedImage(
        imageName = "fnord-1.0.0",
        attributes = mapOf(
          "creationDate" to "2019-07-28T13:01:00.000Z"
        ),
        tagsByImageId = mapOf(
          "ami-1" to mapOf("appversion" to "fnord-1.0.0")
        ),
        accounts = setOf(account),
        amis = mapOf(
          imageRegion to listOf("ami-1")
        )
      ),
      NamedImage(
        imageName = "fnord-1.1.0",
        attributes = mapOf(
          "creationDate" to "2019-07-29T13:01:00.000Z"
        ),
        tagsByImageId = mapOf(
          "ami-2" to mapOf("appversion" to "fnord-1.1.0")
        ),
        accounts = setOf(account),
        amis = mapOf(
          imageRegion to listOf("ami-2")
        )
      ),
      NamedImage(
        imageName = "fnord-1.2.0",
        attributes = mapOf(
          "creationDate" to "2019-07-30T13:01:00.000Z"
        ),
        tagsByImageId = mapOf(
          "ami-3" to mapOf("appversion" to "fnord-1.2.0")
        ),
        accounts = setOf(account),
        amis = mapOf(
          imageRegion to listOf("ami-3")
        )
      )
    )

    val artifact = DeliveryArtifact("fnord", DEB)

    val resource = Resource(
      apiVersion = SPINNAKER_API_V1.subApi("ec2"),
      kind = "cluster",
      metadata = mapOf(
        "uid" to randomUID(),
        "name" to "ec2:cluster:fnord:$account:$resourceRegion:fnord",
        "serviceAccount" to "keel@spinnaker",
        "application" to "fnord"
      ),
      spec = ClusterSpec(
        moniker = Moniker("fnord"),
        location = Location(
          accountName = account,
          region = resourceRegion,
          subnet = "internal (vpc0)",
          availabilityZones = setOf()
        ),
        launchConfiguration = LaunchConfigurationSpec(
          imageProvider = imageProvider,
          instanceType = "m5.large",
          ebsOptimized = true,
          iamRole = "fnordIamRole",
          keyPair = "fnordKeyPair"
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

    fun resolve(): String = runBlocking {
      subject.resolveImageId(resource)
    }
  }

  fun tests() = rootContext<Fixture<*>> {
    derivedContext<Fixture<IdImageProvider>>("a simple image id") {
      fixture {
        Fixture(
          IdImageProvider("ami-12345678")
        )
      }

      test("just returns the image id") {
        expectThat(resolve())
          .isEqualTo(imageProvider.imageId)
      }
    }

    derivedContext<Fixture<ArtifactImageProvider>>("an image derived from an artifact") {
      fixture {
        Fixture(
          ArtifactImageProvider(DeliveryArtifact("fnord", DEB))
        )
      }

      context("the resource is not in an environment") {
        before {
          coEvery {
            cloudDriverService.namedImages(any(), any(), any())
          } answers {
            val name = secondArg<String>()
            images.filter { it.appVersion.startsWith(name) }
          }
        }

        test("returns the most recent version of the artifact") {
          expectThat(resolve())
            .isEqualTo("ami-3")
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
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "1.1.0", "test")
            coEvery {
              cloudDriverService.namedImages(any(), any(), any())
            } answers {
              val name = secondArg<String>()
              images.filter { it.appVersion == name }
            }
          }

          after {
            artifactRepository.dropAll()
          }

          test("returns the image id of the approved version") {
            expectThat(resolve())
              .isEqualTo("ami-2") // TODO: false moniker
          }
        }

        context("no artifact version has been approved for the environment") {
          test("throws an exception") {
            expectCatching { resolve() }
              .failed()
              .isA<NoImageSatisfiesConstraints>()
          }
        }

        context("no image is found for the artifact version") {
          before {
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "1.1.0", "test")
            coEvery {
              cloudDriverService.namedImages(any(), any(), any())
            } returns emptyList()
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

        context("no image is found for the artifact in the desired region") {
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
            artifactRepository.approveVersionFor(deliveryConfig, artifact, "1.1.0", "test")
            coEvery {
              cloudDriverService.namedImages(any(), any(), any())
            } answers {
              val name = secondArg<String>()
              images.filter { it.appVersion == name }
            }
          }

          after {
            artifactRepository.dropAll()
          }

          test("throws an exception") {
            expectCatching { resolve() }
              .failed()
              .isA<NoImageFoundForRegion>()
          }
        }
      }
    }
  }
}
