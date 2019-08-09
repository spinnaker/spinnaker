package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.ResourceRepositoryPeriodicallyCheckedTests
import java.time.Clock

class InMemoryResourceRepositoryPeriodicallyCheckedTests : ResourceRepositoryPeriodicallyCheckedTests<InMemoryResourceRepository>() {
  override val factory: (clock: Clock) -> InMemoryResourceRepository = ::InMemoryResourceRepository
}
