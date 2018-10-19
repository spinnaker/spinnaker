package com.netflix.spinnaker.keel.persistence

import java.time.Clock

internal class InMemoryAssetRepositoryTest : AssetRepositoryTests<InMemoryAssetRepository>() {
  override fun factory(clock: Clock) = InMemoryAssetRepository(clock)
}
