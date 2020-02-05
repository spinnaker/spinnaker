package com.netflix.spinnaker.keel.actuation

import com.netflix.spinnaker.keel.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ArtifactType.deb
import com.netflix.spinnaker.keel.api.DebianArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceSpec
import com.netflix.spinnaker.keel.api.SubmittedDeliveryConfig
import com.netflix.spinnaker.keel.api.SubmittedEnvironment
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.resources
import com.netflix.spinnaker.keel.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.persistence.Cleaner
import com.netflix.spinnaker.keel.persistence.get
import com.netflix.spinnaker.keel.persistence.memory.InMemoryArtifactRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryDeliveryConfigRepository
import com.netflix.spinnaker.keel.persistence.memory.InMemoryResourceRepository
import com.netflix.spinnaker.keel.plugin.SimpleResourceHandler
import com.netflix.spinnaker.keel.plugin.SupportedKind
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import dev.minutest.MinutestFixture
import dev.minutest.Tests
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import io.mockk.verify
import java.time.Clock
import java.time.Duration
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

@AutoConfigureMockMvc
internal class ResourcePersisterTests : JUnit5Minutests {

  @MinutestFixture
  data class Fixture(
    val artifactRepository: InMemoryArtifactRepository = InMemoryArtifactRepository(),
    val resourceRepository: InMemoryResourceRepository = InMemoryResourceRepository(),
    val deliveryConfigRepository: InMemoryDeliveryConfigRepository = InMemoryDeliveryConfigRepository()
  ) {
    private val clock: Clock = Clock.systemDefaultZone()
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)
    private val cleaner: Cleaner = mockk(relaxUnitFun = true)
    private val subject: ResourcePersister = ResourcePersister(
      deliveryConfigRepository,
      artifactRepository,
      resourceRepository,
      listOf(DummyResourceHandler),
      cleaner,
      clock,
      publisher
    )

    lateinit var resource: Resource<DummyResourceSpec>
    lateinit var deliveryConfig: DeliveryConfig

    @Suppress("UNCHECKED_CAST")
    fun create(submittedResource: SubmittedResource<DummyResourceSpec>) {
      resource = subject.upsert(submittedResource)
    }

    @Suppress("UNCHECKED_CAST")
    fun update(updatedSpec: DummyResourceSpec) {
      resource = subject.upsert(SubmittedResource(
        metadata = mapOf("serviceAccount" to "keel@spinnaker"),
        apiVersion = resource.apiVersion,
        kind = resource.kind,
        spec = updatedSpec
      ))
    }

    fun resourcesDueForCheck() =
      resourceRepository.itemsDueForCheck(Duration.ofMinutes(1), Int.MAX_VALUE)

    fun create(submittedDeliveryConfig: SubmittedDeliveryConfig) {
      deliveryConfig = subject.upsert(submittedDeliveryConfig)
    }
  }

  @Tests
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
              metadata = mapOf("serviceAccount" to "keel@spinnaker"),
              apiVersion = "test.$SPINNAKER_API_V1",
              kind = "whatever",
              spec = DummyResourceSpec(data = "o hai")
            ))
          }

          test("stores the normalized resource") {
            val persistedResource = resourceRepository.get<DummyResourceSpec>(resource.id)
            expectThat(persistedResource) {
              get { id }.isEqualTo(resource.id)
              get { spec.data }.isEqualTo("o hai")
            }
          }

          test("records that the resource was created") {
            verify {
              publisher.publishEvent(ofType<ResourceCreated>())
            }
          }

          test("will check the resource") {
            expectThat(resourcesDueForCheck())
              .hasSize(1)
              .first()
              .get { id }.isEqualTo(resource.id)
          }

          context("after an update") {
            before {
              resourcesDueForCheck()
              update(DummyResourceSpec(id = resource.spec.id, data = "kthxbye"))
            }

            test("stores the updated resource") {
              expectThat(resourceRepository.get<DummyResourceSpec>(resource.id))
                .get { spec.data }
                .isEqualTo("kthxbye")
            }

            test("records that the resource was updated") {
              verify {
                publisher.publishEvent(ofType<ResourceUpdated>())
              }
            }

            test("will check the resource again") {
              expectThat(resourcesDueForCheck())
                .hasSize(1)
                .first()
                .get { id }.isEqualTo(resource.id)
            }
          }

          context("after a no-op update") {
            before {
              resourcesDueForCheck()
              update(resource.spec)
            }

            test("does not record that the resource was updated") {
              verify(exactly = 0) {
                publisher.publishEvent(ofType<ResourceUpdated>())
              }
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
              serviceAccount = "keel@spinnaker",
              artifacts = setOf(DebianArtifact(name = "keel")),
              environments = setOf(
                SubmittedEnvironment(
                  name = "test",
                  resources = setOf(
                    SubmittedResource(
                      apiVersion = "test.$SPINNAKER_API_V1",
                      kind = "whatever",
                      spec = DummyResourceSpec("test", "resource in test")
                    )
                  ),
                  constraints = emptySet()
                ),
                SubmittedEnvironment(
                  name = "prod",
                  resources = setOf(
                    SubmittedResource(
                      apiVersion = "test.$SPINNAKER_API_V1",
                      kind = "whatever",
                      spec = DummyResourceSpec("prod", "resource in prod")
                    )
                  ),
                  constraints = emptySet()
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
          expectThat(artifactRepository.isRegistered("keel", deb)).isTrue()
          verify { publisher.publishEvent(ArtifactRegisteredEvent(DebianArtifact(name = "keel", deliveryConfigName = "keel-manifest"))) }
        }

        test("individual resources are persisted") {
          expectThat(resourceRepository.size()).isEqualTo(2)

          deliveryConfig.resources.map { it.id }.forEach { id ->
            expectCatching {
              resourceRepository.get<DummyResourceSpec>(id)
            }.succeeded()
          }
        }
      }

      context("a delivery config with existing artifacts and resources is persisted") {
        before {
          val artifact = DebianArtifact(name = "keel", deliveryConfigName = "keel-manifest")
            .also {
              artifactRepository.register(it)
            }

          val resource1 = SubmittedResource(
            apiVersion = "test.$SPINNAKER_API_V1",
            kind = "whatever",
            metadata = mapOf("serviceAccount" to "keel@spinnaker"),
            spec = DummyResourceSpec("test", "resource in test")
          ).also {
            create(it)
          }
            .run {
              copy(spec = spec.copy(data = "updated resource in test"))
            }

          val resource2 = SubmittedResource(
            apiVersion = "test.$SPINNAKER_API_V1",
            kind = "whatever",
            metadata = mapOf("serviceAccount" to "keel@spinnaker"),
            spec = DummyResourceSpec("prod", "resource in prod")
          ).also {
            create(it)
          }
            .run {
              copy(spec = spec.copy(data = "updated resource in prod"))
            }

          create(
            SubmittedDeliveryConfig(
              name = "keel-manifest",
              application = "keel",
              serviceAccount = "keel@spinnaker",
              artifacts = setOf(artifact),
              environments = setOf(
                SubmittedEnvironment(
                  name = "test",
                  resources = setOf(resource1),
                  constraints = emptySet()
                ),
                SubmittedEnvironment(
                  name = "prod",
                  resources = setOf(resource2),
                  constraints = emptySet()
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
            expectThat(resourceRepository.get<ResourceSpec>(resource.id))
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

internal object DummyResourceHandler : SimpleResourceHandler<DummyResourceSpec>(emptyList()) {
  override val supportedKind =
    SupportedKind("test.$SPINNAKER_API_V1", "whatever", DummyResourceSpec::class.java)

  override suspend fun current(resource: Resource<DummyResourceSpec>): DummyResourceSpec? {
    TODO("not implemented")
  }
}
