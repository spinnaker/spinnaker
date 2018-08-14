package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.Asset

interface AssetRepository {

  fun assets(callback: (Asset) -> Unit)

  fun store(asset: Asset)

}
