package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.redis.spring.MockEurekaConfiguration
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  properties = [
    "clouddriver.baseUrl=https://localhost:8081",
    "orca.baseUrl=https://localhost:8082",
    "front50.baseUrl=https://localhost:8083"
  ],
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
  fun `can register a new artifact`() {
    val request = post("/artifacts")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |name: fnord
          |type: DEB
        """.trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isCreated)
  }

  @Test
  fun `if an artifact is already registered endpoint responds with a conflict`() {
    artifactRepository.store(DeliveryArtifact("fnord", DEB))

    val request = post("/artifacts")
      .accept(APPLICATION_YAML)
      .contentType(APPLICATION_YAML)
      .content(
        """---
          |name: fnord
          |type: DEB
        """.trimMargin()
      )
    mvc
      .perform(request)
      .andExpect(status().isConflict)
  }
}
