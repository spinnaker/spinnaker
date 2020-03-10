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
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.resource
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
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

  object Fixture {
    const val application = "fnord"

    val artifact = DebianArtifact(name = application, deliveryConfigName = "manifest")

    val cluster = resource(
      kind = SPINNAKER_EC2_API_V1.qualify("cluster"),
      spec = ClusterSpec(
        moniker = Moniker(application, "api"),
        imageProvider = ArtifactImageProvider(deliveryArtifact = artifact),
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
        _defaults = ClusterSpec.ServerGroupSpec(
          launchConfiguration = ClusterSpec.LaunchConfigurationSpec(
            instanceType = "m4.2xlarge",
            ebsOptimized = true,
            iamRole = "fnordInstanceProfile",
            keyPair = "fnordKeyPair"
          )
        )
      )
    )

    private val testEnv = Environment(name = "test", resources = setOf(cluster))

    val deliveryConfig = DeliveryConfig(
      name = "manifest",
      application = application,
      serviceAccount = "keel@spinnaker",
      artifacts = setOf(artifact),
      environments = setOf(testEnv)
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    after {
      deliveryConfigRepository.dropAll()
      resourceRepository.dropAll()
      artifactRepository.dropAll()
      clearAllMocks()
    }

    context("application with delivery config exists") {
      before {
        deliveryConfigRepository.store(deliveryConfig)
        resourceRepository.store(cluster)
        resourceRepository.appendHistory(ResourceCreated(cluster))
        artifactRepository.register(artifact)
        artifactRepository.store(artifact, "1.0.0", ArtifactStatus.RELEASE)
        artifactRepository.store(artifact, "1.0.1", ArtifactStatus.RELEASE)
        artifactRepository.store(artifact, "1.0.2", ArtifactStatus.RELEASE)
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "1.0.0", "test")
        artifactRepository.markAsSuccessfullyDeployedTo(deliveryConfig, artifact, "1.0.1", "test")
        artifactRepository.approveVersionFor(deliveryConfig, artifact, "1.0.2", "test")
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
          .accept(MediaType.APPLICATION_JSON_VALUE)
        mvc
          .perform(request)
          .andExpect(status().isOk)
          .andExpect(content().json(
            """
              |{
              |"hasManagedResources":true,
              |"resources":[{"id":"${cluster.id}","kind":"${cluster.kind}","status":"CREATED"}]
              |}
            """.trimMargin()
          ))
      }

      test("can get environment summaries by application") {
        val request = get("/application/$application?entities=environments")
          .accept(MediaType.APPLICATION_JSON_VALUE)
        mvc
          .perform(request)
          .andExpect(status().isOk)
          .andDo { print(it.response.contentAsString) }
          .andExpect(content().json(
            """
            {
              "applicationPaused":false,
              "hasManagedResources":true,
              "environments":[
                {
                  "artifacts":[
                    {
                      "name":"fnord",
                      "type":"deb",
                      "statuses":[],
                      "versions":{
                        "current":"1.0.1",
                        "pending":[],
                        "approved":[
                          "1.0.2"
                        ],
                        "previous":[
                          "1.0.0"
                        ],
                        "vetoed":[]
                      }
                    }
                  ],
                  "name":"test",
                  "resources":[
                    "ec2:cluster:test:fnord-api"
                  ]
                }
              ]
            }
            """.trimMargin()
          ))
      }

      test("can get artifact summaries by application") {
        val request = get("/application/$application?entities=artifacts")
          .accept(MediaType.APPLICATION_JSON_VALUE)
        mvc
          .perform(request)
          .andExpect(status().isOk)
          .andDo { print(it.response.contentAsString) }
          .andExpect(content().json(
            """
            {
              "applicationPaused":false,
              "hasManagedResources":true,
              "artifacts":[
                {
                  "name":"fnord",
                  "type":"deb",
                  "versions":[
                    {
                      "version":"1.0.0",
                      "environments":[
                        {
                          "name":"test",
                          "state":"previous"
                        }
                      ]
                    },
                    {
                      "version":"1.0.1",
                      "environments":[
                        {
                          "name":"test",
                          "state":"current"
                        }
                      ]
                    },
                    {
                      "version":"1.0.2",
                      "environments":[
                        {
                          "name":"test",
                          "state":"approved"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent()
          ))
      }

      test("can get multiple types of summaries by application") {
        val request = get("/application/$application?entities=resources&entities=environments&entities=artifacts")
          .accept(MediaType.APPLICATION_JSON_VALUE)
        val result = mvc
          .perform(request)
          .andExpect(status().isOk)
          .andDo { print(it.response.contentAsString) }
          .andReturn()
        val response = configuredObjectMapper().readValue<Map<String, Any>>(result.response.contentAsString)
        expectThat(response.keys)
          .containsExactly(
            "applicationPaused",
            "hasManagedResources",
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
          .andDo { print(it.response.contentAsString) }
          .andReturn()
        val response = configuredObjectMapper().readValue<Map<String, Any>>(result.response.contentAsString)
        expectThat(response.keys)
          .containsExactly(
            "applicationPaused",
            "hasManagedResources",
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
    }
  }
}
