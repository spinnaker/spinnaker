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
import strikt.api.expectCatching
import strikt.assertions.failed
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.succeeded

abstract class DeliveryConfigRepositoryTests<T : DeliveryConfigRepository> : JUnit5Minutests {

  abstract fun factory(resourceTypeIdentifier: (String) -> Class<*>): T

  open fun flush() {}

  data class Fixture<T : DeliveryConfigRepository>(
    val factory: ((String) -> Class<*>) -> T,
    val deliveryConfig: DeliveryConfig
  ) {
    val resourceTypeIdentifier: (String) -> Class<*> = { kind: String ->
      when (kind) {
        "security-group" -> Map::class.java
        "cluster" -> Map::class.java
        else -> error("unsupported kind $kind")
      }
    }

    val repository: T = factory(resourceTypeIdentifier)

    fun getByName() = expectCatching {
      repository.get(deliveryConfig.name)
    }

    fun store() {
      repository.store(deliveryConfig)
    }
  }

  fun tests() = rootContext<Fixture<T>>() {
    fixture {
      Fixture(
        factory = this@DeliveryConfigRepositoryTests::factory,
        deliveryConfig = DeliveryConfig("keel", "keel")
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
        Fixture(
          factory = this@DeliveryConfigRepositoryTests::factory,
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
                      "name" to "test:cluster:whatever"
                    ),
                    spec = randomData()
                  ),
                  Resource(
                    apiVersion = SPINNAKER_API_V1.subApi("test"),
                    kind = "security-group",
                    metadata = mapOf(
                      "uid" to randomUID().toString(),
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

      context("the artifact and resource updates are successful") {
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

        test("artifacts are attached") {
          getByName()
            .succeeded()
            .get { artifacts }.isEqualTo(deliveryConfig.artifacts)
        }

        test("environments are attached") {
          getByName()
            .succeeded()
            .get { environments }.isEqualTo(deliveryConfig.environments)
        }
      }
    }
  }
}
