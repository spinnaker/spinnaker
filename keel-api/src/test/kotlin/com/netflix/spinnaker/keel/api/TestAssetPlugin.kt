package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import com.netflix.spinnaker.keel.api.plugin.CurrentResponse
import io.grpc.stub.StreamObserver

class TestAssetPlugin : AssetPluginGrpc.AssetPluginImplBase() {
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
