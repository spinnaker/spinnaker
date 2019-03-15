package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.annealing.ResourcePersister
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.events.ResourceDeleted
import com.netflix.spinnaker.keel.persistence.ResourceState.Diff
import com.netflix.spinnaker.keel.persistence.ResourceState.Ok
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.redis.spring.MockEurekaConfiguration
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import com.netflix.spinnaker.time.MutableClock
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.stub
import com.nhaarman.mockitokotlin2.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import strikt.api.Assertion
import strikt.api.DescribeableBuilder
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.isNotNull
import strikt.jackson.has
import strikt.jackson.isArray
import strikt.jackson.isObject
import java.time.Duration

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class, MockTimeConfiguration::class],
  properties = [
    "clouddriver.baseUrl=https://localhost:8081",
    "orca.baseUrl=https://localhost:8082",
    "front50.baseUrl=https://localhost:8083"
  ],
  webEnvironment = RANDOM_PORT
)
@AutoConfigureMockMvc
internal class ResourceControllerTests {
  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var resourceRepository: InMemoryResourceRepository

  @MockBean
  lateinit var resourcePersister: ResourcePersister

  @Autowired
  lateinit var clock: MutableClock

  var resource = Resource(
    apiVersion = ApiVersion("ec2.spinnaker.netflix.com/v1"),
    kind = "securityGroup",
    metadata = ResourceMetadata(
      name = ResourceName("ec2:securityGroup:test:us-west-2:keel"),
      uid = randomUID()
    ),
    spec = "mockingThis"
  )

  @AfterEach
  fun clearRepository() {
    resourceRepository.dropAll()
  }

  @Test
  fun `can create a resource as YAML`() {
    resourcePersister.stub {
      on { handle(any()) } doReturn resource
    }

    val request = post("/resources")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |apiVersion: ec2.spinnaker.netflix.com/v1
          |kind: securityGroup
          |spec:
          |  account: test
          |  region: us-west-2
          |  name: keel"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isCreated)
  }

  @Test
  fun `can create a resource as JSON`() {
    resourcePersister.stub {
      on { handle(any()) } doReturn resource
    }

    val request = post("/resources")
      .accept(APPLICATION_JSON)
      .contentType(APPLICATION_JSON)
      .content(
        """{
          |  "apiVersion": "ec2.spinnaker.netflix.com/v1",
          |  "kind": "securityGroup",
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
      .andExpect(status().isCreated)
  }

  @Test
  fun `an invalid request body results in an HTTP 400`() {
    val request = post("/resources")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |apiVersion: ec2.spinnaker.netflix.com/v1
          |kind: securityGroup
          |metadata:
          |  name: i-should-not-be-naming-my-resources-that-is-keels-job
          |spec:
          |  account: test
          |  region: us-west-2
          |  name: keel"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `can get a resource as YAML`() {
    resourceRepository.store(resource)

    val request = get("/resources/${resource.metadata.name}")
      .accept(APPLICATION_YAML)
    val result = mvc
      .perform(request)
      .andExpect(status().isOk)
      .andReturn()
    expectThat(result.response)
      .contentType
      .isNotNull()
      .isCompatibleWith(APPLICATION_YAML)
  }

  @Test
  fun `can delete a resource`() {
    resourcePersister.stub {
      on {
        handle(any())
      } doReturn resource
    }

    resourceRepository.store(resource)

    val request = delete("/resources/${resource.metadata.name}")
      .accept(APPLICATION_YAML)
    mvc
      .perform(request)
      .andExpect(status().isOk)

    verify(resourcePersister).handle(ResourceDeleted(resource.metadata.name))

    //clean up after the test
    resourceRepository.delete(resource.metadata.name)
  }

  @Test
  fun `unknown resource name results in a 404`() {
    val request = get("/resources/i-do-not-exist")
      .accept(APPLICATION_YAML)
    mvc
      .perform(request)
      .andExpect(status().isNotFound)
  }

  @Test
  fun `can get state history for a resource`() {
    with(resourceRepository) {
      store(resource)
      sequenceOf(Ok, Diff, Ok).forEach {
        clock.incrementBy(Duration.ofMinutes(10))
        updateState(resource.metadata.uid, it)
      }
    }

    val request = get("/resources/${resource.metadata.name}/history")
      .accept(APPLICATION_YAML)
    val result = mvc
      .perform(request)
      .andExpect(status().isOk)
      .andReturn()
    expectThat(result.response.contentAsTree)
      .isArray()
      .hasSize(4)
      .all {
        isObject()
          .has("state")
          .has("timestamp")
      }
  }
}

private val Assertion.Builder<MockHttpServletResponse>.contentType: DescribeableBuilder<MediaType?>
  get() = get { contentType?.let(MediaType::parseMediaType) }

@Suppress("UNCHECKED_CAST")
private fun <T : MediaType?> Assertion.Builder<T>.isCompatibleWith(expected: MediaType): Assertion.Builder<MediaType> =
  assertThat("is compatible with $expected") {
    it?.isCompatibleWith(expected) ?: false
  } as Assertion.Builder<MediaType>

private fun Assertion.Builder<ArrayNode>.hasSize(expected: Int): Assertion.Builder<ArrayNode> =
  assert("has $expected elements") { subject ->
    if (subject.size() == expected) pass()
    else fail(subject.size())
  }

private val MockHttpServletResponse.contentAsTree: JsonNode
  get() = configuredYamlMapper().readTree(contentAsString)

@Configuration
class MockTimeConfiguration {
  @Bean
  @Primary
  fun clock() = MutableClock()
}
