package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc.AssetPluginImplBase

abstract class AssetPlugin : AssetPluginImplBase() {
  abstract val supportedTypes: Iterable<TypeMetadata>
}

val AssetPlugin.registrationRequest: RegisterAssetPluginRequest
  get() = RegisterAssetPluginRequest
    .newBuilder()
    .addAllTypes(supportedTypes)
    .build()
