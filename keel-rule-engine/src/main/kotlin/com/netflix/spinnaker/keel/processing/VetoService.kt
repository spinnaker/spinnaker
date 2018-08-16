package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.model.Asset

interface VetoService {
  // TODO: probably need a more complex return type
  fun allow(asset: Asset): Boolean
}
