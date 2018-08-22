package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.persistence.AssetState.Unknown
import java.time.Clock
import java.time.Instant

class InMemoryAssetRepository(
  private val clock: Clock
) : AssetRepository {
  private val assets = mutableMapOf<AssetId, Asset>()
  private val states = mutableMapOf<AssetId, Pair<AssetState, Instant>>()

  override fun rootAssets(callback: (Asset) -> Unit) {
    assets.values.filter { it.dependsOn.isEmpty() }.forEach(callback)
  }

  override fun get(id: AssetId): Asset? =
    assets[id]

  override fun store(asset: Asset) {
    assets[asset.id] = asset
    states[asset.id] = Unknown to clock.instant()
  }

  override fun dependents(id: AssetId): Iterable<AssetId> =
    assets
      .filter { it.value.dependsOn.contains(id) }
      .keys

  override fun lastKnownState(id: AssetId): Pair<AssetState, Instant>? =
    states[id]

  override fun updateState(id: AssetId, state: AssetState) {
    states[id] = state to clock.instant()
  }

  internal fun dropAll() {
    assets.clear()
  }
}

