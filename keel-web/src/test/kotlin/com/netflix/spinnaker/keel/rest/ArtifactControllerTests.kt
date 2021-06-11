package com.netflix.spinnaker.keel.rest

import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.artifacts.PublishedArtifact
import com.netflix.spinnaker.keel.api.events.ArtifactPublishedEvent
import com.netflix.spinnaker.keel.api.scm.PrCreatedEvent
import com.netflix.spinnaker.keel.rest.ArtifactControllerTests.TestConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.just
import io.mockk.runs
import io.mockk.spyk
import io.mockk.verify
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.context.ApplicationEventPublisher
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.context.support.GenericApplicationContext
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(webEnvironment = MOCK, classes = [TestConfig::class, KeelApplication::class])
@AutoConfigureMockMvc
internal class ArtifactControllerTests
@Autowired constructor(
  val mvc: MockMvc,
) : JUnit5Minutests {

  // Hack to mock ApplicationEventPublisher, which is handled as a special case in Spring (the application
  // context implements the interface). See https://github.com/spring-projects/spring-boot/issues/6060
  class TestConfig {
    @Bean
    @Primary
    fun genericApplicationContext(gac: GenericApplicationContext): GenericApplicationContext {
      return spyk(gac) {
        every { publishEvent(any()) } just runs
      }
    }
  }

  @Autowired
  lateinit var eventPublisher: ApplicationEventPublisher

  object Fixture {
    val disguisedCodeEvent = EchoArtifactEvent(
      eventName = "test",
      payload = ArtifactPublishedEvent(
        artifacts = listOf(
          PublishedArtifact(
            name = "master:953910b24a776eceab03d4dcae8ac050b2e0b668",
            type = "pr_opened",
            reference = "https://stash/projects/ORG/repos/myrepo/commits/953910b24a776eceab03d4dcae8ac050b2e0b668",
            version = "953910b24a776eceab03d4dcae8ac050b2e0b668",
            provenance = "https://stash/projects/ORG/repos/myrepo/commits/953910b24a776eceab03d4dcae8ac050b2e0b668",
            metadata = mapOf(
              "repoKey" to "stash/org/myrepo",
              "prId" to "11494",
              "sha" to  "953910b24a776eceab03d4dcae8ac050b2e0b668",
              "branch" to "master"
            )
          )
        )
      )
    )

    val translatedCodeEvent = PrCreatedEvent(
      repoKey = "stash/org/myrepo",
      targetBranch = "master",
      sourceBranch = "N/A",
      pullRequestId = "11494"
    )
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    context("a code event disguised as an artifact event is received") {
      val request = MockMvcRequestBuilders.post("/artifacts/events")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .content(
          """
             {
               "payload": {
                 "artifacts": [
                   {
                      "type": "pr_opened",
                      "reference": "https://stash/projects/ORG/repos/myrepo/commits/953910b24a776eceab03d4dcae8ac050b2e0b668",
                      "branch": "master",
                      "name": "master:953910b24a776eceab03d4dcae8ac050b2e0b668",
                      "version": "953910b24a776eceab03d4dcae8ac050b2e0b668",
                      "metadata": {
                        "repoKey": "stash/org/myrepo",
                        "prId": "11494",
                        "sha": "953910b24a776eceab03d4dcae8ac050b2e0b668",
                        "branch": "master"
                      }
                   }
                 ]
               },
               "eventName": "test"
            }
            """.trimIndent()
        )

      val response = mvc.perform(request)

      test("request succeeds") {
        response.andExpect(status().isAccepted)
      }

      test("event is properly translated and published as code event") {
        verify(exactly = 1) {
          eventPublisher.publishEvent(translatedCodeEvent)
        }
      }

      test("original artifact event is not propagated") {
        verify(exactly = 0) {
          eventPublisher.publishEvent(disguisedCodeEvent)
        }
      }
    }
  }
}