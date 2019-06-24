package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectCatching
import strikt.assertions.failed
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import strikt.assertions.succeeded

abstract class DeliveryConfigRepositoryTests<T : DeliveryConfigRepository> : JUnit5Minutests {

  abstract fun factory(): T

  open fun flush() {}

  inner class Fixture(
    val deliveryConfig: DeliveryConfig
  ) {
    val repository: T = factory()

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

    context("no delivery configs are stored") {
      test("retrieving config by name fails") {
        getByName()
          .failed()
          .isA<NoSuchDeliveryConfigException>()
      }
    }

    context("a delivery config with no artifacts or environments has been stored") {
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
  }
}
