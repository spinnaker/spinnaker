package com.netflix.spinnaker.keel.dgs

import com.netflix.spinnaker.keel.bakery.BakeryMetadataService
import com.netflix.spinnaker.keel.bakery.diff.OldNewPair
import com.netflix.spinnaker.keel.bakery.diff.PackageDiff
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.core.api.PromotionStatus.PREVIOUS
import com.netflix.spinnaker.keel.core.api.PublishedArtifactInEnvironment
import com.netflix.spinnaker.keel.test.artifactReferenceResource
import com.netflix.spinnaker.keel.test.deliveryConfig
import graphql.schema.DataFetchingEnvironment
import io.mockk.mockk
import io.mockk.spyk
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import io.mockk.coEvery as every

class ApplicationFetcherSupportTests {
  private val cloudDriverService: CloudDriverService = mockk()
  private val bakeryMetadataService: BakeryMetadataService = mockk()
  private val deliveryConfig = deliveryConfig(resources = setOf(artifactReferenceResource()))
  private val deliveryArtifact = deliveryConfig.artifacts.first()
  private val packageDiff = PackageDiff(
    added = mapOf("package1" to "1.0.0"),
    removed = mapOf("package2" to "1.0.0"),
    changed = mapOf("package3" to OldNewPair("1.0.0", "1.0.1"))
  )
  private val dfe: DataFetchingEnvironment = mockk()
  private val subject = spyk(ApplicationFetcherSupport(cloudDriverService, bakeryMetadataService))

  @BeforeEach
  fun setupMocks() {
    every {
      subject.getDeliveryConfigFromContext(any())
    } returns deliveryConfig

    every {
      subject.getDiffContext(any())
    } returns ArtifactDiffContext(
      deliveryConfig = deliveryConfig,
      deliveryArtifact = deliveryArtifact,
      fetchedVersion = PublishedArtifactInEnvironment(
        publishedArtifact = deliveryArtifact.toArtifactVersion("fnord-1.0.1"),
        status = CURRENT,
        environmentName = "test"
      ),
      currentDeployedVersion = PublishedArtifactInEnvironment(
        publishedArtifact = deliveryArtifact.toArtifactVersion("fnord-1.0.1"),
        status = CURRENT,
        environmentName = "test"
      ),
      previousDeployedVersion = PublishedArtifactInEnvironment(
        publishedArtifact = deliveryArtifact.toArtifactVersion("fnord-1.0.0"),
        status = PREVIOUS,
        environmentName = "test",
        replacedBy = "fnord-1.0.1"
      )
    )

    every {
      cloudDriverService.namedImages(DEFAULT_SERVICE_ACCOUNT, "fnord-1.0.0", null)
    } returns listOf(
      NamedImage("fnord-1.0.0-x86_64-bionic-classic-hvm-sriov-ebs")
    )

    every {
      cloudDriverService.namedImages(DEFAULT_SERVICE_ACCOUNT, "fnord-1.0.1", null)
    } returns listOf(
      NamedImage("fnord-1.0.1-x86_64-bionic-classic-hvm-sriov-ebs")
    )

    every {
      bakeryMetadataService.getPackageDiff(
        region ="us-east-1",
        oldImage = "fnord-1.0.0-x86~64-bionic-classic-hvm-sriov-ebs",
        newImage = "fnord-1.0.1-x86~64-bionic-classic-hvm-sriov-ebs"
      )
    } returns packageDiff
  }

  @Test
  fun packageDiff() {
    val result = subject.getDebianPackageDiff(dfe)
    expectThat(result).isEqualTo(packageDiff.toDgs())
  }
}
