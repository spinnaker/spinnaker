package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.spring.test.DisableSpringScheduling
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  properties = ["tests.error-simulation=true"],
  webEnvironment = SpringBootTest.WebEnvironment.MOCK
)

@AutoConfigureMockMvc
@DisableSpringScheduling
internal class ErrorSimulationControllerTests
@Autowired constructor(val mvc: MockMvc) {

  @Test
  fun `error endpoint returns a 5xx server error`() {
    val request = get("/test/error")

    mvc
      .perform(request)
      .andExpect(status().is5xxServerError)
  }
}
