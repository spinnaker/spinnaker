package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

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

      grpc.withChannel { stub ->
        val response = stub.current(request)

        expectThat(response) {
          chain { it.hasSuccess() }.isTrue()
        }.and {
          chain { it.success.desired }.isEqualTo(request.asset)
          chain { it.success.current }.isEqualTo(request.asset)
        }
      }
    }
  }
})
