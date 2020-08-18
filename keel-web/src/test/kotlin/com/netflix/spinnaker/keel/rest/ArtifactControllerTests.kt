package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.KeelApplication
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

    test("can get the versions of an artifact") {
      val artifact = DebianArtifact(
        name = "fnord",
        deliveryConfigName = "myconfig",
        vmOptions = VirtualMachineOptions(
          baseOs = "bionic",
          regions = setOf("us-west-2")
        )
      )

      every { repository.artifactVersions(artifact.name, artifact.type) } returns listOf(
        "fnord-2.1.0-18ed1dc",
        "fnord-2.0.0-608bd90",
        "fnord-1.0.0-41595c4"
      )

      val request = get("/artifacts/${artifact.name}/${artifact.type}")
        .accept(APPLICATION_YAML)
      mvc
        .perform(request)
        .andExpect(status().isOk)
        .andExpect(
          content().string(
            """---
            |- "fnord-2.1.0-18ed1dc"
            |- "fnord-2.0.0-608bd90"
            |- "fnord-1.0.0-41595c4"
          """.trimMargin()
          )
        )
    }

    test("versions empty for an artifact we're not tracking") {
      every { repository.artifactVersions(any(), any()) } returns emptyList()

      val request = get("/artifacts/unregistered/deb")
        .accept(APPLICATION_YAML)
      mvc
        .perform(request)
        .andExpect(status().isOk)
        .andExpect(
          content().string(
            """--- []""".trimMargin()
          )
        )
    }
  }
}
