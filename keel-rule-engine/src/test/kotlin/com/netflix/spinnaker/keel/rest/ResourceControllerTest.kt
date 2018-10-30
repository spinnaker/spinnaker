package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import com.oneeyedmen.minutest.junit.junitTests
import org.junit.jupiter.api.TestFactory
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

@RunWith(SpringRunner::class)
//@ExtendWith(SpringExtension::class)
@SpringBootTest(classes = [ResourceController::class])
@AutoConfigureMockMvc
internal class ResourceControllerTest {
  @Autowired
  lateinit var mvc: MockMvc

  @TestFactory
  fun resourceEndpoint() = junitTests<Unit> {
    mvc
      .perform(post("/resources").accept(APPLICATION_YAML))
      .andExpect(status().isOk)
      .andExpect(content().string("ok"))
  }
}
