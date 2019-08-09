package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.DeliveryConfigRepositoryPeriodicallyCheckedTests
import java.time.Clock

class InMemoryDeliveryConfigRepositoryPeriodicallyCheckedTests :
  DeliveryConfigRepositoryPeriodicallyCheckedTests<InMemoryDeliveryConfigRepository>() {
  override val factory: (Clock) -> InMemoryDeliveryConfigRepository = { clock ->
    InMemoryDeliveryConfigRepository(clock)
  }
}
