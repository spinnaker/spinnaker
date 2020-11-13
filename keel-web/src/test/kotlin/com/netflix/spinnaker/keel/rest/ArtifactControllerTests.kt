package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.artifacts.DEBIAN
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.persistence.KeelRepository
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import com.ninjasquad.springmockk.MockkBean
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import io.mockk.every
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.EnableAutoConfiguration
import org.springframework.boot.autoconfigure.task.TaskSchedulingAutoConfiguration
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
@EnableAutoConfiguration(exclude = [TaskSchedulingAutoConfiguration::class])
internal class ArtifactControllerTests : JUnit5Minutests {
  @Autowired
  lateinit var mvc: MockMvc

  @MockkBean
  lateinit var repository: KeelRepository

  @Autowired
  lateinit var jsonMapper: ObjectMapper

  @MockkBean
  lateinit var authorizationSupport: AuthorizationSupport

  fun tests() = rootContext {
    after {
      clearAllMocks()
    }
  }
}
