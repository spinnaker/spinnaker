package com.netflix.spinnaker.keel.events

import com.netflix.spinnaker.keel.api.Asset

data class AssetEvent(
  val type: AssetEventType,
  val asset: Asset<*>
)
