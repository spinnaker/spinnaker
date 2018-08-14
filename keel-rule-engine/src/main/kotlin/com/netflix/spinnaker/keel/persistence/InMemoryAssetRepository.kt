package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.AssetDesiredState
import com.netflix.spinnaker.keel.model.AssetId

class InMemoryAssetRepository : AssetRepository {

  private val assets = mutableMapOf<AssetId, AssetDesiredState>()

  override fun assets(callback: (AssetDesiredState) -> Unit) {
    assets.values.forEach(callback)
  }

  override fun store(asset: AssetDesiredState) {
    assets[asset.id] = asset
  }
}

