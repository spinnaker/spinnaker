package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import io.grpc.stub.StreamObserver

class TestAssetPlugin : AssetPluginGrpc.AssetPluginImplBase() {
  override fun current(request: Asset, responseObserver: StreamObserver<Asset>) {
    with(responseObserver) {
      onNext(request)
      onCompleted()
    }
  }
}
