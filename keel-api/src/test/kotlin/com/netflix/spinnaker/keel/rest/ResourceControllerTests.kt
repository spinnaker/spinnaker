package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.actuation.ResourcePersister
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.persistence.NoSuchResourceName
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.resource
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
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import strikt.api.Assertion
import strikt.api.DescribeableBuilder
import strikt.api.expectThat
import strikt.assertions.isNotNull

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class, DummyResourceConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class ResourceControllerTests {
  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var resourceRepository: InMemoryResourceRepository

  @MockkBean
  lateinit var authorizationSupport: AuthorizationSupport

  @MockkBean
  lateinit var resourcePersister: ResourcePersister

  var resource = resource()

  @AfterEach
  fun clearRepository() {
    resourceRepository.dropAll()
  }

  @Test
  fun `can create a resource as YAML`() {
    every { resourcePersister.upsert(any<SubmittedResource<ResourceSpec>>()) } returns resource
    every { authorizationSupport.userCanModifySpec("keel@spinnaker", any()) } returns true

    val request = post("/resources")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |apiVersion: test.spinnaker.netflix.com/v1
          |metadata:
          |  serviceAccount: keel@spinnaker
          |kind: whatever
          |spec:
          |  data: o hai"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isOk)

    verify { resourcePersister.upsert(match<SubmittedResource<DummyResourceSpec>> { it.spec.data == "o hai" }) }
  }

  @Test
  fun `can create a resource as JSON`() {
    every { resourcePersister.upsert(any<SubmittedResource<DummyResourceSpec>>()) } returns resource
    every { authorizationSupport.userCanModifySpec("keel@spinnaker", any()) } returns true

    val request = post("/resources")
      .accept(APPLICATION_JSON)
      .contentType(APPLICATION_JSON)
      .content(
        """{
          |  "apiVersion": "test.spinnaker.netflix.com/v1",
          |  "kind": "whatever",
          |  "metadata": {
          |    "serviceAccount": "keel@spinnaker"
          |  },
          |  "spec": {
          |    "data": "o hai"
          |  }
          |}"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isOk)

    verify {
      resourcePersister.upsert(match<SubmittedResource<out ResourceSpec>> {
        (it.spec as? DummyResourceSpec)?.data == "o hai"
      })
    }
  }

  @Test
  fun `can't create a resource when unauthorized`() {
    every { authorizationSupport.userCanModifySpec("keel@spinnaker", any()) } returns false

    val request = post("/resources")
      .accept(APPLICATION_JSON)
      .contentType(APPLICATION_JSON)
      .content(
        """{
          |  "apiVersion": "test.spinnaker.netflix.com/v1",
          |  "kind": "whatever",
          |  "metadata": {
          |  "serviceAccount": "keel@spinnaker"
          |  },
          |  "spec": {
          |    "data": "o hai"
          |  }
          |}"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isForbidden)
  }

  @Test
  fun `can update a resource`() {
    every { resourcePersister.upsert(any<SubmittedResource<ResourceSpec>>()) } returns resource
    every { authorizationSupport.userCanModifySpec("keel@spinnaker", any()) } returns true

    val request = post("/resources")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |apiVersion: test.spinnaker.netflix.com/v1
          |metadata:
          |  serviceAccount: keel@spinnaker
          |kind: whatever
          |spec:
          |  data: kthxbye"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isOk)

    verify { resourcePersister.upsert(match<SubmittedResource<DummyResourceSpec>> { it.spec.data == "kthxbye" }) }
  }

  @Test
  fun `attempting to update an unknown resource results in a 404`() {
    every { resourcePersister.upsert(any<SubmittedResource<ResourceSpec>>()) } throws NoSuchResourceName(resource.name)
    every { authorizationSupport.userCanModifySpec("keel@spinnaker", any()) } returns true

    val request = post("/resources")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |apiVersion: test.spinnaker.netflix.com/v1
          |metadata:
          |  serviceAccount: keel@spinnaker
          |kind: whatever
          |spec:
          |  data: kthxbye"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isNotFound)
  }

  @Test
  fun `an invalid request body results in an HTTP 400`() {
    every { authorizationSupport.userCanModifySpec("keel@spinnaker", any()) } returns true
    val request = post("/resources")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |apiVersion: test.spinnaker.netflix.com/v1
          |kind: whatever
          |metadata:
          |  serviceAccount: keel@spinnaker
          |  name: i-should-not-be-naming-my-resources-that-is-keels-job
          |spec:
          |  data: o hai"""
          .trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isBadRequest)
  }

  @Test
  fun `can get a resource as YAML`() {
    resourceRepository.store(resource)

    val request = get("/resources/${resource.name}")
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
    every { resourcePersister.delete(resource.name) } returns resource
    every { authorizationSupport.userCanModifyResource(resource.name.toString()) } returns true
    resourceRepository.store(resource)

    val request = delete("/resources/${resource.name}")
      .accept(APPLICATION_YAML)
    mvc
      .perform(request)
      .andExpect(status().isOk)

    verify { resourcePersister.delete(resource.name) }

    // clean up after the test
    resourceRepository.delete(resource.name)
  }

  @Test
  fun `unknown resource name results in a 404`() {
    every { authorizationSupport.userCanModifySpec("keel@spinnaker", any()) } returns true
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
