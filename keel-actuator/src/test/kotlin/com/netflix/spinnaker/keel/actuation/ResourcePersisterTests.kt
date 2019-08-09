package com.netflix.spinnaker.keel.actuation

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.ResourceName
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.api.SubmittedMetadata
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.name
import com.netflix.spinnaker.keel.api.resources
import com.netflix.spinnaker.keel.api.uid
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
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
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import strikt.assertions.startsWith
import strikt.assertions.succeeded
import java.time.Clock
import java.time.Duration

@AutoConfigureMockMvc
internal class ResourcePersisterTests : JUnit5Minutests {

  data class Fixture(
    val artifactRepository: InMemoryArtifactRepository = InMemoryArtifactRepository(),
    val resourceRepository: InMemoryResourceRepository = InMemoryResourceRepository(),
    val deliveryConfigRepository: InMemoryDeliveryConfigRepository = InMemoryDeliveryConfigRepository()
  ) {
    private val clock: Clock = Clock.systemDefaultZone()
    private val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
    val subject: ResourcePersister = ResourcePersister(
      deliveryConfigRepository,
      artifactRepository,
      resourceRepository,
      listOf(DummyResourceHandler),
      clock,
      publisher
    )

    lateinit var resource: Resource<*>
    lateinit var deliveryConfig: DeliveryConfig

    @Suppress("UNCHECKED_CAST")
    fun create(submittedResource: SubmittedResource<*>) {
      resource = subject.upsert(submittedResource)
    }

    @Suppress("UNCHECKED_CAST")
    fun update(updatedSpec: Any) {
      resource = subject.upsert(SubmittedResource(
        metadata = SubmittedMetadata("keel@spinnaker"),
        apiVersion = resource.apiVersion,
        kind = resource.kind,
        spec = updatedSpec
      )) as Resource<DummyResourceSpec>
    }

    fun resourcesDueForCheck() =
      resourceRepository.itemsDueForCheck(Duration.ofMinutes(1), Int.MAX_VALUE)

    fun eventHistory() =
      resourceRepository.eventHistory(resource.uid)

    fun create(submittedDeliveryConfig: SubmittedDeliveryConfig) {
      deliveryConfig = subject.upsert(submittedDeliveryConfig)
    }
  }

  fun tests() = rootContext<Fixture> {
    fixture { Fixture() }

    context("persisting individual resources") {

      after {
        deliveryConfigRepository.dropAll()
        artifactRepository.dropAll()
        resourceRepository.dropAll()
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
            val persistedResource = resourceRepository.get<DummyResourceSpec>(resource.name)
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

          test("will check the resource") {
            expectThat(resourcesDueForCheck())
              .hasSize(1)
              .first()
              .get { uid }.isEqualTo(resource.uid)
          }

          context("after an update") {
            before {
              resourcesDueForCheck()
              subject.upsert(SubmittedResource<Any>(
                metadata = SubmittedMetadata("keel@spinnaker"),
                apiVersion = resource.apiVersion,
                kind = resource.kind,
                spec = DummyResourceSpec("o hai", "kthxbye")
              ))
            }

            test("stores the updated resource") {
              expectThat(resourceRepository.get<DummyResourceSpec>(resource.name))
                .get { spec.data }
                .isEqualTo("kthxbye")
            }

            test("records that the resource was updated") {
              expectThat(eventHistory())
                .hasSize(2)
                .first()
                .isA<ResourceUpdated>()
            }

            test("will check the resource again") {
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

            test("will not check the resource again") {
              expectThat(resourcesDueForCheck())
                .isEmpty()
            }
          }
        }
      }
    }

    context("persisting delivery config manifests") {
      after {
        deliveryConfigRepository.dropAll()
        artifactRepository.dropAll()
        resourceRepository.dropAll()
      }

      context("a delivery config with new artifacts and resources is persisted") {
        before {
          create(
            SubmittedDeliveryConfig(
              name = "keel-manifest",
              application = "keel",
              artifacts = setOf(DeliveryArtifact(
                name = "keel",
                type = DEB
              )),
              environments = setOf(
                SubmittedEnvironment(
                  name = "test",
                  resources = setOf(SubmittedResource(
                    apiVersion = SPINNAKER_API_V1.subApi("test"),
                    kind = "whatever",
                    metadata = SubmittedMetadata("keel@spinnaker"),
                    spec = DummyResourceSpec("test", "resource in test")
                  ))
                ),
                SubmittedEnvironment(
                  name = "prod",
                  resources = setOf(SubmittedResource(
                    apiVersion = SPINNAKER_API_V1.subApi("test"),
                    kind = "whatever",
                    metadata = SubmittedMetadata("keel@spinnaker"),
                    spec = DummyResourceSpec("prod", "resource in prod")
                  ))
                )
              )
            )
          )
        }

        test("delivery config is persisted") {
          expectCatching { deliveryConfigRepository.get(deliveryConfig.name) }
            .succeeded()
        }

        test("artifacts are persisted") {
          expectThat(artifactRepository.isRegistered("keel", DEB)).isTrue()
        }

        test("individual resources are persisted") {
          expectThat(resourceRepository.size()).isEqualTo(2)

          deliveryConfig.resources.map { it.uid }.forEach { uid ->
            expectCatching {
              resourceRepository.get<DummyResourceSpec>(uid)
            }.succeeded()
          }
        }
      }

      context("a delivery config with existing artifacts and resources is persisted") {
        before {
          val artifact = DeliveryArtifact(
            name = "keel",
            type = DEB
          ).also {
            artifactRepository.register(it)
          }

          val resource1 = SubmittedResource(
            apiVersion = SPINNAKER_API_V1.subApi("test"),
            kind = "whatever",
            metadata = SubmittedMetadata("keel@spinnaker"),
            spec = DummyResourceSpec("test", "resource in test")
          ).also {
            create(it)
          }
            .copy(spec = DummyResourceSpec("test", "updated resource in test"))

          val resource2 = SubmittedResource(
            apiVersion = SPINNAKER_API_V1.subApi("test"),
            kind = "whatever",
            metadata = SubmittedMetadata("keel@spinnaker"),
            spec = DummyResourceSpec("prod", "resource in prod")
          ).also {
            create(it)
          }
            .copy(spec = DummyResourceSpec("prod", "updated resource in prod"))

          create(
            SubmittedDeliveryConfig(
              name = "keel-manifest",
              application = "keel",
              artifacts = setOf(artifact),
              environments = setOf(
                SubmittedEnvironment(
                  name = "test",
                  resources = setOf(resource1)
                ),
                SubmittedEnvironment(
                  name = "prod",
                  resources = setOf(resource2)
                )
              )
            )
          )
        }

        test("delivery config is persisted") {
          expectCatching { deliveryConfigRepository.get(deliveryConfig.name) }
            .succeeded()
        }

        test("resources are not duplicated") {
          expectThat(resourceRepository.size()).isEqualTo(2)
        }

        test("resources are updated") {
          deliveryConfig.resources.forEach { resource ->
            expectThat(resourceRepository.get<Any>(resource.uid))
              .get { spec }
              .isEqualTo(resource.spec)
              .isA<DummyResourceSpec>()
              .get { data }
              .startsWith("updated")
          }
        }
      }
    }
  }
}

internal object DummyResourceHandler : ResourceHandler<DummyResourceSpec> {
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
