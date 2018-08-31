package com.netflix.spinnaker.keel.plugin

import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.VetoPluginGrpc.VetoPluginImplBase
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc.AssetPluginImplBase
import io.grpc.BindableService

interface KeelPlugin : BindableService

abstract class AssetPlugin : KeelPlugin, AssetPluginImplBase() {
  abstract val supportedTypes: Iterable<TypeMetadata>
}

abstract class VetoPlugin : KeelPlugin, VetoPluginImplBase()
