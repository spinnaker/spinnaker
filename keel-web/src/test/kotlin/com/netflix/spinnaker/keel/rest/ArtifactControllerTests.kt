package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.artifacts.ArtifactStatus.FINAL
import com.netflix.spinnaker.keel.api.artifacts.DebianArtifact
import com.netflix.spinnaker.keel.api.artifacts.VirtualMachineOptions
import com.netflix.spinnaker.keel.core.api.EnvironmentArtifactVeto
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action.WRITE
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.DELIVERY_CONFIG
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import com.ninjasquad.springmockk.MockkBean
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.clearAllMocks
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.http.MediaType
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class ArtifactControllerTests : JUnit5Minutests {
  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var artifactRepository: InMemoryArtifactRepository

  @Autowired
  lateinit var jsonMapper: ObjectMapper

  @MockkBean
  lateinit var authorizationSupport: AuthorizationSupport

  fun tests() = rootContext {
    after {
      artifactRepository.dropAll()
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

    test("versions empty for an artifact we're not tracking") {
      val request = get("/artifacts/unregistered/deb")
        .accept(APPLICATION_YAML)
      mvc
        .perform(request)
        .andExpect(status().isOk)
        .andExpect(content().string(
          """--- []""".trimMargin()
        ))
    }

    context("API permission checks") {
      context("POST /artifacts/veto") {
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, DELIVERY_CONFIG)
            authorizationSupport.allowServiceAccountAccess(DELIVERY_CONFIG)
          }
          test("request is forbidden") {
            val request = post("/artifacts/veto").addData(jsonMapper,
              EnvironmentArtifactVeto("myconfig", "test", "ref", "deb", "0.0.1")
            )
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no access to service account") {
          before {
            authorizationSupport.denyServiceAccountAccess(DELIVERY_CONFIG)
            authorizationSupport.allowApplicationAccess(WRITE, DELIVERY_CONFIG)
          }
          test("request is forbidden") {
            val request = post("/artifacts/veto").addData(jsonMapper,
              EnvironmentArtifactVeto("myconfig", "test", "ref", "deb", "0.0.1")
            )
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
      context("DELETE /artifacts/veto/myconfig/test/deb/ref/0.0.1") {
        context("with no WRITE access to application") {
          before {
            authorizationSupport.denyApplicationAccess(WRITE, DELIVERY_CONFIG)
            authorizationSupport.allowServiceAccountAccess(DELIVERY_CONFIG)
          }
          test("request is forbidden") {
            val request = delete("/artifacts/veto/myconfig/test/deb/ref/0.0.1")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no access to service account") {
          before {
            authorizationSupport.denyServiceAccountAccess(DELIVERY_CONFIG)
            authorizationSupport.allowApplicationAccess(WRITE, DELIVERY_CONFIG)
          }
          test("request is forbidden") {
            val request = delete("/artifacts/veto/myconfig/test/deb/ref/0.0.1")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
    }
  }
}
