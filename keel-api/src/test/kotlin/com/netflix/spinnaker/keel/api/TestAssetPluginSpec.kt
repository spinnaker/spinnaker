package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import strikt.api.expect
import strikt.assertions.isEqualTo

internal object TestAssetPluginSpec : Spek({

  val grpc = GrpcStubManager(AssetPluginGrpc::newBlockingStub)

  beforeGroup {
    grpc.startServer {
      addService(TestAssetPlugin())
    }
  }

  afterGroup(grpc::stopServer)

  describe("a generic gRPC service") {
    it("supports a current request") {
      val request = AssetContainer.newBuilder().apply {
        asset = assetBuilder.apply {
          typeMetadataBuilder.apply {
            apiVersion = "1.0"
            kind = "Test"
          }
        }
          .build()
      }
        .build()

      grpc.withChannel {
        val response = it.current(request)

        expect(response.desired).isEqualTo(request.asset)
        expect(response.current).isEqualTo(request.asset)
      }
    }
  }
})
