package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.constraints.ConstraintStatus.OVERRIDE_PASS
import com.netflix.spinnaker.keel.constraints.UpdatedConstraintStatus
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactPin
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action.READ
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action.WRITE
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.APPLICATION
import com.netflix.spinnaker.keel.services.ApplicationService
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.ninjasquad.springmockk.MockkBean
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.every
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
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
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class ApplicationControllerTests : JUnit5Minutests {
  @MockkBean
  lateinit var authorizationSupport: AuthorizationSupport

  @Autowired
  lateinit var mvc: MockMvc

  @MockkBean
  lateinit var applicationService: ApplicationService

  @MockkBean
  lateinit var actuationPauser: ActuationPauser

  @Autowired
  lateinit var jsonMapper: ObjectMapper

  companion object {
    const val application = "fnord"
    const val user = "keel@keel.io"
  }

  fun tests() = rootContext {
    after {
      clearAllMocks()
    }

    context("application with delivery config exists") {
      before {
        authorizationSupport.allowAll()
        every { applicationService.hasManagedResources(application) } returns true
        every { applicationService.getConstraintStatesFor(application) } returns emptyList()
        every { applicationService.getResourceSummariesFor(application) } returns emptyList()
        every { applicationService.getEnvironmentSummariesFor(application) } returns emptyList()
        every { applicationService.getArtifactSummariesFor(application) } returns emptyList()
      }

      context("with un-paused application") {
        before {
          every { actuationPauser.applicationIsPaused(application) } returns false
        }

        test("can get basic summary by application") {
          val request = get("/application/$application")
            .accept(APPLICATION_JSON_VALUE)
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

        test("returns bad request for unknown entities") {
          val request = get("/application/$application?entities=bananas")
            .accept(APPLICATION_JSON_VALUE)
          mvc.perform(request)
            .andExpect(status().isBadRequest)
        }

        test("can get multiple types of summaries by application") {
          val request = get("/application/$application?entities=resources&entities=environments&entities=artifacts")
            .accept(APPLICATION_JSON_VALUE)
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
            .accept(APPLICATION_JSON_VALUE)
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

        test("is backwards-compatible with older version of the API") {
          var request = get("/application/$application?includeDetails=false")
            .accept(APPLICATION_JSON_VALUE)
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
            .accept(APPLICATION_JSON_VALUE)
          result = mvc
            .perform(request)
            .andExpect(status().isOk)
            .andDo { print(it.response.contentAsString) }
            .andReturn()
          response = jsonMapper.readValue(result.response.contentAsString)
          expectThat(response.keys)
            .containsExactly(
              "applicationPaused",
              "hasManagedResources",
              "currentEnvironmentConstraints",
              "resources"
            )
        }
      }

      context("with paused application") {
        before {
          every {
            actuationPauser.applicationIsPaused(application)
          } returns true
        }

        test("reflects application paused status in basic summary") {
          val request = get("/application/$application")
            .accept(APPLICATION_JSON_VALUE)
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
    }

    context("application is not managed") {
      before {
        every { applicationService.hasManagedResources(any()) } returns false
        every { applicationService.getConstraintStatesFor(any()) } returns emptyList()
        every { actuationPauser.applicationIsPaused(any()) } returns false
        authorizationSupport.allowAll()
      }

      test("API returns gracefully") {
        val request = get("/application/bananas")
          .accept(APPLICATION_JSON_VALUE)
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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              EnvironmentArtifactVeto("test", "ref", "0.0.1", "me", "oopsie")
            )
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              EnvironmentArtifactVeto("test", "ref", "0.0.1", "me", "oopsie")
            )
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

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
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", user)

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }

      context("DELETE /application/fnord/config") {
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, APPLICATION)
          }
          test("request is forbidden") {
            val request = delete("/application/fnord/config")
              .accept(APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
    }
  }
}
