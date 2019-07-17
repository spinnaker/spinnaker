package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.keel.redis.spring.MockEurekaConfiguration
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.junit.jupiter.api.extension.ExtendWith
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.succeeded

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class, TestConfiguration::class],
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

  fun tests() = rootContext<ResultActions> {
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
        |    spec: resource in test
        |- name: prod
        |  resources:
        |  - apiVersion: test.spinnaker.netflix.com/v1
        |    kind: whatever
        |    metadata:
        |      serviceAccount: keel@spinnaker
        |    spec: resource in prod
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
        |          "spec": "resource in test"
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
        |          "spec": "resource in prod"
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
      context("persisting a delivery config as YAML") {
        fixture {
          val request = post("/delivery-configs")
            .accept(contentType)
            .contentType(contentType)
            .content(payload)

          mvc.perform(request)
        }

        after {
          deliveryConfigRepository.dropAll()
          resourceRepository.dropAll()
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

private class TestConfiguration {
  @Bean
  fun whateverResourceHandler() = object : ResourceHandler<String> {
    override val apiVersion: ApiVersion = SPINNAKER_API_V1.subApi("test")

    override val supportedKind: Pair<ResourceKind, Class<String>> =
      ResourceKind("test", "whatever", "whatevers") to String::class.java

    override val objectMapper: ObjectMapper = configuredObjectMapper()

    override val normalizers: List<ResourceNormalizer<*>> = emptyList()

    override fun generateName(spec: String): ResourceName =
      ResourceName("test:whatever:${spec.hashCode()}")

    override suspend fun current(resource: Resource<String>): String? {
      TODO("not implemented")
    }

    override suspend fun delete(resource: Resource<String>) {
      TODO("not implemented")
    }

    override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }
  }
}
