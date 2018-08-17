package com.netflix.spinnaker.keel.grpc

import com.google.protobuf.ByteString
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.api.Asset as AssetProto
import com.netflix.spinnaker.keel.api.AssetId as AssetIdProto

/**
 * Converts an asset model into the protobuf representation.
 */
fun Asset.toProto(): AssetProto =
  AssetProto
    .newBuilder()
    .also {
      it.idBuilder.value = id.value
      it.typeMetadata = toTypeMetaData()
      it.addAllDependsOn(dependsOn.map(AssetId::toProto))
      it.specBuilder.value = ByteString.copyFrom(spec)
    }
    .build()

/**
 * Converts an asset id model into the protobuf representation.
 */
fun AssetId.toProto(): AssetIdProto =
  AssetIdProto.newBuilder().setValue(value).build()

/**
 * Converts an asset model into the protobuf type metadata representation.
 */
fun Asset.toTypeMetaData(): TypeMetadata =
  TypeMetadata
    .newBuilder()
    .also {
      it.apiVersion = apiVersion
      it.kind = kind
    }
    .build()

/**
 * Converts an asset protobuf into the model representation.
 */
fun AssetProto.fromProto(): Asset =
  Asset(
    id = AssetId(id.value),
    apiVersion = typeMetadata.apiVersion,
    kind = typeMetadata.kind,
    dependsOn = dependsOnList.map { AssetId(it.value) }.toSet(),
    spec = spec.value.toByteArray()
  )
