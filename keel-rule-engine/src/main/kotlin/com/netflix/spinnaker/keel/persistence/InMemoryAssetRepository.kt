package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId

class InMemoryAssetRepository : AssetRepository {

  private val assets = mutableMapOf<AssetId, Asset>()

  override fun assets(callback: (Asset) -> Unit) {
    assets.values.forEach(callback)
  }

  override fun get(id: AssetId): Asset? =
    assets[id]

  override fun store(asset: Asset) {
    assets[asset.id] = asset
  }
}

