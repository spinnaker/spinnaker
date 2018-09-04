package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.plugin.CurrentResponse
import com.netflix.spinnaker.keel.plugin.AssetPlugin
import io.grpc.stub.StreamObserver

class TestAssetPlugin : AssetPlugin() {
  override val supportedTypes: Iterable<TypeMetadata>
    get() = TODO("not implemented")

  override fun current(request: AssetContainer, responseObserver: StreamObserver<CurrentResponse>) {
    with(responseObserver) {
      onNext(CurrentResponse
        .newBuilder()
        .also {
          it.desired = request.asset
          it.current = request.asset
        }
        .build()
      )
      onCompleted()
    }
  }
}
