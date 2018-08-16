package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.Asset

/**
 * Facade for gRPC asset plugins.
 */
interface AssetService {
  fun current(desired: Asset): Asset
  fun converge(desired: Asset): Unit
}
