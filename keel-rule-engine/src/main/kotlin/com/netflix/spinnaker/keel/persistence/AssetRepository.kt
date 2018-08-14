package com.netflix.spinnaker.keel.persistence

import com.netflix.spinnaker.keel.model.AssetDesiredState

interface AssetRepository {

  fun assets(callback: (AssetDesiredState) -> Unit)

  fun store(asset: AssetDesiredState)

}
