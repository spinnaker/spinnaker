package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.api.DeliveryConfig
import com.netflix.spinnaker.keel.api.Environment

abstract class DeliveryConfigRepositoryPeriodicallyCheckedTests<S : DeliveryConfigRepository> :
  PeriodicallyCheckedRepositoryTests<DeliveryConfig, S>() {

  override val descriptor = "delivery config"

  override val createAndStore: Fixture<DeliveryConfig, S>.(count: Int) -> Collection<DeliveryConfig> = { count ->
    (1..count)
      .map { i ->
        DeliveryConfig(
          name = "delivery-config-$i",
          application = "fnord"
        )
          .also(subject::store)
      }
  }

  override val updateOne: Fixture<DeliveryConfig, S>.() -> DeliveryConfig = {
    subject.get("delivery-config-1")
      .copy(environments = setOf(Environment("test")))
      .also(subject::store)
  }
}
