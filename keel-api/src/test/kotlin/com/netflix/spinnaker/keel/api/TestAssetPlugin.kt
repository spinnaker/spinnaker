package com.netflix.spinnaker.keel.api

import io.grpc.stub.StreamObserver

class TestAssetPlugin : AssetPluginGrpc.AssetPluginImplBase() {
  override fun supports(request: TypeMetadata, responseObserver: StreamObserver<SupportsResponse>) {
    val response = SupportsResponse
      .newBuilder()
      .setSupports(request.kind == "Test")
      .build()

    with(responseObserver) {
      onNext(response)
      onCompleted()
    }
  }
}
