package com.netflix.spinnaker.keel.rest

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.KeelApplication
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.actuation.Task
import com.netflix.spinnaker.keel.api.application
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.core.api.randomUID
import com.netflix.spinnaker.keel.events.ApplicationActuationPaused
import com.netflix.spinnaker.keel.events.ApplicationActuationResumed
import com.netflix.spinnaker.keel.events.PersistentEvent
import com.netflix.spinnaker.keel.events.ResourceActuationLaunched
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceDeltaDetected
import com.netflix.spinnaker.keel.events.ResourceDeltaResolved
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.events.ResourceValid
import com.netflix.spinnaker.keel.pause.ActuationPauser
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.serialization.configuredYamlMapper
import com.netflix.spinnaker.keel.spring.test.MockEurekaConfiguration
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.keel.yaml.APPLICATION_YAML
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import java.net.URI
import java.time.Duration
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.test.context.junit.jupiter.SpringExtension
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

@ExtendWith(SpringExtension::class)
@SpringBootTest(
  classes = [KeelApplication::class, MockEurekaConfiguration::class, MockTimeConfiguration::class],
  webEnvironment = MOCK
)
@AutoConfigureMockMvc
internal class EventControllerTests : JUnit5Minutests {
  @Autowired
  lateinit var mvc: MockMvc

  @Autowired
  lateinit var clock: MutableClock

  @Autowired
  lateinit var resourceRepository: InMemoryResourceRepository

  @Autowired
  lateinit var actuationPauser: ActuationPauser

  object Fixture {
    val resource: Resource<*> = resource()
    val eventsUri: URI = URI.create("/resources/events/${resource.id}")
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture }

    context("no resource exists") {
      test("event eventHistory endpoint responds with 404") {
        val request = get("/resources/events/${resource.id}")
          .accept(APPLICATION_JSON)
        mvc
          .perform(request)
          .andExpect(status().isNotFound)
      }
    }

    context("a resource exists with events") {
      before {
        with(resourceRepository) {
          store(resource)
          appendHistory(ResourceCreated(resource, clock))
          clock.incrementBy(TEN_MINUTES)
          repeat(3) {
            appendHistory(ResourceUpdated(resource, emptyMap(), clock))
            clock.incrementBy(TEN_MINUTES)
            appendHistory(ResourceDeltaDetected(resource, emptyMap(), clock))
            clock.incrementBy(TEN_MINUTES)
            appendHistory(ResourceActuationLaunched(resource, "a-plugin", listOf(Task(id = randomUID().toString(), name = "i did a thing")), clock))
            clock.incrementBy(TEN_MINUTES)
            appendHistory(ResourceDeltaResolved(resource, clock))
            clock.incrementBy(TEN_MINUTES)
          }
        }
      }

      after {
        resourceRepository.dropAll()
      }

      setOf(APPLICATION_YAML, APPLICATION_JSON).forEach { accept ->
        context("getting event history as $accept") {
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

    context("with application paused at various times") {
      before {
        with(resourceRepository) {
          dropAll()
          store(resource)
          appendHistory(ResourceCreated(resource, clock))
          clock.incrementBy(TEN_MINUTES)
          appendHistory(ResourceValid(resource, clock))
          clock.incrementBy(TEN_MINUTES)
          actuationPauser.pauseApplication(resource.application)
          clock.incrementBy(TEN_MINUTES)
          actuationPauser.resumeApplication(resource.application)
          clock.incrementBy(TEN_MINUTES)
          actuationPauser.pauseApplication(resource.application)
        }
      }

      test("has application paused and resumed events injected in the right positions") {
        val request = get(eventsUri).accept(APPLICATION_YAML)
        val result = mvc
          .perform(request)
          .andReturn()
        expectThat(result.response.contentAs<List<PersistentEvent>>())
          .map { it.javaClass }
          .containsExactly(
            ApplicationActuationPaused::class.java,
            ApplicationActuationResumed::class.java,
            ApplicationActuationPaused::class.java,
            ResourceValid::class.java,
            ResourceCreated::class.java
          )
      }
    }

    context("with a new resource created AFTER the application is paused") {
      before {
        with(resourceRepository) {
          dropAll()
          actuationPauser.pauseApplication(resource.application)
          clock.incrementBy(TEN_MINUTES)
          store(resource)
          appendHistory(ResourceCreated(resource, clock))
          clock.incrementBy(TEN_MINUTES)
          actuationPauser.resumeApplication(resource.application)
        }
      }

      test("has application resumed event injected in the right position") {
        val request = get(eventsUri).accept(APPLICATION_YAML)
        val result = mvc
          .perform(request)
          .andReturn()
        expectThat(result.response.contentAs<List<PersistentEvent>>())
          .map { it.javaClass }
          .containsExactly(
            ApplicationActuationResumed::class.java,
            ResourceCreated::class.java
          )
      }
    }

    context("with application pauses BEFORE and AFTER a new resource is created") {
      before {
        with(resourceRepository) {
          dropAll()
          actuationPauser.pauseApplication(resource.application)
          clock.incrementBy(TEN_MINUTES)
          store(resource)
          appendHistory(ResourceCreated(resource, clock))
          clock.incrementBy(TEN_MINUTES)
          actuationPauser.resumeApplication(resource.application)
          clock.incrementBy(TEN_MINUTES)
          actuationPauser.pauseApplication(resource.application)
        }
      }

      test("has matching resource paused events injected in the right positions") {
        val request = get(eventsUri).accept(APPLICATION_YAML)
        val result = mvc
          .perform(request)
          .andReturn()
        expectThat(result.response.contentAs<List<PersistentEvent>>())
          .map { it.javaClass }
          .containsExactly(
            ApplicationActuationPaused::class.java,
            ApplicationActuationResumed::class.java,
            ResourceCreated::class.java
          )
      }
    }
  }

  companion object {
    val TEN_MINUTES: Duration = Duration.ofMinutes(10)
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
