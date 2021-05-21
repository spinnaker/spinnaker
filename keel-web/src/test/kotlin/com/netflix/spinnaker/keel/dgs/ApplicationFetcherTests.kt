package com.netflix.spinnaker.keel.dgs

import com.fasterxml.jackson.module.kotlin.convertValue
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import com.netflix.spinnaker.keel.api.ScmInfo
import com.netflix.spinnaker.keel.artifacts.ArtifactVersionLinks
import com.netflix.spinnaker.keel.bakery.BakeryMetadataService
import com.netflix.spinnaker.keel.bakery.diff.OldNewPair
import com.netflix.spinnaker.keel.bakery.diff.PackageDiff
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.NamedImage
import com.netflix.spinnaker.keel.core.api.DEFAULT_SERVICE_ACCOUNT
import com.netflix.spinnaker.keel.core.api.PromotionStatus.CURRENT
import com.netflix.spinnaker.keel.core.api.PromotionStatus.PREVIOUS
import com.netflix.spinnaker.keel.core.api.PublishedArtifactInEnvironment
import com.netflix.spinnaker.keel.graphql.types.MdPackageDiff
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventRepository
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.rest.dgs.toDgs
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.services.ApplicationService
import com.netflix.spinnaker.keel.services.ResourceStatusService
import com.netflix.spinnaker.keel.test.artifactReferenceResource
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.ninjasquad.springmockk.MockkBean
import io.mockk.coEvery as every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import strikt.api.expectThat
import strikt.assertions.isEqualTo

@SpringBootTest(classes = [DgsAutoConfiguration::class, DgsTestConfig::class])
class ApplicationFetcherTests {

  @MockkBean
  lateinit var keelRepository: KeelRepository
  @MockkBean
  lateinit var resourceStatusService: ResourceStatusService
  @MockkBean
  lateinit var scmInfo: ScmInfo
  @MockkBean
  lateinit var actuationPauser: ActuationPauser
  @MockkBean
  lateinit var cloudDriverService: CloudDriverService
  @MockkBean
  lateinit var bakeryMetadataService: BakeryMetadataService
  @MockkBean
  lateinit var lifecycleEventRepository: LifecycleEventRepository
  @MockkBean
  lateinit var applicationService: ApplicationService
  @MockkBean
  lateinit var artifactVersionLinks: ArtifactVersionLinks
  @Autowired
  lateinit var dgsQueryExecutor: DgsQueryExecutor

  private val objectMapper = configuredObjectMapper()
  private val deliveryConfig = deliveryConfig(resources = setOf(artifactReferenceResource()))
  private val deliveryArtifact = deliveryConfig.artifacts.first()
  private val packageDiff = PackageDiff(
    added = mapOf("package1" to "1.0.0"),
    removed = mapOf("package2" to "1.0.0"),
    changed = mapOf("package3" to OldNewPair("1.0.0", "1.0.1"))
  )

  @BeforeEach
  fun setupMocks() {
    every {
      keelRepository.getDeliveryConfigForApplication("fnord")
    } returns deliveryConfig

    every {
      keelRepository.getAllVersionsForEnvironment(deliveryArtifact, deliveryConfig, "test")
    } returns listOf(
      PublishedArtifactInEnvironment(
        publishedArtifact = deliveryArtifact.toArtifactVersion("fnord-1.0.1"),
        status = CURRENT,
        environmentName = "test"
      ),
      PublishedArtifactInEnvironment(
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
    val result = dgsQueryExecutor.executeAndExtractJsonPath<Map<String, Any?>>(
      query = """
        query fetchApplication {
          application(appName: "fnord") {
            name
            environments {
              name
              state {
                artifacts {
                  name
                  type
                  versions(statuses: [CURRENT]) {
                    buildNumber
                    version
                    packageDiff {
                      added {
                        package
                        version
                      }
                      removed {
                        package
                        version
                      }
                      changed {
                        package
                        oldVersion
                        newVersion
                      }
                    }
                  }
                }
              }
            }
          }
        }
        """.trimIndent(),
      jsonPath = "data.application.environments[0].state.artifacts[0].versions[0].packageDiff"
    ).let {
      objectMapper.convertValue<MdPackageDiff>(it)
    }

    expectThat(result).isEqualTo(packageDiff.toDgs())
  }
}