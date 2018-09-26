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

import com.netflix.spinnaker.keel.api.AssetRegistryGrpc
import com.netflix.spinnaker.keel.api.ManagedAssetResponse
import com.netflix.spinnaker.keel.api.ManagedAssetsRequest
import com.netflix.spinnaker.keel.api.UpsertAssetRequest
import com.netflix.spinnaker.keel.api.UpsertAssetResponse
import com.netflix.spinnaker.keel.api.UpsertAssetResult
import com.netflix.spinnaker.keel.api.UpsertAssetStatus.INSERTED
import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.PartialAsset
import com.netflix.spinnaker.keel.persistence.AssetRepository
import io.grpc.stub.StreamObserver
import org.lognet.springboot.grpc.GRpcService
import org.slf4j.LoggerFactory

@GRpcService
class GrpcAssetRegistry(
  private val assetRepository: AssetRepository
) : AssetRegistryGrpc.AssetRegistryImplBase() {

  override fun managedAssets(
    request: ManagedAssetsRequest,
    responseObserver: StreamObserver<ManagedAssetResponse>
  ) {
    with(responseObserver) {
      assetRepository.allAssets { asset ->
        val response = ManagedAssetResponse
          .newBuilder()
          .run {
            when (asset) {
              is Asset -> setAsset(asset.toProto())
              is PartialAsset -> setPartialAsset(asset.toProto())
            }
            build()
          }
        onNext(response)
      }
      onCompleted()
    }
  }

  override fun upsertAsset(
    request: UpsertAssetRequest,
    responseObserver: StreamObserver<UpsertAssetResponse>
  ) {
    with(request.asset) {
      log.info("Upserting asset: {} with spec {}", asset.id.value, asset.spec.typeUrl)
      request.asset.partialAssetList.forEach {
        log.info("Upserting partial: {} with spec {}", it.id.value, it.spec.typeUrl)
      }

      val upserted = listOf(asset.fromProto()) + partialAssetList.map { it.fromProto() }
      upserted.forEach(assetRepository::store)

      with(responseObserver) {
        onNext(UpsertAssetResponse.newBuilder().apply {
          upserted.forEach {
            addResult(UpsertAssetResult.newBuilder().apply {
              status = INSERTED
              id = it.id.toProto()
            })
          }
        }.build())
        onCompleted()
      }
    }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}
