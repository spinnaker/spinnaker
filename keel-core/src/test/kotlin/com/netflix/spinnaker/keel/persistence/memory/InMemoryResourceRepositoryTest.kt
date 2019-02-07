package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.ResourceRepositoryTests
import java.time.Clock

internal class InMemoryResourceRepositoryTest : ResourceRepositoryTests<InMemoryResourceRepository>() {
  override fun factory(clock: Clock) = InMemoryResourceRepository(clock)
}
