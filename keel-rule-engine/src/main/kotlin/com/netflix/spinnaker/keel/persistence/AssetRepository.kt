package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import java.time.Instant

interface AssetRepository {
  fun assets(callback: (Asset) -> Unit)
  fun get(id: AssetId): Asset?
  fun store(asset: Asset)
  fun dependents(id: AssetId): Iterable<AssetId>
  fun lastKnownState(id: AssetId): Pair<AssetState, Instant>?
  fun updateState(id: AssetId, state: AssetState)
}
