package com.netflix.spinnaker.keel.rest

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
  properties = ["tests.error-simulation=true"],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
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
