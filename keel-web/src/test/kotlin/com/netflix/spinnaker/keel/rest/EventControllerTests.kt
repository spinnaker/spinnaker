package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.events.PersistentEvent
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.persistence.NoSuchResourceId
import com.netflix.spinnaker.keel.persistence.ResourceRepository
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.Action.READ
import com.netflix.spinnaker.keel.rest.AuthorizationSupport.TargetEntity.RESOURCE
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import com.netflix.spinnaker.time.MutableClock
import com.ninjasquad.springmockk.MockkBean
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import strikt.api.expectThat
import strikt.assertions.containsExactly
import strikt.assertions.hasSize
import strikt.assertions.map
import strikt.jackson.hasSize
import strikt.jackson.isArray
import java.net.URI
import java.time.Clock
import java.time.Duration
import io.mockk.coEvery as every

@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class, MockTimeConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class EventControllerTests
@Autowired constructor(val mvc: MockMvc) : JUnit5Minutests {

  @MockkBean
  lateinit var resourceRepository: ResourceRepository

  @MockkBean
  lateinit var authorizationSupport: AuthorizationSupport

  companion object Fixture {
    val resource: Resource<*> = resource()
    val eventsUri: URI = URI.create("/resources/events/${resource.id}")
    private val TEN_MINUTES: Duration = Duration.ofMinutes(10)

    private val clock = MutableClock()
    fun nextTick(): Clock = clock.apply {
      incrementBy(TEN_MINUTES)
    }
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    context("no resource exists") {
      before {
        authorizationSupport.allowAll()
        every { resourceRepository.eventHistory(resource.id, any()) } throws NoSuchResourceId(resource.id)
      }

      test("event event history endpoint responds with 404") {
        val request = get("/resources/events/${resource.id}")
          .accept(APPLICATION_JSON)
        mvc
          .perform(request)
          .andExpect(status().isNotFound)
      }
    }

    context("a resource exists with events") {
      before {
        authorizationSupport.allowAll()
        every { resourceRepository.get(resource.id) } returns resource
        every { resourceRepository.eventHistory(resource.id, any()) } answers {
          (
            listOf(ResourceCreated(resource, nextTick())) + (1..3).flatMap {
              listOf(
                ResourceUpdated(resource, emptyMap(), nextTick()),
                ResourceDeltaDetected(resource, emptyMap(), nextTick()),
                ResourceActuationLaunched(resource, "a-plugin", listOf(Task(id = randomUID().toString(), name = "i did a thing")), nextTick()),
                ResourceDeltaResolved(resource, nextTick())
              )
            }
            )
            .sortedByDescending { it.timestamp }
            .take(secondArg())
        }
      }

      setOf(APPLICATION_YAML, APPLICATION_JSON).forEach { accept ->
        context("getting event history as $accept") {
          before { authorizationSupport.allowAll() }

          test("the list contains the most recent 10 events") {
            val request = get(eventsUri).accept(accept)
            val result = mvc
              .perform(request)
              .andExpect(status().isOk)
              .andExpect(content().contentTypeCompatibleWith(accept))
              .andReturn()
            expectThat(result.response.contentAsTree)
              .isArray()
              .hasSize(10)
          }

          test("every event specifies its type") {
            val request = get(eventsUri).accept(accept)
            val result = mvc
              .perform(request)
              .andExpect(status().isOk)
              .andExpect(content().contentTypeCompatibleWith(accept))
              .andReturn()
            expectThat(result.response.contentAs<List<PersistentEvent>>())
              .hasSize(10)
              .map { it.javaClass }
              .containsExactly(
                ResourceDeltaResolved::class.java,
                ResourceActuationLaunched::class.java,
                ResourceDeltaDetected::class.java,
                ResourceUpdated::class.java,
                ResourceDeltaResolved::class.java,
                ResourceActuationLaunched::class.java,
                ResourceDeltaDetected::class.java,
                ResourceUpdated::class.java,
                ResourceDeltaResolved::class.java,
                ResourceActuationLaunched::class.java
              )
          }
        }
      }

      test("can limit the number of events returned") {
        val limit = 2
        val request = get(eventsUri)
          .param("limit", limit.toString())
          .accept(APPLICATION_JSON)
        val result = mvc
          .perform(request)
          .andExpect(status().isOk)
          .andReturn()
        expectThat(result.response.contentAsTree)
          .isArray()
          .hasSize(limit)
      }
    }

    context("API permission checks") {
      context("GET /resources/events/${resource.id}") {
        context("with no READ access to application") {
          before {
            authorizationSupport.denyApplicationAccess(READ, RESOURCE)
            authorizationSupport.allowCloudAccountAccess(READ, RESOURCE)
          }
          test("request is forbidden") {
            val request = get("/resources/events/${resource.id}")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
        context("with no READ access to cloud account") {
          before {
            authorizationSupport.denyCloudAccountAccess(READ, RESOURCE)
            authorizationSupport.allowApplicationAccess(READ, RESOURCE)
          }
          test("request is forbidden") {
            val request = get("/resources/events/${resource.id}")
              .accept(MediaType.APPLICATION_JSON_VALUE)
              .header("X-SPINNAKER-USER", "keel@keel.io")

            mvc.perform(request).andExpect(status().isForbidden)
          }
        }
      }
    }
  }
}

private inline fun <reified T> MockHttpServletResponse.contentAs(): T =
  configuredYamlMapper().readValue<T>(contentAsString)

private val MockHttpServletResponse.contentAsTree: JsonNode
  get() = configuredYamlMapper().readTree(contentAsString)

@Configuration
class MockTimeConfiguration {
  @Bean
  @Primary
  fun clock() = MutableClock()
}
