package com.netflix.spinnaker.keel.bakery.resource

import com.netflix.spinnaker.igor.ArtifactService
import com.netflix.spinnaker.keel.api.ArtifactStatus.FINAL
import com.netflix.spinnaker.keel.api.ArtifactStatus.SNAPSHOT
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.NoKnownArtifactVersions
import com.netflix.spinnaker.keel.bakery.BaseImageCache
import com.netflix.spinnaker.keel.bakery.UnknownBaseImage
import com.netflix.spinnaker.keel.bakery.api.BaseLabel.RELEASE
import com.netflix.spinnaker.keel.bakery.api.ImageSpec
import com.netflix.spinnaker.keel.bakery.api.StoreType.EBS
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.Image
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.orca.TaskRefResponse
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.kork.artifacts.model.Artifact
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.runBlocking
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.hasEntry
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
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
    val handler = ImageHandler(
      configuredObjectMapper(),
      artifactRepository,
      baseImageCache,
      theCloudDriver,
      orcaService,
      igorService,
      imageService,
      publisher,
      emptyList()
    )
    val resource = resource(
      apiVersion = handler.apiVersion,
      kind = handler.supportedKind.first.singular,
      spec = ImageSpec(
        artifactName = "keel",
        baseLabel = RELEASE,
        baseOs = "xenial",
        regions = setOf("us-west-2", "us-east-1"),
        storeType = EBS,
        application = "keel"
      )
    )
    val resourceOnlySnapshot = resource.copy(spec = resource.spec.copy(artifactStatuses = listOf(SNAPSHOT)))

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
          artifactRepository.store(artifact, image.appVersion, FINAL)

          every {
            baseImageCache.getBaseImage(resource.spec.baseOs, resource.spec.baseLabel)
          } returns "xenialbase-x86_64-201904291721-ebs"

          coEvery { theCloudDriver.namedImages("keel@spinnaker", "xenialbase-x86_64-201904291721-ebs", "test") } returns
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

          coEvery { imageService.getLatestImage("keel", "test") } returns image
        }

        test("desired state composes application and base image versions") {
          val desired = runBlocking {
            handler.desired(resource)
          }
          expectThat(desired).isEqualTo(image)
        }
      }

      context("there are no known versions of the artifact") {
        before {
          val artifact = DeliveryArtifact("keel", DEB)
          artifactRepository.register(artifact)
        }

        test("an exception is thrown") {
          expectThrows<NoKnownArtifactVersions> { handler.desired(resource) }
        }
      }

      context("there is only a version with the wrong status") {
        before {
          val artifact = DeliveryArtifact("keel", DEB)
          artifactRepository.register(artifact)
          artifactRepository.store(artifact, image.appVersion, FINAL)
        }

        test("an exception is thrown") {
          expectThrows<NoKnownArtifactVersions> { handler.desired(resourceOnlySnapshot) }
        }
      }

      context("there is no cached base image") {
        before {
          val artifact = DeliveryArtifact("keel", DEB)
          artifactRepository.register(artifact)
          artifactRepository.store(artifact, image.appVersion, FINAL)

          every {
            baseImageCache.getBaseImage(resource.spec.baseOs, resource.spec.baseLabel)
          } throws UnknownBaseImage(resource.spec.baseOs, resource.spec.baseLabel)
        }

        test("the exception is propagated") {
          expectThrows<UnknownBaseImage> { handler.desired(resource) }
        }
      }

      context("clouddriver can't find the base AMI") {
        before {
          val artifact = DeliveryArtifact("keel", DEB)
          artifactRepository.register(artifact)
          artifactRepository.store(artifact, image.appVersion, FINAL)

          every {
            baseImageCache.getBaseImage(resource.spec.baseOs, resource.spec.baseLabel)
          } returns "xenialbase-x86_64-201904291721-ebs"

          coEvery {
            theCloudDriver.namedImages("keel@spinnaker", "xenialbase-x86_64-201904291721-ebs", "test")
          } returns emptyList()
        }

        test("an exception is thrown") {
          expectThrows<BaseAmiNotFound> { handler.desired(resource) }
        }
      }

      context("the image already exists in more regions than desired") {
        before {
          coEvery {
            imageService.getLatestImage("keel", "test")
          } returns image.copy(regions = image.regions + "eu-west-1")
        }
        test("current should filter the undesireable regions out of the image") {
          runBlocking {
            expectThat(handler.current(resource)!!.regions).isEqualTo(resource.spec.regions)
          }
        }
      }
    }

    context("baking a new AMI") {
      before {
        coEvery { igorService.getArtifact("keel", "0.161.0-h63.24d0843") } returns
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
      }

      test("artifact is attached to the trigger") {
        val request = slot<OrchestrationRequest>()
        coEvery { orcaService.orchestrate("keel@spinnaker", capture(request)) } returns randomTaskRef()

        runBlocking {
          handler.upsert(resource, ResourceDiff(image, null))
        }

        expectThat(request.captured.trigger.artifacts)
          .hasSize(1)
      }

      test("the full debian name is specified when we create a bake task") {
        val request = slot<OrchestrationRequest>()
        coEvery { orcaService.orchestrate("keel@spinnaker", capture(request)) } returns randomTaskRef()

        runBlocking {
          handler.upsert(resource, ResourceDiff(image, null))
        }

        expectThat(request.captured.job.first())
          .hasEntry("package", "keel_0.160.0-h62.02c0fbf_all.deb")
      }
    }
  }

  private fun randomTaskRef() = TaskRefResponse(randomUUID().toString())
}
