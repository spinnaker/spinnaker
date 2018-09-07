/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.grpc

import com.google.protobuf.ByteString
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterVetoPluginRequest
import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetContainer
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.model.PartialAsset
import com.netflix.spinnaker.keel.registry.AssetType
import com.netflix.spinnaker.keel.registry.PluginAddress
import com.netflix.spinnaker.keel.api.Asset as AssetProto
import com.netflix.spinnaker.keel.api.AssetContainer as AssetContainerProto
import com.netflix.spinnaker.keel.api.AssetId as AssetIdProto
import com.netflix.spinnaker.keel.api.PartialAsset as PartialAssetProto

/**
 * Converts an asset container model into the protobuf representation.
 */
fun AssetContainer.toProto(): AssetContainerProto =
  AssetContainerProto
    .newBuilder()
    .also {
      it.asset = asset?.toProto()
      it.addAllPartialAssets(partialAssets.map(PartialAsset::toProto))
    }
    .build()

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
 * Converts a partial asset model into the protobuf representation.
 */
fun PartialAsset.toProto(): PartialAssetProto =
  PartialAssetProto
    .newBuilder()
    .also {
      it.idBuilder.value = id.value
      it.rootBuilder.value = root.value
      it.typeMetadata = toTypeMetaData()
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
 * Converts a partial asset model into the protobuf type metadata representation.
 */
fun PartialAsset.toTypeMetaData(): TypeMetadata =
  TypeMetadata
    .newBuilder()
    .also {
      it.apiVersion = apiVersion
      it.kind = kind
    }
    .build()

/**
 * Converts an asset container protobuf into the model representation.
 */
fun AssetContainerProto.fromProto(): AssetContainer =
  AssetContainer(
    asset = asset?.fromProto(),
    partialAssets = partialAssetsList.map { it.fromProto() }.toSet()
  )

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

/**
 * Converts an asset container protobuf into the model representation.
 */
fun PartialAssetProto.fromProto(): PartialAsset =
  PartialAsset(
    id = AssetId(id.value),
    root = AssetId(root.value),
    apiVersion = typeMetadata.apiVersion,
    kind = typeMetadata.kind,
    spec = spec.value.toByteArray()
  )


fun RegisterAssetPluginRequest.toPluginAddress() =
  PluginAddress(name, vip, port)

fun RegisterVetoPluginRequest.toPluginAddress() =
  PluginAddress(name, vip, port)

fun TypeMetadata.toAssetType() =
  AssetType(kind, apiVersion)
