package com.netflix.spinnaker.keel.registry

import com.netflix.spinnaker.keel.api.TypeMetadata

interface PluginRepository {
  fun vetoPlugins(): Iterable<PluginAddress>

  fun addVetoPlugin(address: PluginAddress)

  fun assetPluginFor(type: TypeMetadata): PluginAddress? // TODO: don't expose gRPC type here

  fun addAssetPluginFor(type: TypeMetadata, address: PluginAddress)
}
