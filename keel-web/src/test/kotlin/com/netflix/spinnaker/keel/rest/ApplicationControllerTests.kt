package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper
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
import com.netflix.spinnaker.keel.constraints.ConstraintState
import com.netflix.spinnaker.keel.constraints.ConstraintStatus
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.PipelineConstraint
import com.netflix.spinnaker.keel.diff.DefaultResourceDiff
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.mockk
import java.nio.charset.StandardCharsets
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
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
  classes = [KeelApplication::class, MockEurekaConfiguration::class, ApplicationControllerTests.TestConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class ApplicationControllerTests : JUnit5Minutests {
  @Configuration
  class TestConfiguration {
    @Bean
    fun clock(): Clock = MutableClock(
      Instant.parse("2020-03-25T00:00:00.00Z"),
      ZoneId.of("UTC")
    )

    @Bean
    @Primary
    fun publisher(): ApplicationEventPublisher = mockk(relaxed = true)
  }

  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var clock: MutableClock

  @Autowired
  lateinit var deliveryConfigRepository: InMemoryDeliveryConfigRepository

  @Autowired
  lateinit var resourceRepository: InMemoryResourceRepository

  @Autowired
  lateinit var artifactRepository: InMemoryArtifactRepository

  @Autowired
  lateinit var combinedRepository: KeelRepository

  @Autowired
  lateinit var actuationPauser: ActuationPauser

  @Autowired
  lateinit var jsonMapper: ObjectMapper

  @Autowired
  lateinit var yamlMapper: YAMLMapper

  class Fixture {
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
      Environment(
        name = name,
        constraints = if (name == "production") {
          setOf(
            DependsOnConstraint("staging"),
            ManualJudgementConstraint(),
            PipelineConstraint(pipelineId = "fakePipeline")
          )
        } else {
          emptySet()
        },
        resources = setOf(
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
        )
      )
    }

    val deliveryConfig = DeliveryConfig(
      name = "manifest",
      application = application,
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = environments.values.toSet()
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    before {
      clock.reset()
    }

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
        artifactRepository.store(artifact, "fnord-1.0.0-h0.a0a0a0a", ArtifactStatus.RELEASE)
        artifactRepository.store(artifact, "fnord-1.0.1-h1.b1b1b1b", ArtifactStatus.RELEASE)
        artifactRepository.store(artifact, "fnord-1.0.2-h2.c2c2c2c", ArtifactStatus.RELEASE)
        artifactRepository.store(artifact, "fnord-1.0.3-h3.d3d3d3d", ArtifactStatus.RELEASE)

        // with our fake clock moving forward, simulate artifact approvals and deployments
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "fnord-1.0.0-h0.a0a0a0a", "test")
        clock.tickHours(1) // 2020-03-25T01:00:00.00Z
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "fnord-1.0.0-h0.a0a0a0a", "staging")
        val productionDeployed = clock.tickHours(1) // 2020-03-25T02:00:00.00Z
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "fnord-1.0.0-h0.a0a0a0a", "production")
        clock.tickHours(1) // 2020-03-25T03:00:00.00Z
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "fnord-1.0.1-h1.b1b1b1b", "test")
        clock.tickHours(1) // 2020-03-25T04:00:00.00Z
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "fnord-1.0.1-h1.b1b1b1b", "staging")
        clock.tickHours(1) // 2020-03-25T05:00:00.00Z
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "fnord-1.0.2-h2.c2c2c2c", "test")
        artifactRepository.approveVersionFor(deliveryConfig, artifact, "fnord-1.0.3-h3.d3d3d3d", "test")

        deliveryConfigRepository.storeConstraintState(
          ConstraintState(
            deliveryConfigName = deliveryConfig.name,
            environmentName = "production",
            artifactVersion = "fnord-1.0.0-h0.a0a0a0a",
            type = "manual-judgement",
            status = ConstraintStatus.OVERRIDE_PASS,
            createdAt = clock.start,
            judgedAt = productionDeployed.minus(Duration.ofMinutes(30)),
            judgedBy = "lpollo@acme.com",
            comment = "Aye!"
          )
        )
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
                "hasManagedResources":true
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
                "hasManagedResources":true
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
            currentEnvironmentConstraints:
            - deliveryConfigName: "manifest"
              environmentName: "production"
              artifactVersion: "fnord-1.0.0-h0.a0a0a0a"
              type: "manual-judgement"
              status: "OVERRIDE_PASS"
              createdAt: "2020-03-25T00:00:00Z"
              judgedBy: "lpollo@acme.com"
              judgedAt: "2020-03-25T01:30:00Z"
              comment: "Aye!"
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
            currentEnvironmentConstraints:
            - deliveryConfigName: "manifest"
              environmentName: "production"
              artifactVersion: "fnord-1.0.0-h0.a0a0a0a"
              type: "manual-judgement"
              status: "OVERRIDE_PASS"
              createdAt: "2020-03-25T00:00:00Z"
              judgedBy: "lpollo@acme.com"
              judgedAt: "2020-03-25T01:30:00Z"
              comment: "Aye!"
            environments:
            - name: "test"
              artifacts:
              - name: "fnord"
                type: "deb"
                statuses:
                - "RELEASE"
                versions:
                  current: "fnord-1.0.2-h2.c2c2c2c"
                  pending: []
                  approved:
                  - "fnord-1.0.3-h3.d3d3d3d"
                  previous:
                  - "fnord-1.0.0-h0.a0a0a0a"
                  - "fnord-1.0.1-h1.b1b1b1b"
                  vetoed: []
              resources:
              - "ec2:cluster:test:fnord-test-west"
              - "ec2:cluster:test:fnord-test-east"
            - name: "staging"
              artifacts:
              - name: "fnord"
                type: "deb"
                statuses:
                - "RELEASE"
                versions:
                  current: "fnord-1.0.1-h1.b1b1b1b"
                  pending:
                  - "fnord-1.0.2-h2.c2c2c2c"
                  - "fnord-1.0.3-h3.d3d3d3d"
                  approved: []
                  previous:
                  - "fnord-1.0.0-h0.a0a0a0a"
                  vetoed: []
              resources:
              - "ec2:cluster:test:fnord-staging-west"
              - "ec2:cluster:test:fnord-staging-east"
            - name: "production"
              artifacts:
              - name: "fnord"
                type: "deb"
                statuses:
                - "RELEASE"
                versions:
                  current: "fnord-1.0.0-h0.a0a0a0a"
                  pending:
                  - "fnord-1.0.1-h1.b1b1b1b"
                  - "fnord-1.0.2-h2.c2c2c2c"
                  - "fnord-1.0.3-h3.d3d3d3d"
                  approved: []
                  previous: []
                  vetoed: []
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
            currentEnvironmentConstraints:
            - deliveryConfigName: "manifest"
              environmentName: "production"
              artifactVersion: "fnord-1.0.0-h0.a0a0a0a"
              type: "manual-judgement"
              status: "OVERRIDE_PASS"
              createdAt: "2020-03-25T00:00:00Z"
              judgedBy: "lpollo@acme.com"
              judgedAt: "2020-03-25T01:30:00Z"
              comment: "Aye!"
            artifacts:
            - name: "fnord"
              type: "deb"
              versions:
              - version: "fnord-1.0.3-h3.d3d3d3d"
                displayName: "1.0.3"
                environments:
                - name: "test"
                  state: "approved"
                - name: "staging"
                  state: "pending"
                - name: "production"
                  state: "pending"
                  statefulConstraints:
                  - type: "manual-judgement"
                    status: "NOT_EVALUATED"
                  - type: "pipeline"
                    status: "NOT_EVALUATED"
                build:
                  id: 3
                git:
                  commit: "d3d3d3d"
              - version: "fnord-1.0.2-h2.c2c2c2c"
                displayName: "1.0.2"
                environments:
                - name: "test"
                  state: "current"
                  deployedAt: "2020-03-25T05:00:00Z"
                - name: "staging"
                  state: "pending"
                - name: "production"
                  state: "pending"
                  statefulConstraints:
                  - type: "manual-judgement"
                    status: "NOT_EVALUATED"
                  - type: "pipeline"
                    status: "NOT_EVALUATED"
                build:
                  id: 2
                git:
                  commit: "c2c2c2c"
              - version: "fnord-1.0.1-h1.b1b1b1b"
                displayName: "1.0.1"
                environments:
                - name: "test"
                  state: "previous"
                  deployedAt: "2020-03-25T03:00:00Z"
                  replacedAt: "2020-03-25T05:00:00Z"
                  replacedBy: "fnord-1.0.2-h2.c2c2c2c"
                - name: "staging"
                  state: "current"
                  deployedAt: "2020-03-25T04:00:00Z"
                - name: "production"
                  state: "pending"
                  statefulConstraints:
                  - type: "manual-judgement"
                    status: "NOT_EVALUATED"
                  - type: "pipeline"
                    status: "NOT_EVALUATED"
                build:
                  id: 1
                git:
                  commit: "b1b1b1b"
              - version: "fnord-1.0.0-h0.a0a0a0a"
                displayName: "1.0.0"
                environments:
                - name: "test"
                  state: "previous"
                  deployedAt: "2020-03-25T00:00:00Z"
                  replacedAt: "2020-03-25T03:00:00Z"
                  replacedBy: "fnord-1.0.1-h1.b1b1b1b"
                - name: "staging"
                  state: "previous"
                  deployedAt: "2020-03-25T01:00:00Z"
                  replacedAt: "2020-03-25T04:00:00Z"
                  replacedBy: "fnord-1.0.1-h1.b1b1b1b"
                - name: "production"
                  state: "current"
                  deployedAt: "2020-03-25T02:00:00Z"
                  statefulConstraints:
                  - type: "manual-judgement"
                    status: "OVERRIDE_PASS"
                    startedAt: "2020-03-25T00:00:00Z"
                    judgedBy: "lpollo@acme.com"
                    judgedAt: "2020-03-25T01:30:00Z"
                    comment: "Aye!"
                  - type: "pipeline"
                    status: "NOT_EVALUATED"
                build:
                  id: 0
                git:
                  commit: "a0a0a0a"
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
