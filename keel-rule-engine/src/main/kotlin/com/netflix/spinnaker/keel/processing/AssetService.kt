package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetContainer

/**
 * Facade for gRPC asset plugins.
 */
interface AssetService {
  fun current(assetContainer: AssetContainer): CurrentAssetPair
  fun converge(assetContainer: AssetContainer)
}

data class CurrentAssetPair(
  val desired: Asset,
  val current: Asset?
)
