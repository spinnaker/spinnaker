package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.actuation.ResourcePersister
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceMetadata
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.persistence.NoSuchResourceName
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.redis.spring.MockEurekaConfiguration
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import strikt.api.Assertion
import strikt.api.DescribeableBuilder
import strikt.api.expectThat
import strikt.assertions.isNotNull

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  properties = [
    "clouddriver.baseUrl=https://localhost:8081",
    "orca.baseUrl=https://localhost:8082",
    "front50.baseUrl=https://localhost:8083"
  ],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class ResourceControllerTests {
  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var resourceRepository: InMemoryResourceRepository

  @MockkBean
  lateinit var resourcePersister: ResourcePersister

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
    every { resourcePersister.create(any()) } returns resource

    val request = post("/resources")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |apiVersion: test.spinnaker.netflix.com/v1
          |kind: whatever
          |spec: o hai"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isCreated)

    verify { resourcePersister.create(match { it.spec == "o hai" }) }
  }

  @Test
  fun `can create a resource as JSON`() {
    every { resourcePersister.create(any()) } returns resource

    val request = post("/resources")
      .accept(APPLICATION_JSON)
      .contentType(APPLICATION_JSON)
      .content(
        """{
          |  "apiVersion": "test.spinnaker.netflix.com/v1",
          |  "kind": "whatever",
          |  "spec": "o hai"
          |}"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isCreated)

    verify { resourcePersister.create(match { it.spec == "o hai" }) }
  }

  @Test
  fun `can update a resource`() {
    every { resourcePersister.update(resource.metadata.name, any()) } returns resource

    val request = put("/resources/${resource.metadata.name}")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |apiVersion: test.spinnaker.netflix.com/v1
          |kind: whatever
          |spec: kthxbye"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isOk)

    verify { resourcePersister.update(resource.metadata.name, match { it.spec == "kthxbye" }) }
  }

  @Test
  fun `attempting to update an unknown resource results in a 404`() {
    every { resourcePersister.update(resource.metadata.name, any()) } throws NoSuchResourceName(resource.metadata.name)

    val request = put("/resources/${resource.metadata.name}")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |apiVersion: test.spinnaker.netflix.com/v1
          |kind: whatever
          |spec: kthxbye"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isNotFound)
  }

  @Test
  fun `an invalid request body results in an HTTP 400`() {
    val request = post("/resources")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |apiVersion: test.spinnaker.netflix.com/v1
          |kind: whatever
          |metadata:
          |  name: i-should-not-be-naming-my-resources-that-is-keels-job
          |spec: o hai"""
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
    every { resourcePersister.delete(resource.metadata.name) } returns resource

    resourceRepository.store(resource)

    val request = delete("/resources/${resource.metadata.name}")
      .accept(APPLICATION_YAML)
    mvc
      .perform(request)
      .andExpect(status().isOk)

    verify { resourcePersister.delete(resource.metadata.name) }

    // clean up after the test
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
}

private val Assertion.Builder<MockHttpServletResponse>.contentType: DescribeableBuilder<MediaType?>
  get() = get { contentType?.let(MediaType::parseMediaType) }

@Suppress("UNCHECKED_CAST")
private fun <T : MediaType?> Assertion.Builder<T>.isCompatibleWith(expected: MediaType): Assertion.Builder<MediaType> =
  assertThat("is compatible with $expected") {
    it?.isCompatibleWith(expected) ?: false
  } as Assertion.Builder<MediaType>
