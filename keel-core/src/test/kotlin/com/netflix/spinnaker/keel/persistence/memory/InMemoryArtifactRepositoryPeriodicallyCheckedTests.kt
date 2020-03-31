package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.ArtifactRepositoryPeriodicallyCheckedTests
import java.time.Clock

class InMemoryArtifactRepositoryPeriodicallyCheckedTests :
ArtifactRepositoryPeriodicallyCheckedTests<InMemoryArtifactRepository>() {
  override val factory: (Clock) -> InMemoryArtifactRepository = { clock ->
    InMemoryArtifactRepository(clock)
  }
}
