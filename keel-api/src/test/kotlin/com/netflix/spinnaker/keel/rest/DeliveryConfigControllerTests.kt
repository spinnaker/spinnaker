package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.actuation.ResourcePersister
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.HasApplication
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.api.SubmittedMetadata
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
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
import strikt.assertions.isEqualTo
import strikt.assertions.succeeded

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class, DummyResourceConfiguration::class],
  properties = [
    "clouddriver.baseUrl=https://localhost:8081",
    "orca.baseUrl=https://localhost:8082",
    "front50.baseUrl=https://localhost:8083"
  ],
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
                  metadata = SubmittedMetadata("keel@spinnaker"),
                  spec = DummyResourceSpec(data = "resource in test")
                ))
              ),
              SubmittedEnvironment(
                name = "prod",
                resources = setOf(SubmittedResource(
                  apiVersion = SPINNAKER_API_V1.subApi("test"),
                  kind = "whatever",
                  metadata = SubmittedMetadata("keel@spinnaker"),
                  spec = DummyResourceSpec(data = "resource in prod")
                ))
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
        }
      }
    }
  }
}

internal data class DummyResource(
  val data: String = "some data",
  override val application: String = "someapp"
) : HasApplication
