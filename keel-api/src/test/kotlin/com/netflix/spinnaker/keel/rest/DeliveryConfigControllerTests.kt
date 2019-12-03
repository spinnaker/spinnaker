package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.actuation.ResourcePersister
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.ConstraintState
import com.netflix.spinnaker.keel.api.ConstraintStatus.PENDING
import com.netflix.spinnaker.keel.api.ConstraintStatus.PASS
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DependsOnConstraint
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.hamcrest.CoreMatchers.containsString
import org.hamcrest.CoreMatchers.not
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.hasSize
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.succeeded

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class, DummyResourceConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class DeliveryConfigControllerTests : JUnit5Minutests {

  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var deliveryConfigRepository: InMemoryDeliveryConfigRepository

  @Autowired
  lateinit var resourceRepository: InMemoryResourceRepository

  @Autowired
  lateinit var artifactRepository: InMemoryArtifactRepository

  @Autowired
  lateinit var resourcePersister: ResourcePersister

  fun tests() = rootContext {
    after {
      deliveryConfigRepository.dropAll()
      resourceRepository.dropAll()
      artifactRepository.dropAll()
    }

    context("getting a delivery config manifest") {
      before {
        resourcePersister.upsert(
          SubmittedDeliveryConfig(
            name = "keel-manifest",
            application = "keel",
            artifacts = setOf(DeliveryArtifact(
              name = "keel",
              type = DEB
            )),
            environments = setOf(
              SubmittedEnvironment(
                name = "test",
                resources = setOf(SubmittedResource(
                  apiVersion = SPINNAKER_API_V1.subApi("test"),
                  kind = "whatever",
                  metadata = mapOf("serviceAccount" to "keel@spinnaker"),
                  spec = DummyResourceSpec(data = "resource in test")
                ))
              ),
              SubmittedEnvironment(
                name = "prod",
                resources = setOf(SubmittedResource(
                  apiVersion = SPINNAKER_API_V1.subApi("test"),
                  kind = "whatever",
                  metadata = mapOf("serviceAccount" to "keel@spinnaker"),
                  spec = DummyResourceSpec(data = "resource in prod")
                )),
                constraints = setOf(DependsOnConstraint("test"))
              )
            )
          )
        )
      }

      setOf(APPLICATION_YAML, APPLICATION_JSON).forEach { contentType ->
        derivedContext<ResultActions>("getting a delivery config as $contentType") {
          fixture {
            val request = get("/delivery-configs/keel-manifest")
              .accept(contentType)

            mvc.perform(request)
          }

          test("the request is successful") {
            andExpect(status().isOk)
          }

          test("the response content type is correct") {
            andExpect(content().contentTypeCompatibleWith(contentType))
          }
        }
      }
    }

    context("submitting a delivery config manifest") {
      val yamlPayload =
        """---
        |name: keel-manifest
        |application: keel
        |artifacts:
        |- name: keel
        |  type: DEB
        |environments:
        |- name: test
        |  resources:
        |  - apiVersion: test.spinnaker.netflix.com/v1
        |    kind: whatever
        |    metadata:
        |      serviceAccount: keel@spinnaker
        |    spec:
        |      data: resource in test
        |      application: someapp
        |- name: prod
        |  constraints:
        |  - type: depends-on
        |    environment: test
        |  - type: allowed-times
        |    windows:
        |      - hours: 6-18
        |        days: mon-fri
        |  resources:
        |  - apiVersion: test.spinnaker.netflix.com/v1
        |    kind: whatever
        |    metadata:
        |      serviceAccount: keel@spinnaker
        |    spec:
        |      data: resource in prod
        |      application: someapp
        |"""
          .trimMargin()

      val jsonPayload =
        """{
        |  "name": "keel-manifest",
        |  "application": "keel",
        |  "artifacts": [
        |    {
        |      "name": "keel",
        |      "type": "DEB"
        |    }
        |  ],
        |  "environments": [
        |    {
        |      "name": "test",
        |      "resources": [
        |        {
        |          "apiVersion": "test.spinnaker.netflix.com/v1",
        |          "kind": "whatever",
        |          "metadata": {
        |            "serviceAccount": "keel@spinnaker"
        |          },
        |          "spec": {
        |            "data": "resource in test",
        |            "application": "someapp"
        |          }
        |        }
        |      ]
        |    },
        |    {
        |      "name": "prod",
        |      "constraints": [
        |        {
        |          "type": "depends-on",
        |          "environment": "test"
        |        },
        |        {
        |          "type": "allowed-times",
        |          "windows": [
        |            {
        |              "hours": "6-18",
        |              "days": "Monday-Friday",
        |              "tz": "America/Los_Angeles"
        |            }
        |          ]
        |        },
        |        {
        |          "type": "manual-judgement"
        |        }
        |      ],
        |      "resources": [
        |        {
        |          "apiVersion": "test.spinnaker.netflix.com/v1",
        |          "kind": "whatever",
        |          "metadata": {
        |            "serviceAccount": "keel@spinnaker"
        |          },
        |          "spec": {
        |            "data": "resource in prod",
        |            "application": "someapp"
        |          }
        |        }
        |      ]
        |    }
        |  ]
        |}"""
          .trimMargin()

      mapOf(
        APPLICATION_YAML to yamlPayload,
        APPLICATION_JSON to jsonPayload
      ).forEach { (contentType, payload) ->
        derivedContext<ResultActions>("persisting a delivery config as $contentType") {
          fixture {
            val request = post("/delivery-configs")
              .accept(contentType)
              .contentType(contentType)
              .content(payload)

            mvc.perform(request)
          }

          test("the request is successful") {
            andExpect(status().isOk)
          }

          test("the manifest is persisted") {
            expectCatching { deliveryConfigRepository.get("keel-manifest") }
              .succeeded()
          }

          test("each individual resource is persisted") {
            expectThat(resourceRepository.size()).isEqualTo(2)
          }

          test("prod constraint is persisted") {
            expectThat(
              deliveryConfigRepository
                .get("keel-manifest")
                .environments
                .find { it.name == "prod" }
                ?.constraints
            ).isNotNull()
          }

          context("no environments with stateful constraints have been evaluated") {
            test("no constraint state") {
              getProdConstraintResponse(contentType)
                // Response assertions that aren't dependent on contentType or deserialization
                .andExpect(status().isOk)
                .andExpect(content().string(not(containsString("status"))))

              val states = getProdConstraintStates(contentType)
              expectThat(states).isEmpty()
            }
          }

          derivedContext<ResultActions>("interacting with manual judgement") {
            fixture {
              val judgement = ConstraintState("keel-manifest", "prod", "keel-1.0.2", "manual-judgement", PENDING)
              deliveryConfigRepository.storeConstraintState(judgement)

              getProdConstraintResponse(contentType)
            }

            test("pending manual judgement") {
              andExpect(status().isOk)
              andExpect(content().string(containsString("manual-judgement")))
              andExpect(content().string(containsString("PENDING")))
            }

            derivedContext<ResultActions>("approving manual judgement") {
              fixture {
                val request = post(
                  "/delivery-configs/keel-manifest/environment/prod/constraint/manual-judgement" +
                    "?artifactVersion=keel-1.0.2&status=PASS")
                  .accept(contentType)
                  .contentType(contentType)
                  .header("X-SPINNAKER-USER", "keel")

                mvc.perform(request)
              }

              test("judgement is persisted") {
                andExpect(status().isOk)

                val judgement = deliveryConfigRepository
                  .getConstraintState("keel-manifest", "prod", "keel-1.0.2", "manual-judgement")

                expectThat(judgement).isNotNull()
                expectThat(judgement!!.status).isEqualTo(PASS)
                expectThat(judgement.judgedBy).isEqualTo("keel")
              }

              test("persisted judgement is in rest response") {
                val states = getProdConstraintStates(contentType)
                expectThat(states).hasSize(1)
                with(states.first()) {
                  expectThat(status).isEqualTo(PASS)
                  expectThat(judgedBy).isEqualTo("keel")
                }
              }
            }
          }
        }
      }
    }
  }

  private fun getProdConstraintResponse(contentType: MediaType) =
    mvc.perform(
      get("/delivery-configs/keel-manifest/environment/prod/constraints")
        .accept(contentType)
        .contentType(contentType)
    )

  private fun getProdConstraintStates(contentType: MediaType): List<ConstraintState> =
    getProdConstraintResponse(contentType)
      .andReturn()
      .response
      .contentAsString
      .let {
        if (contentType == APPLICATION_YAML) {
          configuredYamlMapper().readValue(it)
        } else {
          configuredObjectMapper().readValue(it)
        }
      }
}
