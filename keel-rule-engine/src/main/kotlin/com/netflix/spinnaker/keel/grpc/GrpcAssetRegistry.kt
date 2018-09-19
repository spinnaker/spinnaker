package com.netflix.spinnaker.keel.grpc

import com.netflix.spinnaker.keel.api.AssetRegistryGrpc
import com.netflix.spinnaker.keel.api.UpsertAssetRequest
import com.netflix.spinnaker.keel.api.UpsertAssetResponse
import com.netflix.spinnaker.keel.api.UpsertAssetResult
import com.netflix.spinnaker.keel.api.UpsertAssetStatus.INSERTED
import com.netflix.spinnaker.keel.persistence.AssetRepository
import io.grpc.stub.StreamObserver
import org.lognet.springboot.grpc.GRpcService
import org.slf4j.LoggerFactory

@GRpcService
class GrpcAssetRegistry(
  private val assetRepository: AssetRepository
) : AssetRegistryGrpc.AssetRegistryImplBase() {
  override fun upsertAsset(
    request: UpsertAssetRequest,
    responseObserver: StreamObserver<UpsertAssetResponse>
  ) {
    with(request.asset) {
      log.info("Upserting asset {}", asset.id)

      val upserted = listOf(asset.fromProto()) + partialAssetsList.map { it.fromProto() }
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
