package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId

interface AssetRepository {
  fun assets(callback: (Asset) -> Unit)
  fun get(id: AssetId): Asset?
  fun store(asset: Asset)
  fun dependents(id: AssetId): Iterable<AssetId>
  fun lastKnownState(id: AssetId): AssetState?
}
