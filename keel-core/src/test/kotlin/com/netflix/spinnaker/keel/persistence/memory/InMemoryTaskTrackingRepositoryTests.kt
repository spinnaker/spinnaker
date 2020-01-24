package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.TaskTrackingRepositoryTests
import java.time.Clock

class InMemoryTaskTrackingRepositoryTests : TaskTrackingRepositoryTests<InMemoryTaskTrackingRepository>() {
  override fun factory(clock: Clock): InMemoryTaskTrackingRepository {
    return InMemoryTaskTrackingRepository(clock)
  }
}
