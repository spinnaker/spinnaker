package com.netflix.spinnaker.keel.processing

import com.netflix.spinnaker.keel.grpc.fromProto
import com.netflix.spinnaker.keel.grpc.toProto
import com.netflix.spinnaker.keel.grpc.toTypeMetaData
import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.registry.GrpcAssetPluginRegistry
import com.netflix.spinnaker.keel.registry.UnsupportedAssetType
import org.springframework.stereotype.Component
import com.netflix.spinnaker.keel.api.Asset as AssetProto
import com.netflix.spinnaker.keel.api.AssetId as AssetIdProto

@Component
class GrpcAssetService(
  private val registry: GrpcAssetPluginRegistry
) : AssetService {
  // TODO: this would be ripe for a suspending function if not using gRPC blocking stub
  override fun current(asset: Asset): Asset? {
    val typeMetaData = asset.toTypeMetaData()
    val stub = registry
      .pluginFor(typeMetaData) ?: throw UnsupportedAssetType(typeMetaData)
    return stub.current(asset.toProto()).let {
      if (it.hasAsset()) {
        it.asset.fromProto()
      } else {
        null
      }
    }
  }

  override fun converge(asset: Asset) {
    TODO("not implemented")
  }
}
