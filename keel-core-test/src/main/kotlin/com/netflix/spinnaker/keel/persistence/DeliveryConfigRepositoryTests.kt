package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.ArtifactType.DEB
import com.netflix.spinnaker.keel.api.DeliveryArtifact
import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.randomUID
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.mockk
import io.mockk.verify
import strikt.api.expectCatching
import strikt.assertions.failed
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.succeeded

abstract class DeliveryConfigRepositoryTests<T : DeliveryConfigRepository> : JUnit5Minutests {

  abstract fun factory(
    artifactRepository: ArtifactRepository,
    resourceRepository: ResourceRepository
  ): T

  open fun flush() {}

  inner class Fixture(
    val deliveryConfig: DeliveryConfig
  ) {
    val artifactRepository: ArtifactRepository = mockk(relaxUnitFun = true)
    val resourceRepository: ResourceRepository = mockk(relaxUnitFun = true)
    val repository: T = factory(artifactRepository, resourceRepository)

    fun getByName() = expectCatching {
      repository.get(deliveryConfig.name)
    }
  }

  fun tests() = rootContext<Fixture>() {
    fixture {
      Fixture(
        DeliveryConfig(
          "keel",
          "keel"
        )
      )
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
        repository.store(deliveryConfig)
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

    context("a delivery config with artifacts and environments is stored") {
      deriveFixture {
        Fixture(
          deliveryConfig = deliveryConfig.copy(
            artifacts = setOf(
              DeliveryArtifact(
                name = "keel",
                type = DEB
              )
            ),
            environments = setOf(
              Environment(
                name = "test",
                resources = setOf(
                  Resource(
                    apiVersion = SPINNAKER_API_V1.subApi("test"),
                    kind = "cluster",
                    metadata = mapOf(
                      "uid" to randomUID(),
                      "name" to "test:cluster:whatever"
                    ),
                    spec = randomData()
                  ),
                  Resource(
                    apiVersion = SPINNAKER_API_V1.subApi("test"),
                    kind = "security-group",
                    metadata = mapOf(
                      "uid" to randomUID(),
                      "name" to "test:security-group:whatever"
                    ),
                    spec = randomData()
                  )
                )
              )
            )
          )
        )
      }

      before {
        repository.store(deliveryConfig)
      }

      test("the config can be retrieved by name") {
        getByName()
          .succeeded()
          .and {
            get { artifacts }.isEqualTo(deliveryConfig.artifacts)
            get { environments }.isEqualTo(deliveryConfig.environments)
          }
      }

      test("artifacts are persisted via the artifact repository") {
        deliveryConfig
          .artifacts
          .forEach { artifact ->
            verify {
              artifactRepository.register(artifact)
            }
          }
      }

      test("resources are persisted via the resource repository") {
        deliveryConfig
          .environments
          .flatMap { it.resources }
          .forEach { resource ->
            verify {
              resourceRepository.store(resource)
            }
          }
      }
    }
  }
}
