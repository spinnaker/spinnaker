package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Moniker
import com.netflix.spinnaker.keel.api.SubnetAwareLocations
import com.netflix.spinnaker.keel.api.SubnetAwareRegionSpec
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.RELEASE
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.api.ec2.ClusterSpec
import com.netflix.spinnaker.keel.api.ec2.ReferenceArtifactImageProvider
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.constraints.UpdatedConstraintStatus
import com.netflix.spinnaker.keel.core.api.DependsOnConstraint
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.core.api.ManualJudgementConstraint
import com.netflix.spinnaker.keel.core.api.PipelineConstraint
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action.READ
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action.WRITE
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.APPLICATION
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.time.MutableClock
import com.ninjasquad.springmockk.MockkBean
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.mockk
import java.time.Clock
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
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

  @MockkBean
  lateinit var authorizationSupport: AuthorizationSupport

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

  companion object {
    const val application = "fnord"
  }

  class Fixture {
    val artifact = DebianArtifact(
      name = application,
      deliveryConfigName = "manifest",
      vmOptions = VirtualMachineOptions(baseOs = "bionic", regions = setOf("us-west-2", "us-east-1")),
      reference = "fnord",
      statuses = setOf(RELEASE)
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
        authorizationSupport.allowAll()
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

    context("application is not managed") {
      before {
        authorizationSupport.allowAll()
      }

      test("API returns gracefully") {
        val request = get("/application/bananas")
          .accept(MediaType.APPLICATION_JSON_VALUE)
        mvc
          .perform(request)
          .andExpect(status().isOk)
          .andExpect(content().json(
            """
              {
                "hasManagedResources":false
              }
            """.trimIndent()
          ))
      }
    }

    context("API permission checks") {
      context("GET /application/fnord") {
        context("with no READ access to application") {
          before {
            authorizationSupport.denyApplicationAccess(READ, APPLICATION)
            authorizationSupport.allowCloudAccountAccess(READ, APPLICATION)
          }
          test("request is forbidden") {
            val request = get("/application/fnord")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no READ access to cloud account") {
          before {
            authorizationSupport.denyCloudAccountAccess(READ, APPLICATION)
            authorizationSupport.allowApplicationAccess(READ, APPLICATION)
          }
          test("request is forbidden") {
            val request = get("/application/fnord")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
      context("GET /application/fnord/environment/prod/constraints") {
        context("with no READ access to application") {
          before {
            authorizationSupport.denyApplicationAccess(READ, APPLICATION)
          }
          test("request is forbidden") {
            val request = get("/application/fnord/environment/prod/constraints")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
      context("POST /application/fnord/environment/prod/constraint") {
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, APPLICATION)
            authorizationSupport.allowServiceAccountAccess()
          }
          test("request is forbidden") {
            val request = post("/application/fnord/environment/prod/constraint").addData(jsonMapper,
              UpdatedConstraintStatus("manual-judgement", "prod", OVERRIDE_PASS)
            )
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no access to service account") {
          before {
            authorizationSupport.denyServiceAccountAccess()
            authorizationSupport.allowApplicationAccess(WRITE, APPLICATION)
          }
          test("request is forbidden") {
            val request = post("/application/fnord/environment/prod/constraint").addData(jsonMapper,
              UpdatedConstraintStatus("manual-judgement", "prod", OVERRIDE_PASS)
            )
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
      context("POST /application/fnord/pause") {
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, APPLICATION)
          }
          test("request is forbidden") {
            val request = post("/application/fnord/pause")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
      context("DELETE /application/fnord/pause") {
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, APPLICATION)
            authorizationSupport.allowServiceAccountAccess()
          }
          test("request is forbidden") {
            val request = delete("/application/fnord/pause")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no access to service account") {
          before {
            authorizationSupport.denyServiceAccountAccess()
            authorizationSupport.allowApplicationAccess(WRITE, APPLICATION)
          }
          test("request is forbidden") {
            val request = delete("/application/fnord/pause")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }

      context("POST /application/fnord/pin") {
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, APPLICATION)
            authorizationSupport.allowServiceAccountAccess()
          }
          test("request is forbidden") {
            val request = post("/application/fnord/pin").addData(jsonMapper,
              EnvironmentArtifactPin("test", "ref", "deb", "0.0.1", null)
            )
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no access to service account") {
          before {
            authorizationSupport.denyServiceAccountAccess()
            authorizationSupport.allowApplicationAccess(WRITE, APPLICATION)
          }
          test("request is forbidden") {
            val request = post("/application/fnord/pin").addData(jsonMapper,
              EnvironmentArtifactPin("test", "ref", "deb", "0.0.1", null)
            )
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
      context("DELETE /application/fnord/pin/test") {
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, APPLICATION)
            authorizationSupport.allowServiceAccountAccess()
          }
          test("request is forbidden") {
            val request = delete("/application/fnord/pin/test")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no access to service account") {
          before {
            authorizationSupport.denyServiceAccountAccess()
            authorizationSupport.allowApplicationAccess(WRITE, APPLICATION)
          }
          test("request is forbidden") {
            val request = delete("/application/fnord/pin/test")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }

      context("POST /application/fnord/veto") {
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, APPLICATION)
            authorizationSupport.allowServiceAccountAccess()
          }
          test("request is forbidden") {
            val request = post("/application/fnord/veto").addData(jsonMapper,
              EnvironmentArtifactVeto("test", "ref", "0.0.1")
            )
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no access to service account") {
          before {
            authorizationSupport.denyServiceAccountAccess()
            authorizationSupport.allowApplicationAccess(WRITE, APPLICATION)
          }
          test("request is forbidden") {
            val request = post("/application/fnord/veto").addData(jsonMapper,
              EnvironmentArtifactVeto("test", "ref", "0.0.1")
            )
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
      context("DELETE /application/fnord/veto/test/ref/0.0.1") {
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, APPLICATION)
            authorizationSupport.allowServiceAccountAccess()
          }
          test("request is forbidden") {
            val request = delete("/application/fnord/veto/test/ref/0.0.1")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no access to service account") {
          before {
            authorizationSupport.denyServiceAccountAccess()
            authorizationSupport.allowApplicationAccess(WRITE, APPLICATION)
          }
          test("request is forbidden") {
            val request = delete("/application/fnord/veto/test/ref/0.0.1")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
      context("GET /application/fnord/config") {
        context("with no READ access to application") {
          before {
            authorizationSupport.denyApplicationAccess(READ, APPLICATION)
          }
          test("request is forbidden") {
            val request = get("/application/fnord/config")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
    }
  }
}
