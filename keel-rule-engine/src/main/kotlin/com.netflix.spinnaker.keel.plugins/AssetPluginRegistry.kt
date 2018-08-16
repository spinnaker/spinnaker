package com.netflix.spinnaker.keel.plugins

import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc.AssetPluginBlockingStub

interface AssetPluginRegistry {
  // TODO: use a non-gRPC return type
  fun pluginFor(type: TypeMetadata): AssetPluginBlockingStub?
}
