package com.netflix.spinnaker.keel.bakery.constraint

import com.netflix.frigga.ami.AppVersion
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.artifacts.DeliveryArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.support.EventPublisher
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.bakery.api.ImageExistsConstraint
import com.netflix.spinnaker.keel.caffeine.TEST_CACHE_FACTORY
import com.netflix.spinnaker.keel.clouddriver.ImageService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.locatableResource
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService.NoopDynamicConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.Called
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.verify
import strikt.api.expectThat
import strikt.assertions.isFalse
import strikt.assertions.isTrue

internal class ImageExistsConstraintEvaluatorTests : JUnit5Minutests {

  data class Fixture(
    val account: String = "prod",
    val regions: List<String> = listOf("us-west-2", "us-east-1"),
    val artifact: DeliveryArtifact = DebianArtifact(
      name = "fnord",
      deliveryConfigName = "image-exists-constraint-evaluator-tests",
      vmOptions = VirtualMachineOptions(
        baseOs = "bionique-classique",
        regions = regions.toSet()
      )
    ),
    val resource: Resource<*> = locatableResource(
      locations = SimpleLocations(
        account = account,
        regions = regions.map(::SimpleRegionSpec).toSet()
      )
    ),
    val deliveryConfig: DeliveryConfig = deliveryConfig(
      configName = "bake-constraint-evaluator-tests",
      artifact = artifact,
      env = Environment(
        name = "test",
        resources = setOf(resource),
        constraints = setOf(ImageExistsConstraint())
      )
    )
  ) {
    val eventPublisher = mockk<EventPublisher>(relaxUnitFun = true)
    val imageService = mockk<ImageService>(relaxUnitFun = true)
    val evaluator = ImageExistsConstraintEvaluator(
      imageService,
      NoopDynamicConfig(),
      eventPublisher,
      TEST_CACHE_FACTORY
    )
    val appVersion = "fnord-1.0.0-123456"
    var promotionResult: Boolean? = null
  }

  private fun Fixture.canPromote() {
    promotionResult = evaluator.canPromote(
      deliveryConfig.artifacts.first(),
      appVersion,
      deliveryConfig,
      deliveryConfig.environments.first()
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("A non-Debian artifact") {
      deriveFixture {
        Fixture(
          artifact = DockerArtifact("fnord", "bake-constraint-evaluator-tests")
        )
      }

      before {
        canPromote()
      }

      test("the constraint passes") {
        expectThat(promotionResult)
          .describedAs("promotion decision")
          .isTrue()
      }

      test("We don't go trying to look the image up") {
        verify { imageService wasNot Called }
      }
    }

    context("CloudDriver cannot find an image for an artifact version") {
      before {
        coEvery {
          imageService.getLatestNamedImageWithAllRegionsForAppVersion(any(), any(), any())
        } returns null

        canPromote()
      }

      test("the constraint does not pass (yet)") {
        expectThat(promotionResult)
          .describedAs("promotion decision")
          .isFalse()
      }
    }

    context("CloudDriver finds a matching image for an artifact version") {
      before {
        coEvery {
          imageService.getLatestNamedImageWithAllRegionsForAppVersion(
            AppVersion.parseName(appVersion),
            "test",
            (artifact as DebianArtifact).vmOptions.regions
          )
        } returns NamedImage(
          imageName = appVersion,
          attributes = mapOf(
            "creationDate" to "2020-03-17T12:09:00.000Z"
          ),
          tagsByImageId = mapOf(
            "ami-1" to mapOf("appversion" to appVersion, "base_ami_version" to "nflx-base-5.464.0-h1473.31178a8")
          ),
          accounts = setOf(
            "test" +
              ""
          ),
          amis = mapOf(
            "us-west-2" to listOf("ami-1")
          )
        )

        canPromote()
      }

      test("the constraint passes") {
        expectThat(promotionResult)
          .describedAs("promotion decision")
          .isTrue()
      }
    }
  }
}
