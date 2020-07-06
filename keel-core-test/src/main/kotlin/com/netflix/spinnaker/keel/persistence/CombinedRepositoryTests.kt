package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.artifacts.DOCKER
import com.netflix.spinnaker.keel.api.artifacts.TagVersionStrategy.BRANCH_JOB_COMMIT_BY_JOB
import com.netflix.spinnaker.keel.api.events.ArtifactRegisteredEvent
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.artifacts.DockerArtifact
import com.netflix.spinnaker.keel.core.api.SubmittedResource
import com.netflix.spinnaker.keel.core.api.normalize
import com.netflix.spinnaker.keel.events.ResourceCreated
import com.netflix.spinnaker.keel.events.ResourceUpdated
import com.netflix.spinnaker.keel.exceptions.DuplicateManagedResourceException
import com.netflix.spinnaker.keel.resources.ResourceSpecIdentifier
import com.netflix.spinnaker.keel.test.DummyResourceSpec
import com.netflix.spinnaker.keel.test.TEST_API_V1
import com.netflix.spinnaker.keel.test.configuredTestObjectMapper
import com.netflix.spinnaker.keel.test.resource
import com.netflix.spinnaker.time.MutableClock
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import org.springframework.context.ApplicationEventPublisher
import strikt.api.expect
import strikt.api.expectCatching
import strikt.api.expectThat
import strikt.api.expectThrows
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEmpty
import strikt.assertions.isEqualTo
import strikt.assertions.isSuccess
import strikt.assertions.isTrue

/**
 * Tests that involve creating, updating, or deleting things from two or more of the three repositories present.
 *
 * Tests that only apply to one repository should live in the repository-specific test classes.
 */
abstract class CombinedRepositoryTests<D : DeliveryConfigRepository, R : ResourceRepository, A : ArtifactRepository> :
  JUnit5Minutests {

  abstract fun createDeliveryConfigRepository(resourceSpecIdentifier: ResourceSpecIdentifier): D
  abstract fun createResourceRepository(resourceSpecIdentifier: ResourceSpecIdentifier): R
  abstract fun createArtifactRepository(): A

  open fun flush() {}

  val configName = "my-config"
  val secondConfigName = "my-config-2"
  val application = "fnord"
  val secondApplication = "fnord-2"
  val artifact = DockerArtifact(name = "org/image", deliveryConfigName = configName)
  val newArtifact = artifact.copy(reference = "myart")
  val firstResource = resource()
  val secondResource = resource()
  val firstEnv = Environment(name = "env1", resources = setOf(firstResource))
  val secondEnv = Environment(name = "env2", resources = setOf(secondResource))
  val deliveryConfig = DeliveryConfig(
    name = configName,
    application = application,
    serviceAccount = "keel@spinnaker",
    artifacts = setOf(artifact),
    environments = setOf(firstEnv)
  )
  val secondDeliveryConfig = DeliveryConfig(
    name = secondConfigName,
    application = secondApplication,
    serviceAccount = "keel@spinnaker",
    artifacts = setOf(artifact),
    environments = setOf(firstEnv)
  )

  val anotherDeliveryConfigWithSameName = DeliveryConfig(
    name = configName,
    application = secondApplication,
    serviceAccount = "keel@spinnaker",
    artifacts = setOf(),
    environments = setOf()
  )

  val anotherDeliveryConfigWithSameApp = DeliveryConfig(
    name = secondConfigName,
    application = application,
    serviceAccount = "keel@spinnaker",
    artifacts = setOf(artifact),
    environments = setOf(firstEnv)
  )

  data class Fixture<D : DeliveryConfigRepository, R : ResourceRepository, A : ArtifactRepository>(
    val deliveryConfigRepositoryProvider: (ResourceSpecIdentifier) -> D,
    val resourceRepositoryProvider: (ResourceSpecIdentifier) -> R,
    val artifactRepositoryProvider: () -> A
  ) {
    internal val clock = MutableClock()
    val publisher: ApplicationEventPublisher = mockk(relaxUnitFun = true)

    internal val deliveryConfigRepository: D = deliveryConfigRepositoryProvider(DummyResourceSpecIdentifier)
    internal val resourceRepository: R = resourceRepositoryProvider(DummyResourceSpecIdentifier)
    internal val artifactRepository: A = artifactRepositoryProvider()

    val subject = CombinedRepository(
      deliveryConfigRepository,
      artifactRepository,
      resourceRepository,
      clock,
      publisher,
      configuredTestObjectMapper()
    )

    fun resourcesDueForCheck() =
      subject.resourcesDueForCheck(Duration.ofMinutes(1), Int.MAX_VALUE)

    fun CombinedRepository.allResourceNames(): List<String> =
      mutableListOf<String>()
        .also { list ->
          allResources { list.add(it.id) }
        }
  }

  fun tests() = rootContext<Fixture<D, R, A>> {
    fixture {
      Fixture(
        deliveryConfigRepositoryProvider = this@CombinedRepositoryTests::createDeliveryConfigRepository,
        resourceRepositoryProvider = this@CombinedRepositoryTests::createResourceRepository,
        artifactRepositoryProvider = this@CombinedRepositoryTests::createArtifactRepository
      )
    }

    after {
      flush()
    }

    context("creating and deleting delivery configs") {
      before {
        subject.upsertDeliveryConfig(deliveryConfig)
      }

      context("delivery config created") {
        test("delivery config is persisted") {
          expectCatching { subject.getDeliveryConfig(deliveryConfig.name) }
            .isSuccess()
        }

        test("artifacts are persisted") {
          expectThat(subject.isRegistered("org/image", DOCKER)).isTrue()
          verify { publisher.publishEvent(ArtifactRegisteredEvent(DockerArtifact(name = "org/image", deliveryConfigName = configName))) }
        }

        test("individual resources are persisted") {
          deliveryConfig.resources.map { it.id }.forEach { id ->
            expectCatching {
              subject.getResource(id)
            }.isSuccess()
          }
        }
      }

      context("delivery config was deleted") {
        before {
          subject.deleteDeliveryConfig(configName)
        }
        test("everything is deleted") {
          expectThrows<NoSuchDeliveryConfigException> { deliveryConfigRepository.get(configName) }
          expectThrows<NoSuchResourceException> { resourceRepository.get(firstResource.id) }
          expectThat(artifactRepository.get(artifact.name, artifact.type, configName)).isEmpty()
        }
      }

      context("delivery config is updated") {
        context("artifact and resource have changed") {
          before {
            val updatedConfig = deliveryConfig.copy(
              artifacts = setOf(newArtifact),
              environments = setOf(firstEnv.copy(resources = setOf(secondResource)))
            )
            subject.upsertDeliveryConfig(updatedConfig)
          }

          test("no longer present dependents are removed") {
            expectThrows<NoSuchResourceException> { resourceRepository.get(firstResource.id) }
            expectThrows<ArtifactNotFoundException> {
              artifactRepository.get(name = artifact.name, type = artifact.type, reference = "org/image", deliveryConfigName = configName)
            }
          }

          test("correct resources still exist") {
            expectCatching { resourceRepository.get(secondResource.id) }.isSuccess()
            expectCatching {
              artifactRepository.get(name = newArtifact.name, type = newArtifact.type, reference = "myart", deliveryConfigName = configName)
            }.isSuccess()
          }
        }

        context("artifact properties modified") {
          before {
            val updatedConfig = deliveryConfig.copy(
              artifacts = setOf(artifact.copy(tagVersionStrategy = BRANCH_JOB_COMMIT_BY_JOB))
            )
            subject.upsertDeliveryConfig(updatedConfig)
          }

          test("artifact is updated but still present") {
            expectCatching {
              artifactRepository.get(name = artifact.name, type = artifact.type, reference = artifact.reference, deliveryConfigName = configName)
            }.isSuccess()
              .isA<DockerArtifact>()
              .get { tagVersionStrategy }.isEqualTo(BRANCH_JOB_COMMIT_BY_JOB)
          }
        }

        context("environment changed and artifact removed") {
          before {
            val updatedConfig = deliveryConfig.copy(
              artifacts = setOf(),
              environments = setOf(firstEnv.copy(name = "env2"))
            )
            subject.upsertDeliveryConfig(updatedConfig)
          }
          test("old environment is gone") {
            val config = deliveryConfigRepository.get(configName)
            expect {
              that(config.environments.size).isEqualTo(1)
              that(config.environments.first().name).isEqualTo("env2")
              that(config.resources.size).isEqualTo(1)
              that(config.resources.first().id).isEqualTo(firstResource.id)
              that(config.artifacts.size).isEqualTo(0)
              that(artifactRepository.get(artifact.name, artifact.type, configName)).isEmpty()
            }
          }
        }
      }
    }

    context("persisting individual resources") {
      context("resource lifecycle") {
        val resource = SubmittedResource(
          metadata = mapOf("serviceAccount" to "keel@spinnaker"),
          kind = TEST_API_V1.qualify("whatever"),
          spec = DummyResourceSpec(data = "o hai")
        ).normalize()

        context("creation") {
          before {
            val updatedConfig = deliveryConfig.copy(
              environments = setOf(firstEnv.copy(resources = setOf(resource)))
            )
            subject.upsertDeliveryConfig(updatedConfig)
          }

          test("stores the resource") {
            val persistedResource = subject.getResource(resource.id)
            expectThat(persistedResource) {
              isA<Resource<DummyResourceSpec>>()
              get { id }.isEqualTo(resource.id)
              get { (spec as DummyResourceSpec).data }.isEqualTo("o hai")
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
              subject.upsertResource(resource.copy(spec = DummyResourceSpec(id = resource.spec.id, data = "kthxbye")), deliveryConfig.name)
            }

            test("stores the updated resource") {
              expectThat(subject.getResource(resource.id))
                .get { spec }
                .isA<DummyResourceSpec>()
                .get { data }
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
              subject.upsertResource(resource, deliveryConfig.name)
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
    }

    context("don't allow resources to be managed by more than 1 config") {
      before {
        subject.upsertDeliveryConfig(deliveryConfig)
      }
      test("trying to persist another config with the same resource") {
        expectThrows<DuplicateManagedResourceException> { subject.upsertDeliveryConfig(secondDeliveryConfig) }
        expectThrows<NoSuchDeliveryConfigException> { deliveryConfigRepository.get(secondConfigName) }
        expectCatching { resourceRepository.get(firstResource.id) }.isSuccess()
      }
    }

    context("trying to persist two configs with the same name, but different application") {
      before {
        subject.upsertDeliveryConfig(deliveryConfig)
      }
      test("second config fails with exception, first config didn't change") {
        expectThrows<ConflictingDeliveryConfigsException> { subject.upsertDeliveryConfig(anotherDeliveryConfigWithSameName) }
        expectThat(deliveryConfigRepository.get(configName))
          .get { application }
          .isEqualTo(deliveryConfig.application)

        expectThrows<NoSuchDeliveryConfigException> { deliveryConfigRepository.getByApplication(anotherDeliveryConfigWithSameName.application) }
      }
    }

    context("trying to persist another config with the same application, but different config names") {
      before {
        subject.upsertDeliveryConfig(deliveryConfig)
      }
      test("second config fails with exception, first config didn't change") {
        expectThrows<TooManyDeliveryConfigsException> { subject.upsertDeliveryConfig(anotherDeliveryConfigWithSameApp) }
        expectThat(deliveryConfigRepository.get(configName))
          .get { application }
          .isEqualTo(deliveryConfig.application)

        expectThrows<NoSuchDeliveryConfigException> { deliveryConfigRepository.get(anotherDeliveryConfigWithSameApp.name) }
      }
    }
  }
}
