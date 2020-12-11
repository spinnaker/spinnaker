package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.core.api.ApplicationSummary
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.services.AdminService
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.ninjasquad.springmockk.MockkBean
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType.APPLICATION_JSON_VALUE
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class AdminControllerTests
@Autowired constructor(
  val mvc: MockMvc,
  val combinedRepository: KeelRepository,
  val actuationPauser: ActuationPauser
) : JUnit5Minutests {

  @MockkBean
  lateinit var adminService: AdminService

  companion object {
    const val application1 = "fnord"
    const val application2 = "fnord2"
  }

  fun tests() = rootContext<Collection<ApplicationSummary>> {
    context("a single application") {
      fixture {
        listOf(
          ApplicationSummary(
            deliveryConfigName = "$application1-manifest",
            application = application1,
            serviceAccount = "keel@spinnaker",
            apiVersion = "delivery.config.spinnaker.netflix.com/v1",
            isPaused = false
          )
        )
      }

      before {
        every { adminService.getManagedApplications() } returns this
      }

      test("can get basic summary of unpaused application") {
        val request = get("/admin/applications/")
          .accept(APPLICATION_JSON_VALUE)
        mvc
          .perform(request)
          .andExpect(status().isOk)
          .andExpect(
            content().json(
              """
              [{
                "deliveryConfigName": "fnord-manifest",
                "application": "fnord",
                "serviceAccount": "keel@spinnaker",
                "apiVersion": "delivery.config.spinnaker.netflix.com/v1",
                "isPaused":false
              }]
              """.trimIndent()
            )
          )
      }
    }

    context("more than one application") {
      fixture {
        listOf(
          ApplicationSummary(
            deliveryConfigName = "$application1-manifest",
            application = application1,
            serviceAccount = "keel@spinnaker",
            apiVersion = "delivery.config.spinnaker.netflix.com/v1",
            isPaused = false
          ),
          ApplicationSummary(
            deliveryConfigName = "$application2-manifest",
            application = application2,
            serviceAccount = "keel@spinnaker",
            apiVersion = "delivery.config.spinnaker.netflix.com/v1",
            isPaused = false
          )
        )
      }

      before {
        every { adminService.getManagedApplications() } returns this
      }

      test("can get basic summary of 2 applications") {
        val request = get("/admin/applications/")
          .accept(APPLICATION_JSON_VALUE)
        mvc
          .perform(request)
          .andExpect(status().isOk)
          .andExpect(
            content().json(
              """
              [
                {
                  "deliveryConfigName": "fnord-manifest",
                  "application": "fnord",
                  "serviceAccount": "keel@spinnaker",
                  "apiVersion": "delivery.config.spinnaker.netflix.com/v1",
                  "isPaused":false
                },{
                  "deliveryConfigName": "fnord2-manifest",
                  "application": "fnord2",
                  "serviceAccount": "keel@spinnaker",
                  "apiVersion": "delivery.config.spinnaker.netflix.com/v1",
                  "isPaused":false
                }
              ]
              """.trimIndent()
            )
          )
      }
    }

    context("no delivery config found") {
      fixture { emptyList() }

      before {
        every { adminService.getManagedApplications() } returns this
      }

      test("return an empty list") {
        val request = get("/admin/applications/")
          .accept(APPLICATION_JSON_VALUE)
        mvc
          .perform(request)
          .andExpect(status().isOk)
          .andExpect(content().json("[]"))
      }
    }
  }
}
