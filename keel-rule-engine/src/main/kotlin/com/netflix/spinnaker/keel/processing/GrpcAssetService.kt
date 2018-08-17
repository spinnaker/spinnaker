package com.netflix.spinnaker.keel.processing

import com.google.protobuf.ByteString
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
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

  private fun Asset.toProto(): AssetProto =
    AssetProto
      .newBuilder()
      .also {
        it.typeMetadata = toTypeMetaData()
        it.addDependsOn(id.toProto())
        it.specBuilder.value = ByteString.copyFrom(spec)
      }
      .build()

  private fun AssetId.toProto(): AssetIdProto =
    AssetIdProto.newBuilder().setValue(value).build()

  private fun Asset.toTypeMetaData(): TypeMetadata =
    TypeMetadata
      .newBuilder()
      .also {
        it.apiVersion = apiVersion
        it.kind = kind
      }
      .build()

  private fun AssetProto.fromProto(): Asset =
    Asset(
      id = AssetId(id.value),
      apiVersion = typeMetadata.apiVersion,
      kind = typeMetadata.kind,
      dependsOn = dependsOnList.map { AssetId(it.value) }.toSet(),
      spec = spec.value.toByteArray()
    )
}
