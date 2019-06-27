package com.netflix.spinnaker.keel.actuation

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.SubmittedMetadata
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.api.uid
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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.context.ApplicationEventPublisher
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
    val handler: ResourceHandler<DummyResourceSpec> = DummyResourceHandler(),
    val clock: Clock = Clock.systemDefaultZone(),
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true),
    val subject: ResourcePersister = ResourcePersister(repository, listOf(handler), clock, publisher)
  ) {
    lateinit var resource: Resource<DummyResourceSpec>

    @Suppress("UNCHECKED_CAST")
    fun create(submittedResource: SubmittedResource<Any>) {
      resource = subject.create(submittedResource) as Resource<DummyResourceSpec>
    }

    @Suppress("UNCHECKED_CAST")
    fun update(updatedSpec: Any) {
      resource = subject.update(resource.name, SubmittedResource(
        metadata = SubmittedMetadata("keel@spinnaker"),
        apiVersion = resource.apiVersion,
        kind = resource.kind,
        spec = updatedSpec
      )) as Resource<DummyResourceSpec>
    }

    fun resourcesDueForCheck() =
      repository.nextResourcesDueForCheck(Duration.ofMinutes(1), Int.MAX_VALUE)

    fun eventHistory() =
      repository.eventHistory(resource.uid)
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
            metadata = SubmittedMetadata("keel@spinnaker"),
            apiVersion = SPINNAKER_API_V1.subApi("test"),
            kind = "whatever",
            spec = DummyResourceSpec("o hai")
          ))
        }

        test("stores the normalized resource") {
          val persistedResource = repository.get<DummyResourceSpec>(resource.name)
          expectThat(persistedResource) {
            get { name.value }.isEqualTo("test:whatever:o hai")
            get { spec.state }.isEqualTo("o hai")
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
            .get { uid }.isEqualTo(resource.uid)
        }

        context("after an update") {
          before {
            resourcesDueForCheck()
            update(DummyResourceSpec("kthxbye"))
          }

          test("stores the updated resource") {
            expectThat(repository.get<DummyResourceSpec>(resource.name))
              .get { spec.state }
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
              .get { uid }.isEqualTo(resource.uid)
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

internal class DummyResourceHandler : ResourceHandler<DummyResourceSpec> {
  override val apiVersion: ApiVersion = SPINNAKER_API_V1.subApi("test")

  override val supportedKind: Pair<ResourceKind, Class<DummyResourceSpec>> =
    ResourceKind("test", "whatever", "whatevers") to DummyResourceSpec::class.java

  override val objectMapper: ObjectMapper = configuredObjectMapper()

  override val normalizers: List<ResourceNormalizer<*>> = emptyList()

  override fun generateName(spec: DummyResourceSpec): ResourceName =
    ResourceName("test:whatever:${spec.state}")

  override suspend fun current(resource: Resource<DummyResourceSpec>): DummyResourceSpec? {
    TODO("not implemented")
  }

  override suspend fun delete(resource: Resource<DummyResourceSpec>) {
    TODO("not implemented")
  }

  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }
}
