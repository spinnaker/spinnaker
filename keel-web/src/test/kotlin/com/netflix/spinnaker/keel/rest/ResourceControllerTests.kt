package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.diff.AdHocDiffer
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.persistence.NoSuchResourceId
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action.READ
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action.WRITE
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.APPLICATION
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.RESOURCE
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.test.submittedResource
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import com.ninjasquad.springmockk.MockkBean
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import strikt.api.Assertion.Builder
import strikt.api.DescribeableBuilder
import strikt.api.expectThat
import strikt.assertions.isNotNull

@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class, DummyResourceConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
@EnableAutoConfiguration(exclude = [TaskSchedulingAutoConfiguration::class])
internal class ResourceControllerTests : JUnit5Minutests {
  @Autowired
  lateinit var mvc: MockMvc

  @MockkBean
  lateinit var repository: KeelRepository

  @MockkBean
  lateinit var authorizationSupport: AuthorizationSupport

  @Qualifier("jsonMapper")
  @Autowired
  lateinit var jsonMapper: ObjectMapper

  @MockkBean
  lateinit var adHocDiffer: AdHocDiffer

  var resource = resource()

  val user = "keel@keel.io"

  fun tests() = rootContext {
    before {
      every { authorizationSupport.hasApplicationPermission(READ.name, RESOURCE.name, any()) } returns true
      every { authorizationSupport.hasCloudAccountPermission(READ.name, RESOURCE.name, any()) } returns true
    }

    test("an invalid request body results in an HTTP 400") {
      val request = post("/resources/diff")
        .accept(APPLICATION_YAML)
        .contentType(APPLICATION_YAML)
        .header("X-SPINNAKER-USER", "fzlem@netflix.com")
        .content(
          """---
          |metadata:
          |  name: i-forgot-my-kind
          |spec:
          |  data: o hai"""
            .trimMargin()
        )
      mvc
        .perform(request)
        .andExpect(status().isBadRequest)
    }

    test("can get a resource as YAML") {
      every { repository.getResource(resource.id) } returns resource

      val request = get("/resources/${resource.id}")
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

    test("unknown resource name results in a 404") {
      every { repository.getResource("i-do-not-exist") } throws NoSuchResourceId("i-do-not-exist")

      val request = get("/resources/i-do-not-exist")
        .accept(APPLICATION_YAML)
      mvc
        .perform(request)
        .andExpect(status().isNotFound)
    }

    context("API permission checks") {
      context("GET /resources/test:${resource.id}") {
        context("with no READ access to application") {
          before {
            authorizationSupport.denyApplicationAccess(READ, RESOURCE)
            authorizationSupport.allowCloudAccountAccess(READ, RESOURCE)
          }
          test("request is forbidden") {
            val request = get("/resources/test:${resource.id}")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no READ access to cloud account") {
          before {
            authorizationSupport.denyCloudAccountAccess(READ, RESOURCE)
            authorizationSupport.allowApplicationAccess(READ, RESOURCE)
          }
          test("request is forbidden") {
            val request = get("/resources/test:${resource.id}")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
      context("GET /resources/test:${resource.id}/status") {
        context("with no READ access to application") {
          before {
            authorizationSupport.denyApplicationAccess(READ, RESOURCE)
            authorizationSupport.allowCloudAccountAccess(READ, RESOURCE)
          }
          test("request is forbidden") {
            val request = get("/resources/test:${resource.id}/status")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no READ access to cloud account") {
          before {
            authorizationSupport.denyCloudAccountAccess(READ, RESOURCE)
            authorizationSupport.allowApplicationAccess(READ, RESOURCE)
          }
          test("request is forbidden") {
            val request = get("/resources/test:${resource.id}/status")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
      context("POST /resources/test:${resource.id}/pause") {
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, RESOURCE)
          }
          test("request is forbidden") {
            val request = post("/resources/test:${resource.id}/pause")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
      context("DELETE /resources/test:${resource.id}/pause") {
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, RESOURCE)
            authorizationSupport.allowServiceAccountAccess()
          }
          test("request is forbidden") {
            val request = delete("/resources/test:${resource.id}/pause")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no access to service account") {
          before {
            authorizationSupport.denyServiceAccountAccess()
            authorizationSupport.allowApplicationAccess(WRITE, RESOURCE)
          }
          test("request is forbidden") {
            val request = delete("/resources/test:${resource.id}/pause")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
      context("POST /resources/diff") {
        context("with no READ access to application") {
          before {
            authorizationSupport.denyApplicationAccess(READ, APPLICATION)
          }
          test("request is forbidden") {
            val request = post("/resources/diff").addData(jsonMapper, submittedResource())
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
    }
  }
}

private val Builder<MockHttpServletResponse>.contentType: DescribeableBuilder<MediaType?>
  get() = get { contentType?.let(MediaType::parseMediaType) }

@Suppress("UNCHECKED_CAST")
private fun <T : MediaType?> Builder<T>.isCompatibleWith(expected: MediaType): Builder<MediaType> =
  assertThat("is compatible with $expected") {
    it?.isCompatibleWith(expected) ?: false
  } as Builder<MediaType>
