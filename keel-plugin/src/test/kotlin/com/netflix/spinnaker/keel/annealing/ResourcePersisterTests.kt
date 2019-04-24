package com.netflix.spinnaker.keel.annealing

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.keel.serialization.configuredObjectMapper
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import io.mockk.verify
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.time.Clock

@AutoConfigureMockMvc
internal class ResourcePersisterTests : JUnit5Minutests {

  data class Fixture<T : Any>(
    val repository: InMemoryResourceRepository = InMemoryResourceRepository(),
    val queue: ResourceCheckQueue = mockk(relaxUnitFun = true),
    val publisher: ApplicationEventPublisher = mockk(),
    val handler: ResourceHandler<String> = StringResourceHandler(),
    val clock: Clock = Clock.systemDefaultZone(),
    val subject: ResourcePersister = ResourcePersister(repository, listOf(handler), queue, publisher, clock),
    var resource: Resource<T>? = null
  )

  @Suppress("UNCHECKED_CAST")
  fun tests() = rootContext<Fixture<String>> {
    fixture { Fixture() }

    after {
      repository.dropAll()
    }

    context("resource lifecycle") {
      context("creation") {
        before {
          resource = subject.create(SubmittedResource(
            apiVersion = SPINNAKER_API_V1.subApi("test"),
            kind = "whatever",
            spec = "o hai"
          )) as Resource<String>
        }

        test("stores the normalized resource") {
          val persistedResource = repository.get<String>(resource!!.metadata.name)
          expectThat(persistedResource) {
            get { metadata.name.value }.isEqualTo("test:whatever:o hai")
            get { spec }.isEqualTo("o hai")
          }
        }

        test("records that the resource was created") {
          expectThat(repository.eventHistory(resource!!.metadata.uid))
            .hasSize(1)
            .first()
            .isA<ResourceCreated>()
        }

        test("queues the resource for checking") {
          verify { queue.scheduleCheck(resource!!) }
        }

        context("update") {
          before {
            resource = subject.update(resource!!.copy(spec = "kthxbye") as Resource<Any>) as Resource<String>
          }

          test("stores the updated resource") {
            expectThat(repository.get<String>(resource!!.metadata.name))
              .get { spec }
              .isEqualTo("kthxbye")
          }

          test("records that the resource was updated") {
            expectThat(repository.eventHistory(resource!!.metadata.uid))
              .hasSize(2)
              .first()
              .isA<ResourceUpdated>()
          }

          test("queues the resource for checking") {
            verify { queue.scheduleCheck(resource!!) }
          }
        }

        context("no-op update") {
          before {
            resource = subject.create(SubmittedResource(
              apiVersion = SPINNAKER_API_V1.subApi("test"),
              kind = "whatever",
              spec = "o hai"
            )) as Resource<String>

            resource = subject.update(resource!! as Resource<Any>) as Resource<String>
          }

          test("does not record that the resource was updated") {
            expectThat(repository.eventHistory(resource!!.metadata.uid))
              .hasSize(1)
          }

          test("does not queue the resource for checking") {
            verify { queue.scheduleCheck(resource!!) }
          }
        }
      }
    }
  }
}

internal class StringResourceHandler : ResourceHandler<String> {
  override val apiVersion: ApiVersion = SPINNAKER_API_V1.subApi("test")

  override val supportedKind: Pair<ResourceKind, Class<String>> =
    ResourceKind("test", "whatever", "whatevers") to String::class.java

  override val objectMapper: ObjectMapper = configuredObjectMapper()

  override val normalizers: List<ResourceNormalizer<*>> = emptyList()

  override fun generateName(spec: String): ResourceName =
    ResourceName("test:whatever:$spec")

  override fun current(resource: Resource<String>): String? {
    TODO("not implemented")
  }

  override fun delete(resource: Resource<String>) {
    TODO("not implemented")
  }

  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }
}
