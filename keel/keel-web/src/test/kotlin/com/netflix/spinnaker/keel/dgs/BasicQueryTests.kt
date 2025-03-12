package com.netflix.spinnaker.keel.dgs

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
import com.netflix.graphql.dgs.DgsQueryExecutor
import com.netflix.graphql.dgs.autoconfig.DgsAutoConfiguration
import com.netflix.spinnaker.keel.actuation.ExecutionSummaryService
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.EC2_CLUSTER_V1_1
import com.netflix.spinnaker.keel.artifacts.ArtifactVersionLinks
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.auth.AuthorizationSupport
import com.netflix.spinnaker.keel.core.api.PromotionStatus
import com.netflix.spinnaker.keel.core.api.PublishedArtifactInEnvironment
import com.netflix.spinnaker.keel.front50.Front50Cache
import com.netflix.spinnaker.keel.front50.Front50Service
import com.netflix.spinnaker.keel.igor.DeliveryConfigImporter
import com.netflix.spinnaker.keel.lifecycle.LifecycleEventRepository
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.DismissibleNotificationRepository
import com.netflix.spinnaker.keel.persistence.EnvironmentDeletionRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.TaskTrackingRepository
import com.netflix.spinnaker.keel.scm.ScmUtils
import com.netflix.spinnaker.keel.services.ApplicationService
import com.netflix.spinnaker.keel.services.ResourceStatusService
import com.netflix.spinnaker.keel.test.deliveryConfig
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.upsert.DeliveryConfigUpserter
import com.netflix.spinnaker.keel.veto.unhappy.UnhappyVeto
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import strikt.api.expectCatching
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess

@SpringBootTest(
  classes = [DgsAutoConfiguration::class, DgsTestConfig::class],
)
class BasicQueryTests {

  @Autowired
  lateinit var dgsQueryExecutor: DgsQueryExecutor

  @MockkBean
  lateinit var authorizationSupport: AuthorizationSupport

  @MockkBean
  lateinit var keelRepository: KeelRepository

  @MockkBean
  lateinit var actuationPauser: ActuationPauser

  @MockkBean
  lateinit var artifactVersionLinks: ArtifactVersionLinks

  @MockkBean
  lateinit var notificationRepository: DismissibleNotificationRepository

  @MockkBean
  lateinit var scmUtils: ScmUtils

  @MockkBean
  lateinit var executionSummaryService: ExecutionSummaryService

  @MockkBean
  lateinit var yamlMapper: YAMLMapper

  @MockkBean
  lateinit var deliveryConfigImporter: DeliveryConfigImporter

  @MockkBean
  lateinit var environmentDeletionRepository: EnvironmentDeletionRepository

  @MockkBean
  lateinit var front50Service: Front50Service

  @MockkBean
  lateinit var front50Cache: Front50Cache

  @MockkBean
  lateinit var deliveryConfigUpserter: DeliveryConfigUpserter

  @MockkBean
  lateinit var lifecycleEventRepository: LifecycleEventRepository

  @MockkBean
  lateinit var applicationService: ApplicationService

  @MockkBean
  lateinit var unhappyVeto: UnhappyVeto

  @MockkBean
  lateinit var deliveryConfigRepository: DeliveryConfigRepository

  @MockkBean
  lateinit var resourceStatusService: ResourceStatusService

  @MockkBean
  lateinit var taskTrackingRepository: TaskTrackingRepository

  private val artifact = DebianArtifact(
    name = "fnord",
    deliveryConfigName = "fnord",
    vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2"))
  )

  private val resource = resource(
    kind = EC2_CLUSTER_V1_1.kind,
    spec = ClusterSpec(
      moniker = Moniker("fnord"),
      artifactReference = "fnord",
      locations = SubnetAwareLocations(
        account = "test",
        vpc = "vpc0",
        subnet = "internal (vpc0)",
        regions = setOf(
          SubnetAwareRegionSpec(
            name = "us-east-1",
            availabilityZones = setOf()
          )
        )
      )
    )
  )
  private val deliveryConfig = deliveryConfig(artifact = artifact, resources = setOf(resource))

  @BeforeEach
  fun setup() {
    every {
      keelRepository.getDeliveryConfigForApplication(any())
    } returns deliveryConfig

    every {
      keelRepository.getAllVersionsForEnvironment(artifact, deliveryConfig, "test")
    } returns listOf(
      PublishedArtifactInEnvironment(
        artifact.toArtifactVersion(version = "v1"),
        status = PromotionStatus.CURRENT,
        environmentName = "test"
      )
    )
  }

  fun getQuery(path: String) = javaClass.getResource(path).readText().trimIndent()


  @Test
  fun basicTest() {
    expectCatching {
      dgsQueryExecutor.executeAndExtractJsonPath<String>(
        getQuery("/dgs/basicQuery.graphql"),
        "data.md_application.environments[0].name",
        mapOf("appName" to "fnord")
      )
    }.isSuccess().isEqualTo("test")
  }

  @Test
  fun BasicTestQueryDeprecated() {
    expectCatching {
      dgsQueryExecutor.executeAndExtractJsonPath<String>(
        getQuery("/dgs/deprecatedBasicQuery.graphql"),
        "data.application.environments[0].name",
        mapOf("appName" to "fnord")
      )
    }.isSuccess().isEqualTo("test")
  }

  @Test
  fun artifactVersionStatus() {
    expectCatching {
      dgsQueryExecutor.executeAndExtractJsonPath<String>(
        getQuery("/dgs/basicQuery.graphql"),
        "data.md_application.environments[0].state.artifacts[0].versions[0].status",
        mapOf("appName" to "fnord")
      )
    }.isSuccess().isEqualTo("CURRENT")
  }

  @Test
  fun artifactVersionStatusDeprecated() {
    expectCatching {
      dgsQueryExecutor.executeAndExtractJsonPath<String>(
        getQuery("/dgs/deprecatedBasicQuery.graphql"),
        "data.application.environments[0].state.artifacts[0].versions[0].status",
        mapOf("appName" to "fnord")
      )
    }.isSuccess().isEqualTo("CURRENT")
  }
}
