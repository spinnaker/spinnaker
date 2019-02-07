package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.persistence.NoSuchResourceException
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.redis.spring.EmbeddedRedisConfiguration
import com.netflix.spinnaker.keel.redis.spring.MockEurekaConfig
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML_VALUE
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.http.HttpHeaders.CONTENT_TYPE
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.util.*

@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfig::class, EmbeddedRedisConfiguration::class],
  properties = [
    "clouddriver.baseUrl=https://localhost:8081",
    "orca.baseUrl=https://localhost:8082"
  ],
  webEnvironment = RANDOM_PORT
)
@AutoConfigureMockMvc
internal class ResourceControllerTest {
  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var resourceRepository: ResourceRepository

  @Test
  fun `can create a resource as YAML`() {
    val request = post("/resources")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |apiVersion: spinnaker.netflix.com/v1
          |kind: ec2.SecurityGroup
          |metadata:
          |  name: ec2.SecurityGroup:keel:test:us-west-2:keel
          |spec:
          |  account: test
          |  region: us-west-2
          |  name: keel"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isOk)
  }

  @Test
  fun `can create a resource as JSON`() {
    val request = post("/resources")
      .accept(APPLICATION_JSON)
      .contentType(APPLICATION_JSON)
      .content(
        """{
          |  "apiVersion": "spinnaker.netflix.com/v1",
          |  "kind": "ec2.SecurityGroup",
          |  "metadata": {
          |    "name": "ec2.SecurityGroup:keel:test:us-west-2:keel"
          |  },
          |  "spec": {
          |    "account": "test",
          |    "region": "us-west-2",
          |    "name": "keel"
          |  }
          |}"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isOk)
  }

  @Test
  fun `can get a resource as YAML`() {
    val resource = Resource(
      apiVersion = SPINNAKER_API_V1,
      kind = "whatever",
      metadata = ResourceMetadata(
        name = ResourceName("my-resource"),
        uid = UUID.randomUUID(),
        resourceVersion = 1234L
      ),
      spec = "some spec content"
    )
    resourceRepository.store(resource)

    val request = get("/resources/${resource.metadata.name}")
      .accept(APPLICATION_YAML)
    val result = mvc
      .perform(request)
      .andExpect(status().isOk)
      .andReturn()
    expectThat(result.response.getHeaderValue(CONTENT_TYPE))
      .isA<String>()
      .get { MediaType.parseMediaType(this) }
      .get { "$type/$subtype" }
      .isEqualTo(APPLICATION_YAML_VALUE)
  }

  @Test
  @Ignore("TODO: Event needs to go via k8s for delete to happen")
  fun `can delete a resource`() {
    val resource = Resource(
      apiVersion = SPINNAKER_API_V1,
      kind = "whatever",
      metadata = ResourceMetadata(
        name = ResourceName("my-resource"),
        uid = UUID.randomUUID(),
        resourceVersion = 1234L
      ),
      spec = "some spec content"
    )
    resourceRepository.store(resource)

    val request = delete("/resources/${resource.metadata.name}")
      .accept(APPLICATION_YAML)
    mvc
      .perform(request)
      .andExpect(status().isOk)

    expectThrows<NoSuchResourceException> {
      resourceRepository.get<Any>(resource.metadata.name)
    }
  }

  @Test
  fun `unknown resource name results in a 404`() {
    val request = get("/resources/i-do-not-exist")
      .accept(APPLICATION_YAML)
    mvc
      .perform(request)
      .andExpect(status().isNotFound)
  }
}
