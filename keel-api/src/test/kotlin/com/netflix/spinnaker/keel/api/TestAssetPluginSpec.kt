package com.netflix.spinnaker.keel.api

import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.specification.describe
import strikt.api.expect
import strikt.assertions.isFalse
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
    it("supports a Test request") {
      val request = TypeMetadata
        .newBuilder()
        .setApiVersion("1.0")
        .setKind("Test")
        .build()

      grpc.withChannel {
        val response = it.supports(request)

        expect(response.supports).isTrue()
      }
    }

    it("does not support any other kind of request") {
      val request = TypeMetadata
        .newBuilder()
        .setApiVersion("1.0")
        .setKind("Whatever")
        .build()

      grpc.withChannel {
        val response = it.supports(request)

        expect(response.supports).isFalse()
      }
    }
  }
})
