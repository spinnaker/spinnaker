package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ApiVersion
import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.DependsOnConstraint
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import com.netflix.spinnaker.keel.api.resources
import com.netflix.spinnaker.keel.api.uid
import com.netflix.spinnaker.keel.resources.ResourceTypeIdentifier
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.assertions.failed
import strikt.assertions.hasSize
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.succeeded

abstract class DeliveryConfigRepositoryTests<T : DeliveryConfigRepository, R : ResourceRepository, A : ArtifactRepository> :
  JUnit5Minutests {

  abstract fun createDeliveryConfigRepository(resourceTypeIdentifier: ResourceTypeIdentifier): T
  abstract fun createResourceRepository(): R
  abstract fun createArtifactRepository(): A

  open fun flush() {}

  data class Fixture<T : DeliveryConfigRepository, R : ResourceRepository, A : ArtifactRepository>(
    val deliveryConfigRepositoryProvider: (ResourceTypeIdentifier) -> T,
    val resourceRepositoryProvider: () -> R,
    val artifactRepositoryProvider: () -> A,
    val deliveryConfig: DeliveryConfig = DeliveryConfig("keel", "keel")
  ) {
    private val resourceTypeIdentifier: ResourceTypeIdentifier =
      object : ResourceTypeIdentifier {
        override fun identify(apiVersion: ApiVersion, kind: String): Class<*> {
          return when (kind) {
            "security-group" -> Map::class.java
            "cluster" -> Map::class.java
            else -> error("unsupported kind $kind")
          }
        }
      }

    private val repository: T = deliveryConfigRepositoryProvider(resourceTypeIdentifier)
    private val resourceRepository: R = resourceRepositoryProvider()
    private val artifactRepository: A = artifactRepositoryProvider()

    fun getByName() = expectCatching {
      repository.get(deliveryConfig.name)
    }

    fun store() {
      repository.store(deliveryConfig)
    }

    fun storeResources() {
      deliveryConfig.environments.flatMap { it.resources }.forEach {
        resourceRepository.store(it)
      }
    }

    fun storeArtifacts() {
      deliveryConfig.artifacts.forEach {
        artifactRepository.register(it)
      }
    }

    fun getEnvironment(resource: Resource<*>) = expectCatching {
      repository.environmentFor(resource.uid)
    }

    fun getDeliveryConfig(resource: Resource<*>) = expectCatching {
      repository.deliveryConfigFor(resource.uid)
    }
  }

  fun tests() = rootContext<Fixture<T, R, A>>() {
    fixture {
      Fixture(
        deliveryConfigRepositoryProvider = this@DeliveryConfigRepositoryTests::createDeliveryConfigRepository,
        resourceRepositoryProvider = this@DeliveryConfigRepositoryTests::createResourceRepository,
        artifactRepositoryProvider = this@DeliveryConfigRepositoryTests::createArtifactRepository
      )
    }

    after {
      flush()
    }

    context("an empty repository") {
      test("retrieving config by name fails") {
        getByName()
          .failed()
          .isA<NoSuchDeliveryConfigException>()
      }
    }

    context("storing a delivery config with no artifacts or environments") {
      before {
        store()
      }

      test("the config can be retrieved by name") {
        getByName()
          .succeeded()
          .and {
            get { name }.isEqualTo(deliveryConfig.name)
            get { application }.isEqualTo(deliveryConfig.application)
          }
      }
    }

    context("storing a delivery config with artifacts and environments") {
      deriveFixture {
        copy(
          deliveryConfig = deliveryConfig.copy(
            artifacts = setOf(
              DeliveryArtifact(name = "keel", type = DEB)
            ),
            environments = setOf(
              Environment(
                name = "test",
                resources = setOf(
                  Resource(
                    apiVersion = SPINNAKER_API_V1.subApi("test"),
                    kind = "cluster",
                    metadata = mapOf(
                      "uid" to randomUID().toString(),
                      "name" to "test:cluster:whatever",
                      "serviceAccount" to "keel@spinnaker",
                      "application" to "whatever"
                    ),
                    spec = randomData()
                  ),
                  Resource(
                    apiVersion = SPINNAKER_API_V1.subApi("test"),
                    kind = "security-group",
                    metadata = mapOf(
                      "uid" to randomUID().toString(),
                      "name" to "test:security-group:whatever",
                      "serviceAccount" to "keel@spinnaker",
                      "application" to "whatever"
                    ),
                    spec = randomData()
                  )
                )
              ),
              Environment(
                name = "staging",
                constraints = setOf(
                  DependsOnConstraint(
                    environment = "test"
                  )
                ),
                resources = setOf(
                  Resource(
                    apiVersion = SPINNAKER_API_V1.subApi("test"),
                    kind = "cluster",
                    metadata = mapOf(
                      "uid" to randomUID().toString(),
                      "name" to "staging:cluster:whatever",
                      "serviceAccount" to "keel@spinnaker",
                      "application" to "whatever"
                    ),
                    spec = randomData()
                  ),
                  Resource(
                    apiVersion = SPINNAKER_API_V1.subApi("test"),
                    kind = "security-group",
                    metadata = mapOf(
                      "uid" to randomUID().toString(),
                      "name" to "staging:security-group:whatever",
                      "serviceAccount" to "keel@spinnaker",
                      "application" to "whatever"
                    ),
                    spec = randomData()
                  )
                )
              )
            )
          )
        )
      }

      context("the environments did not previously exist") {
        before {
          storeArtifacts()
          storeResources()
          store()
        }

        test("the config can be retrieved by name") {
          getByName()
            .succeeded()
            .and {
              get { name }.isEqualTo(deliveryConfig.name)
              get { application }.isEqualTo(deliveryConfig.application)
            }
        }

        test("artifacts are attached") {
          getByName()
            .succeeded()
            .get { artifacts }.isEqualTo(deliveryConfig.artifacts)
        }

        test("environments are attached") {
          getByName()
            .succeeded()
            .get { environments }
            .isEqualTo(deliveryConfig.environments)
        }

        test("can retrieve the environment for the resources") {
          val environment = deliveryConfig.environments.first { it.name == "test" }
          val resource = environment.resources.random()

          getEnvironment(resource)
            .succeeded()
            .isNotNull()
            .isEqualTo(environment)
        }

        test("can retrieve the manifest for the resources") {
          val resource = deliveryConfig.resources.random()

          getDeliveryConfig(resource)
            .succeeded()
            .isNotNull()
            .isEqualTo(deliveryConfig)
        }
      }

      context("environments already existed") {
        before {
          storeArtifacts()
          storeResources()
          store()
          store()
        }

        test("the environments are not duplicated") {
          getByName()
            .succeeded()
            .get { environments }.hasSize(deliveryConfig.environments.size)
        }
      }
    }
  }
}
