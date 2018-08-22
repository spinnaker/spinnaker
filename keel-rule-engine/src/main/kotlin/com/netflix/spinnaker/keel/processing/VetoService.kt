package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.AssetContainer

interface VetoService {
  // TODO: probably need a more complex return type
  fun allow(asset: AssetContainer): Boolean
}
