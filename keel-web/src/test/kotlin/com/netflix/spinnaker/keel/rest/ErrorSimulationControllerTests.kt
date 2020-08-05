package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
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
@EnableAutoConfiguration(exclude = [TaskSchedulingAutoConfiguration::class])
internal class ErrorSimulationControllerTests {
  @Autowired
  lateinit var mvc: MockMvc

  @Test
  fun `error endpoint returns a 5xx server error`() {
    val request = get("/test/error")

    mvc
      .perform(request)
      .andExpect(status().is5xxServerError)
  }
}
