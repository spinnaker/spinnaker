package com.netflix.spinnaker.keel.actuation

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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import java.time.Clock
import java.time.Duration

@AutoConfigureMockMvc
internal class ResourcePersisterTests : JUnit5Minutests {

  data class Fixture(
    val repository: InMemoryResourceRepository = InMemoryResourceRepository(),
    val handler: ResourceHandler<String> = StringResourceHandler(),
    val clock: Clock = Clock.systemDefaultZone(),
    val subject: ResourcePersister = ResourcePersister(repository, listOf(handler), clock)
  ) {
    lateinit var resource: Resource<String>

    @Suppress("UNCHECKED_CAST")
    fun create(submittedResource: SubmittedResource<Any>) {
      resource = subject.create(submittedResource) as Resource<String>
    }

    @Suppress("UNCHECKED_CAST")
    fun update(updatedSpec: Any) {
      resource = subject.update(resource.metadata.name, SubmittedResource(
        apiVersion = resource.apiVersion,
        kind = resource.kind,
        spec = updatedSpec
      )) as Resource<String>
    }

    fun resourcesDueForCheck() =
      repository.nextResourcesDueForCheck(Duration.ofMinutes(1), Int.MAX_VALUE)

    fun eventHistory() =
      repository.eventHistory(resource.metadata.uid)
  }

  @Suppress("UNCHECKED_CAST")
  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    after {
      repository.dropAll()
    }

    context("resource lifecycle") {
      context("creation") {
        before {
          create(SubmittedResource(
            apiVersion = SPINNAKER_API_V1.subApi("test"),
            kind = "whatever",
            spec = "o hai"
          ))
        }

        test("stores the normalized resource") {
          val persistedResource = repository.get<String>(resource.metadata.name)
          expectThat(persistedResource) {
            get { metadata.name.value }.isEqualTo("test:whatever:o hai")
            get { spec }.isEqualTo("o hai")
          }
        }

        test("records that the resource was created") {
          expectThat(eventHistory())
            .hasSize(1)
            .first()
            .isA<ResourceCreated>()
        }

        test("checks the resource") {
          expectThat(resourcesDueForCheck())
            .hasSize(1)
            .first()
            .get { uid }.isEqualTo(resource.metadata.uid)
        }

        context("after an update") {
          before {
            resourcesDueForCheck()
            update("kthxbye")
          }

          test("stores the updated resource") {
            expectThat(repository.get<String>(resource.metadata.name))
              .get { spec }
              .isEqualTo("kthxbye")
          }

          test("records that the resource was updated") {
            expectThat(eventHistory())
              .hasSize(2)
              .first()
              .isA<ResourceUpdated>()
          }

          test("checks the resource again") {
            expectThat(resourcesDueForCheck())
              .hasSize(1)
              .first()
              .get { uid }.isEqualTo(resource.metadata.uid)
          }
        }

        context("after a no-op update") {
          before {
            resourcesDueForCheck()
            update(resource.spec)
          }

          test("does not record that the resource was updated") {
            expectThat(eventHistory())
              .hasSize(1)
          }

          test("does not check the resource again") {
            expectThat(resourcesDueForCheck())
              .isEmpty()
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
