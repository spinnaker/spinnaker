package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

internal object TestAssetPluginTest {

  val grpc = GrpcStubManager(AssetPluginGrpc::newBlockingStub)

  @JvmStatic
  @BeforeAll
  fun startGrpc() {
    grpc.startServer {
      addService(TestAssetPlugin())
    }
  }

  @JvmStatic
  @AfterAll
  fun afterGroup() {
    grpc.stopServer()
  }

  @Test
  fun `a generic gRPC service supports a current request`() {
    val request = AssetContainer.newBuilder().apply {
      assetBuilder.apply {
        typeMetadataBuilder.apply {
          apiVersion = "1.0"
          kind = "Test"
        }
      }
    }
      .build()

    grpc.withChannel { stub ->
      val response = stub.current(request)

      expectThat(response) {
        get { hasSuccess() }.isTrue()
      }.and {
        get { success.desired }.isEqualTo(request.asset)
        get { success.current }.isEqualTo(request.asset)
      }
    }
  }
}
