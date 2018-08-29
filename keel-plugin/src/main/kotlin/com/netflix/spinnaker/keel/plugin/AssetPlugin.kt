package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc.AssetPluginImplBase

abstract class AssetPlugin : AssetPluginImplBase() {
  abstract val supportedTypes: Iterable<TypeMetadata>
}
