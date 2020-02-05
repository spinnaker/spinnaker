package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.ArtifactStatus.FINAL
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class ArtifactControllerTests {
  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var artifactRepository: InMemoryArtifactRepository

  @AfterEach
  fun clearRepository() {
    artifactRepository.dropAll()
  }

  @Test
  fun `can get the versions of an artifact`() {
    val artifact = DebianArtifact(name = "fnord", deliveryConfigName = "myconfig")
    with(artifactRepository) {
      register(artifact)
      store(artifact, "fnord-2.1.0-18ed1dc", FINAL)
      store(artifact, "fnord-2.0.0-608bd90", FINAL)
      store(artifact, "fnord-1.0.0-41595c4", FINAL)
    }

    val request = get("/artifacts/${artifact.name}/${artifact.type}")
      .accept(APPLICATION_YAML)
    mvc
      .perform(request)
      .andExpect(status().isOk)
      .andExpect(content().string(
        """---
          |- "fnord-2.1.0-18ed1dc"
          |- "fnord-2.0.0-608bd90"
          |- "fnord-1.0.0-41595c4"
        """.trimMargin()
      ))
  }

  @Test
  fun `versions empty for an artifact we're not tracking`() {
    val request = get("/artifacts/unregistered/deb")
      .accept(APPLICATION_YAML)
    mvc
      .perform(request)
      .andExpect(status().isOk)
      .andExpect(content().string(
        """--- []""".trimMargin()
      ))
  }
}
