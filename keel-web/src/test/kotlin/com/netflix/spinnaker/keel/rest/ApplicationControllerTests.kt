package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.ec2.ArtifactImageProvider
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ReferenceArtifactImageProvider
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.CombinedRepository
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import java.time.Clock
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.ResultMatcher
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.ContentResultMatchers
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import strikt.api.expectThat
import strikt.assertions.containsExactly

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class ApplicationControllerTests : JUnit5Minutests {
  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var deliveryConfigRepository: InMemoryDeliveryConfigRepository

  @Autowired
  lateinit var resourceRepository: InMemoryResourceRepository

  @Autowired
  lateinit var artifactRepository: InMemoryArtifactRepository

  @Autowired
  lateinit var actuationPauser: ActuationPauser

  private val jsonMapper = configuredObjectMapper()

  private val yamlMapper = configuredYamlMapper()

  class Fixture(combinedRepositoryMaker: () -> KeelRepository) {
    val application = "fnord"

    val artifact = DebianArtifact(
      name = application,
      deliveryConfigName = "manifest",
      reference = "fnord",
      statuses = setOf(ArtifactStatus.RELEASE)
    )

    val clusterDefaults = ClusterSpec.ServerGroupSpec(
      launchConfiguration = ClusterSpec.LaunchConfigurationSpec(
        instanceType = "m4.2xlarge",
        ebsOptimized = true,
        iamRole = "fnordInstanceProfile",
        keyPair = "fnordKeyPair"
      )
    )

    val environments = listOf("test", "staging", "production").associateWith { name ->
      Environment(name = name, resources = setOf(
        // cluster with new-style artifact reference
        resource(
          kind = SPINNAKER_EC2_API_V1.qualify("cluster"),
          spec = ClusterSpec(
            moniker = Moniker(application, "$name-west"),
            imageProvider = ReferenceArtifactImageProvider(reference = "fnord"),
            locations = SubnetAwareLocations(
              account = "test",
              vpc = "vpc0",
              subnet = "internal (vpc0)",
              regions = setOf(
                SubnetAwareRegionSpec(
                  name = "us-west-2",
                  availabilityZones = setOf("us-west-2a", "us-west-2b", "us-west-2c")
                )
              )
            ),
            _defaults = clusterDefaults
          )
        ),
        // cluster with old-style image provider
        resource(
          kind = SPINNAKER_EC2_API_V1.qualify("cluster"),
          spec = ClusterSpec(
            moniker = Moniker(application, "$name-east"),
            imageProvider = ArtifactImageProvider(deliveryArtifact = artifact),
            locations = SubnetAwareLocations(
              account = "test",
              vpc = "vpc0",
              subnet = "internal (vpc0)",
              regions = setOf(
                SubnetAwareRegionSpec(
                  name = "us-east-1",
                  availabilityZones = setOf("us-east-1a", "us-east-1b", "us-east-1c")
                )
              )
            ),
            _defaults = clusterDefaults
          )
        )
      ))
    }

    val deliveryConfig = DeliveryConfig(
      name = "manifest",
      application = application,
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = environments.values.toSet()
    )

    val combinedRepository = combinedRepositoryMaker()
  }

  fun makeCombinedRepository() = CombinedRepository(
    deliveryConfigRepository, artifactRepository, resourceRepository, Clock.systemDefaultZone(), mockk(relaxed = true)
  )

  fun tests() = rootContext<Fixture> {
    fixture { Fixture(this@ApplicationControllerTests::makeCombinedRepository) }

    after {
      deliveryConfigRepository.dropAll()
      resourceRepository.dropAll()
      artifactRepository.dropAll()
      clearAllMocks()
    }

    context("application with delivery config exists") {
      before {
        combinedRepository.upsertDeliveryConfig(deliveryConfig)
        // these events are required because Resource.toResourceSummary() relies on events to determine resource status
        deliveryConfig.environments.flatMap { it.resources }.forEach { resource ->
          resourceRepository.appendHistory(ResourceValid(resource))
        }
        artifactRepository.store(artifact, "1.0.0", ArtifactStatus.RELEASE)
        artifactRepository.store(artifact, "1.0.1", ArtifactStatus.RELEASE)
        artifactRepository.store(artifact, "1.0.2", ArtifactStatus.RELEASE)
        artifactRepository.store(artifact, "1.0.3", ArtifactStatus.RELEASE)
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "1.0.0", "test")
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "1.0.0", "staging")
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "1.0.0", "production")
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "1.0.1", "test")
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "1.0.1", "staging")
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "1.0.2", "test")
        artifactRepository.approveVersionFor(deliveryConfig, artifact, "1.0.3", "test")
      }

      test("can get basic summary by application") {
        val request = get("/application/$application")
          .accept(MediaType.APPLICATION_JSON_VALUE)
        mvc
          .perform(request)
          .andExpect(status().isOk)
          .andExpect(content().json(
            """
              {
                "applicationPaused":false,
                "hasManagedResources":true,
                "currentEnvironmentConstraints":[]
              }
            """.trimIndent()
          ))
      }

      context("with paused application") {
        before {
          actuationPauser.pauseApplication(application)
        }

        after {
          actuationPauser.resumeApplication(application)
        }

        test("reflects application paused status in basic summary") {
          val request = get("/application/$application")
            .accept(MediaType.APPLICATION_JSON_VALUE)
          mvc
            .perform(request)
            .andExpect(status().isOk)
            .andExpect(content().json(
              """
              {
                "applicationPaused":true,
                "hasManagedResources":true,
                "currentEnvironmentConstraints":[]
              }
            """.trimIndent()
            ))
        }
      }

      test("can get resource summaries by application") {
        val request = get("/application/$application?entities=resources")
          .accept(APPLICATION_YAML_VALUE)
        mvc
          .perform(request)
          .andExpect(status().isOk)
          .andDo { println(it.response.contentAsString) }
          .andExpect(content().yaml(
            """
            applicationPaused: false
            hasManagedResources: true
            currentEnvironmentConstraints: []
            resources:
            - id: "ec2:cluster:test:fnord-test-west"
              kind: "ec2/cluster@v1"
              status: "HAPPY"
              moniker:
                app: "fnord"
                stack: "test-west"
              locations:
                account: "test"
                vpc: "vpc0"
                regions:
                - name: "us-west-2"
              artifact:
                name: "fnord"
                type: "deb"
            - id: "ec2:cluster:test:fnord-test-east"
              kind: "ec2/cluster@v1"
              status: "HAPPY"
              moniker:
                app: "fnord"
                stack: "test-east"
              locations:
                account: "test"
                vpc: "vpc0"
                regions:
                - name: "us-east-1"
              artifact:
                name: "fnord"
                type: "deb"
            - id: "ec2:cluster:test:fnord-staging-west"
              kind: "ec2/cluster@v1"
              status: "HAPPY"
              moniker:
                app: "fnord"
                stack: "staging-west"
              locations:
                account: "test"
                vpc: "vpc0"
                regions:
                - name: "us-west-2"
              artifact:
                name: "fnord"
                type: "deb"
            - id: "ec2:cluster:test:fnord-staging-east"
              kind: "ec2/cluster@v1"
              status: "HAPPY"
              moniker:
                app: "fnord"
                stack: "staging-east"
              locations:
                account: "test"
                vpc: "vpc0"
                regions:
                - name: "us-east-1"
              artifact:
                name: "fnord"
                type: "deb"
            - id: "ec2:cluster:test:fnord-production-west"
              kind: "ec2/cluster@v1"
              status: "HAPPY"
              moniker:
                app: "fnord"
                stack: "production-west"
              locations:
                account: "test"
                vpc: "vpc0"
                regions:
                - name: "us-west-2"
              artifact:
                name: "fnord"
                type: "deb"
            - id: "ec2:cluster:test:fnord-production-east"
              kind: "ec2/cluster@v1"
              status: "HAPPY"
              moniker:
                app: "fnord"
                stack: "production-east"
              locations:
                account: "test"
                vpc: "vpc0"
                regions:
                - name: "us-east-1"
              artifact:
                name: "fnord"
                type: "deb"
            """.trimIndent()
          ))
      }

      test("can get environment summaries by application") {
        val request = get("/application/$application?entities=environments")
          .accept(APPLICATION_YAML_VALUE)
        mvc
          .perform(request)
          .andExpect(status().isOk)
          .andDo { println(it.response.contentAsString) }
          .andExpect(content().yaml(
            """
            applicationPaused: false
            hasManagedResources: true
            currentEnvironmentConstraints: []
            environments:
            - artifacts:
              - name: "fnord"
                type: "deb"
                statuses:
                - "RELEASE"
                versions:
                  current: "1.0.2"
                  pending: []
                  approved:
                  - "1.0.3"
                  previous:
                  - "1.0.0"
                  - "1.0.1"
                  vetoed: []
              name: "test"
              resources:
              - "ec2:cluster:test:fnord-test-west"
              - "ec2:cluster:test:fnord-test-east"
            - artifacts:
              - name: "fnord"
                type: "deb"
                statuses:
                - "RELEASE"
                versions:
                  current: "1.0.1"
                  pending:
                  - "1.0.2"
                  - "1.0.3"
                  approved: []
                  previous:
                  - "1.0.0"
                  vetoed: []
              name: "staging"
              resources:
              - "ec2:cluster:test:fnord-staging-west"
              - "ec2:cluster:test:fnord-staging-east"
            - artifacts:
              - name: "fnord"
                type: "deb"
                statuses:
                - "RELEASE"
                versions:
                  current: "1.0.0"
                  pending:
                  - "1.0.1"
                  - "1.0.2"
                  - "1.0.3"
                  approved: []
                  previous: []
                  vetoed: []
              name: "production"
              resources:
              - "ec2:cluster:test:fnord-production-west"
              - "ec2:cluster:test:fnord-production-east"
            """.trimIndent()
          ))
      }

      test("can get artifact summaries by application") {
        val request = get("/application/$application?entities=artifacts")
          .accept(APPLICATION_YAML_VALUE)
        mvc
          .perform(request)
          .andExpect(status().isOk)
          .andDo { println(it.response.contentAsString) }
          .andExpect(content().yaml(
            """
            applicationPaused: false
            hasManagedResources: true
            currentEnvironmentConstraints: []
            artifacts:
            - name: "fnord"
              type: "deb"
              versions:
              - version: "1.0.0"
                environments:
                - name: "test"
                  state: "previous"
                - name: "staging"
                  state: "previous"
                - name: "production"
                  state: "current"
              - version: "1.0.1"
                environments:
                - name: "test"
                  state: "previous"
                - name: "staging"
                  state: "current"
                - name: "production"
                  state: "pending"
              - version: "1.0.2"
                environments:
                - name: "test"
                  state: "current"
                - name: "staging"
                  state: "pending"
                - name: "production"
                  state: "pending"
              - version: "1.0.3"
                environments:
                - name: "test"
                  state: "approved"
                - name: "staging"
                  state: "pending"
                - name: "production"
                  state: "pending"
            """.trimIndent()
          ))
      }

      test("can get multiple types of summaries by application") {
        val request = get("/application/$application?entities=resources&entities=environments&entities=artifacts")
          .accept(MediaType.APPLICATION_JSON_VALUE)
        val result = mvc
          .perform(request)
          .andExpect(status().isOk)
          .andDo { println(it.response.contentAsString) }
          .andReturn()
        val response = jsonMapper.readValue<Map<String, Any>>(result.response.contentAsString)
        expectThat(response.keys)
          .containsExactly(
            "applicationPaused",
            "hasManagedResources",
            "currentEnvironmentConstraints",
            "resources",
            "environments",
            "artifacts"
          )
      }

      test("can get multiple types of summaries by application with comma-separate list of entities") {
        val request = get("/application/$application?entities=resources,environments,artifacts")
          .accept(MediaType.APPLICATION_JSON_VALUE)
        val result = mvc
          .perform(request)
          .andExpect(status().isOk)
          .andDo { println(it.response.contentAsString) }
          .andReturn()
        val response = jsonMapper.readValue<Map<String, Any>>(result.response.contentAsString)
        expectThat(response.keys)
          .containsExactly(
            "applicationPaused",
            "hasManagedResources",
            "currentEnvironmentConstraints",
            "resources",
            "environments",
            "artifacts"
          )
      }

      test("returns bad request for unknown entities") {
        val request = get("/application/$application?entities=bananas")
          .accept(MediaType.APPLICATION_JSON_VALUE)
        mvc.perform(request)
          .andExpect(status().isBadRequest)
      }

      test("is backwards-compatible with older version of the API") {
        var request = get("/application/$application?includeDetails=false")
          .accept(MediaType.APPLICATION_JSON_VALUE)
        var result = mvc
          .perform(request)
          .andExpect(status().isOk)
          .andDo { print(it.response.contentAsString) }
          .andReturn()
        var response = jsonMapper.readValue<Map<String, Any>>(result.response.contentAsString)
        expectThat(response.keys)
          .containsExactly(
            "applicationPaused",
            "hasManagedResources",
            "currentEnvironmentConstraints"
          )

        request = get("/application/$application?includeDetails=true")
          .accept(MediaType.APPLICATION_JSON_VALUE)
        result = mvc
          .perform(request)
          .andExpect(status().isOk)
          .andDo { print(it.response.contentAsString) }
          .andReturn()
        response = jsonMapper.readValue<Map<String, Any>>(result.response.contentAsString)
        expectThat(response.keys)
          .containsExactly(
            "applicationPaused",
            "hasManagedResources",
            "currentEnvironmentConstraints",
            "resources"
          )
      }
    }
  }

  private fun ContentResultMatchers.yaml(expectedYaml: String): ResultMatcher {
    return ResultMatcher { result: MvcResult ->
      val content = result.response.getContentAsString(StandardCharsets.UTF_8)
      val actual = yamlMapper.readValue<Map<String, Any?>>(content)
      val expected = yamlMapper.readValue<Map<String, Any?>>(expectedYaml)
      val diff = DefaultResourceDiff(actual, expected)

      if (!diff.hasChanges()) {
        true
      } else {
        throw AssertionError("Actual and expected YAML differ: \n${diff.toDeltaJson()}")
      }
    }
  }
}
