package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.spring.test.DisableSpringScheduling
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.ninjasquad.springmockk.MockkBean
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.test.web.servlet.MockMvc

@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
@DisableSpringScheduling
internal class ArtifactControllerTests
@Autowired constructor(
  val mvc: MockMvc,
  val jsonMapper: ObjectMapper
) : JUnit5Minutests {

  @MockkBean
  lateinit var repository: KeelRepository

  @MockkBean
  lateinit var authorizationSupport: AuthorizationSupport

  fun tests() = rootContext {
    after {
      clearAllMocks()
    }
  }
}
