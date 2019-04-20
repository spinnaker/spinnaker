package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryArtifactVersion
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
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.net.URI

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
    artifactRepository.register(DeliveryArtifact("fnord", DEB))

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

  @Test
  fun `can get the versions of an artifact`() {
    val artifact = DeliveryArtifact("fnord", DEB)
    with(artifactRepository) {
      register(artifact)
      store(DeliveryArtifactVersion(artifact, "1.0", URI.create("https://my.jenkins.master/builds/1")))
      store(DeliveryArtifactVersion(artifact, "2.0", URI.create("https://my.jenkins.master/builds/2")))
      store(DeliveryArtifactVersion(artifact, "2.1", URI.create("https://my.jenkins.master/builds/3")))
    }

    val request = get("/artifacts/${artifact.name}/${artifact.type}")
      .accept(APPLICATION_YAML)
    mvc
      .perform(request)
      .andExpect(status().isOk)
      .andExpect(content().string(
        """---
          |- artifact:
          |    name: "fnord"
          |    type: "DEB"
          |  version: "2.1"
          |  provenance: "https://my.jenkins.master/builds/3"
          |- artifact:
          |    name: "fnord"
          |    type: "DEB"
          |  version: "2.0"
          |  provenance: "https://my.jenkins.master/builds/2"
          |- artifact:
          |    name: "fnord"
          |    type: "DEB"
          |  version: "1.0"
          |  provenance: "https://my.jenkins.master/builds/1"
        """.trimMargin()
      ))
  }
}
