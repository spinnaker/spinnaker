package com.netflix.spinnaker.keel.bakery.resource

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.NoKnownArtifactVersions
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.bakery.UnknownBaseImage
import com.netflix.spinnaker.keel.bakery.api.BaseLabel.RELEASE
import com.netflix.spinnaker.keel.bakery.api.ImageSpec
import com.netflix.spinnaker.keel.bakery.api.StoreType.EBS
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.Image
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.plugin.ResourceDiff
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import java.util.UUID.randomUUID

internal class ImageHandlerTests : JUnit5Minutests {

  internal class Fixture {
    val artifactRepository = InMemoryArtifactRepository()
    val theCloudDriver = mockk<CloudDriverService>()
    val orcaService = mockk<OrcaService>()
    val igorService = mockk<ArtifactService>()
    val baseImageCache = mockk<BaseImageCache>()
    val imageService = mockk<ImageService>()
    val handler = ImageHandler(
      configuredObjectMapper(),
      artifactRepository,
      baseImageCache,
      theCloudDriver,
      orcaService,
      igorService,
      imageService,
      emptyList()
    )
    val resource = Resource(
      apiVersion = handler.apiVersion,
      kind = handler.supportedKind.first.singular,
      metadata = ResourceMetadata(
        uid = randomUID(),
        name = ResourceName("bakery:image:keel")
      ),
      spec = ImageSpec(
        artifactName = "keel",
        baseLabel = RELEASE,
        baseOs = "xenial",
        regions = setOf("us-west-2", "us-east-1"),
        storeType = EBS
      )
    )
    val image = Image(
      baseAmiVersion = "nflx-base-5.378.0-h1230.8808866",
      appVersion = "keel-0.161.0-h63.24d0843",
      regions = resource.spec.regions
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    after { artifactRepository.dropAll() }

    context("resolving desired and current state") {
      context("clouddriver has an image for the base AMI") {
        before {
          val artifact = DeliveryArtifact("keel", DEB)
          artifactRepository.register(artifact)
          artifactRepository.store(artifact, image.appVersion)

          every {
            baseImageCache.getBaseImage(resource.spec.baseOs, resource.spec.baseLabel)
          } returns "xenialbase-x86_64-201904291721-ebs"

          every { theCloudDriver.namedImages("xenialbase-x86_64-201904291721-ebs", "test") } returns CompletableDeferred(
            listOf(
              NamedImage(
                imageName = "xenialbase-x86_64-201904291721-ebs",
                attributes = mapOf(
                  "virtualizationType" to "paravirtual",
                  "creationDate" to "2019-04-29T18:11:45.000Z"
                ),
                tagsByImageId = mapOf(
                  "ami-0c3f1dc20535ef3b7" to mapOf(
                    "base_ami_version" to "nflx-base-5.378.0-h1230.8808866",
                    "creation_time" to "2019-04-29 17:53:18 UTC",
                    "creator" to "builds",
                    "base_ami_flavor" to "xenial",
                    "build_host" to "https://opseng.builds.test.netflix.net/"
                  ),
                  "ami-0c86c73e07f5df756" to mapOf(
                    "creation_time" to "2019-04-29 17:53:18 UTC",
                    "build_host" to "https://opseng.builds.test.netflix.net/",
                    "base_ami_flavor" to "xenial",
                    "base_ami_version" to "nflx-base-5.378.0-h1230.8808866",
                    "creator" to "builds"
                  ),
                  "ami-05f25743c025c5a11" to mapOf(
                    "base_ami_version" to "nflx-base-5.378.0-h1230.8808866",
                    "build_host" to "https://opseng.builds.test.netflix.net/",
                    "creation_time" to "2019-04-29 17:53:18 UTC",
                    "creator" to "builds",
                    "base_ami_flavor" to "xenial"
                  ),
                  "ami-04772f06ffdb0bc68" to mapOf(
                    "base_ami_flavor" to "xenial",
                    "creator" to "builds",
                    "creation_time" to "2019-04-29 17:53:18 UTC",
                    "build_host" to "https://opseng.builds.test.netflix.net/",
                    "base_ami_version" to "nflx-base-5.378.0-h1230.8808866"
                  )
                ),
                accounts = setOf("test"),
                amis = mapOf(
                  "eu-west-1" to listOf("ami-04772f06ffdb0bc68"),
                  "us-east-1" to listOf("ami-0c3f1dc20535ef3b7"),
                  "us-west-1" to listOf("ami-0c86c73e07f5df756"),
                  "us-west-2" to listOf("ami-05f25743c025c5a11")
                )
              )
            )
          )

          every { theCloudDriver.namedImages(image.appVersion, "test") } returns CompletableDeferred(
            listOf(
              NamedImage(
                imageName = "keel-0.161.0-h63.24d0843-x86_64-20190422190426-xenial-hvm-sriov-ebs",
                attributes = mapOf(
                  "virtualizationType" to "hvm",
                  "creationDate" to "2019-04-22T19:08:59.000Z"
                ),
                tagsByImageId = mapOf(
                  "ami-0863f573375b40615" to mapOf(
                    "appversion" to image.appVersion,
                    "base_ami_version" to image.baseAmiVersion,
                    "build_host" to "https://spinnaker.builds.test.netflix.net/",
                    "creation_time" to "2019-04-22 19:08:17 UTC",
                    "creator" to "delivery-engineering@netflix.com"
                  ),
                  "ami-04c1f962313f1890d" to mapOf(
                    "appversion" to image.appVersion,
                    "base_ami_version" to image.baseAmiVersion,
                    "build_host" to "https://spinnaker.builds.test.netflix.net/",
                    "creation_time" to "2019-04-22 19:09:00 UTC",
                    "creator" to "delivery-engineering@netflix.com"
                  )
                ),
                accounts = setOf("mgmt", "mgmttest", "test"),
                amis = mapOf(
                  "us-west-2" to listOf("ami-0863f573375b40615"),
                  "us-east-1" to listOf("ami-04c1f962313f1890d")
                )
              )
            )
          )

          coEvery { imageService.getLatestImage("keel", "keel-0.161.0-h63.24d0843", "test") } returns image
        }

        test("desired state composes application and base image versions") {
          val (desired, current) = runBlocking {
            handler.resolve(resource)
          }
          expectThat(desired).isEqualTo(image)
          expectThat(current).isEqualTo(image)
        }
      }

      context("there are no known versions of the artifact") {
        before {
          val artifact = DeliveryArtifact("keel", DEB)
          artifactRepository.register(artifact)
        }

        test("an exception is thrown") {
          expectThrows<NoKnownArtifactVersions> { handler.resolve(resource) }
        }
      }

      context("there is no cached base image") {
        before {
          val artifact = DeliveryArtifact("keel", DEB)
          artifactRepository.register(artifact)
          artifactRepository.store(artifact, image.appVersion)

          every {
            baseImageCache.getBaseImage(resource.spec.baseOs, resource.spec.baseLabel)
          } throws UnknownBaseImage(resource.spec.baseOs, resource.spec.baseLabel)
        }

        test("the exception is propagated") {
          expectThrows<UnknownBaseImage> { handler.resolve(resource) }
        }
      }

      context("clouddriver can't find the base AMI") {
        before {
          val artifact = DeliveryArtifact("keel", DEB)
          artifactRepository.register(artifact)
          artifactRepository.store(artifact, image.appVersion)

          every {
            baseImageCache.getBaseImage(resource.spec.baseOs, resource.spec.baseLabel)
          } returns "xenialbase-x86_64-201904291721-ebs"

          every {
            theCloudDriver.namedImages("xenialbase-x86_64-201904291721-ebs", "test")
          } returns CompletableDeferred(emptyList())
        }

        test("an exception is thrown") {
          expectThrows<BaseAmiNotFound> { handler.resolve(resource) }
        }
      }
    }

    context("baking a new AMI") {
      before {
        every { igorService.getArtifact("keel", "0.161.0-h63.24d0843") } returns CompletableDeferred(
          Artifact(
            "DEB",
            false,
            "keel",
            "0.161.0-h63.24d0843",
            "rocket",
            "debian-local:pool/k/keel/keel_0.160.0-h62.02c0fbf_all.deb",
            mapOf(
              "repoKey" to "stash/spkr/keel-nflx",
              "rocketMessageId" to "84c1ecca-7f76-482e-9952-226fb2c4c410",
              "releaseStatus" to "FINAL"
            ),
            null,
            "https://spinnaker.builds.test.netflix.net/job/SPINNAKER-rocket-package-keel/62",
            null
          )
        )
      }

      test("bake configuration is based on resource spec") {
      }

      test("artifact is attached to the trigger") {
        val request = slot<OrchestrationRequest>()
        every { orcaService.orchestrate(capture(request)) } returns randomTaskRef()

        runBlocking {
          handler.upsert(resource, ResourceDiff(null, image))
        }

        expectThat(request.captured.trigger.artifacts)
          .hasSize(1)
      }
    }
  }

  private fun randomTaskRef() = CompletableDeferred(TaskRefResponse(randomUUID().toString()))
}
