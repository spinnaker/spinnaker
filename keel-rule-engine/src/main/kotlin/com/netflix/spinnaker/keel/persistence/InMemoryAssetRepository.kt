package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.persistence.AssetState.Unknown
import java.time.Clock

class InMemoryAssetRepository(
  private val clock: Clock
) : AssetRepository {
  private val assets = mutableMapOf<AssetId, Asset>()
  private val states = mutableMapOf<AssetId, AssetState>()

  override fun assets(callback: (Asset) -> Unit) {
    assets.values.forEach(callback)
  }

  override fun get(id: AssetId): Asset? =
    assets[id]

  override fun store(asset: Asset) {
    assets[asset.id] = asset
    states[asset.id] = Unknown(clock.instant())
  }

  override fun dependents(id: AssetId): Iterable<AssetId> =
    assets
      .filter { it.value.dependsOn.contains(id) }
      .keys

  override fun lastKnownState(id: AssetId): AssetState? =
    states[id]

  internal fun dropAll() {
    assets.clear()
  }
}

