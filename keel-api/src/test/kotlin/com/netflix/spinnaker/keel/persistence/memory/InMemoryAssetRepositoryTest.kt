package com.netflix.spinnaker.keel.persistence.memory

import com.netflix.spinnaker.keel.persistence.AssetRepositoryTests
import java.time.Clock

internal class InMemoryAssetRepositoryTest : AssetRepositoryTests<InMemoryAssetRepository>() {
  override fun factory(clock: Clock) = InMemoryAssetRepository(clock)
}
