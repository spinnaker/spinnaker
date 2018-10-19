package com.netflix.spinnaker.keel.persistence

import java.time.Clock
import java.time.Instant
import java.time.ZoneId

internal class InMemoryAssetRepositoryTest : AssetRepositoryTests<InMemoryAssetRepository>() {

  val clock = Clock.fixed(Instant.now(), ZoneId.systemDefault())

  override fun factory() = InMemoryAssetRepository(clock)
}
