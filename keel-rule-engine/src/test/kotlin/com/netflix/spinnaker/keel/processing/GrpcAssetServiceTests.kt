package com.netflix.spinnaker.keel.processing

import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.GrpcStubManager
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginResponse
import com.netflix.spinnaker.keel.api.instanceInfo
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.registry.GrpcAssetPluginRegistry
import com.netflix.spinnaker.keel.registry.UnsupportedAssetType
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.api.throws
import strikt.assertions.isNull
import strikt.assertions.isTrue
import com.netflix.spinnaker.keel.api.Asset as AssetProto

internal class GrpcAssetServiceTests {
  val grpc = GrpcStubManager(AssetPluginGrpc::newBlockingStub)
  val plugin: AssetPluginGrpc.AssetPluginImplBase = mock()
  val eureka: EurekaClient = mock()
  val registry = GrpcAssetPluginRegistry(eureka)
  val subject = GrpcAssetService(registry)

  @BeforeEach
  fun startPlugin() {
    grpc.startServer {
      addService(plugin)
    }
    whenever(eureka.getNextServerFromEureka("aws-plugin", false)) doReturn grpc.instanceInfo
  }

  @AfterEach
  fun stopPlugin() {
    grpc.stopServer()
  }

  val asset = Asset(
    id = AssetId("SecurityGroup:aws:prod:us-west-2:keel"),
    kind = "aws:SecurityGroup",
    spec = randomBytes()
  )

  @Test
  fun `current throws an exception if no registered plugin supports an asset type`() {
    throws<UnsupportedAssetType> {
      subject.current(asset)
    }
  }

  @Test
  fun `current returns null if asset does not currently exist`() {
    registry.register(RegisterAssetPluginRequest.newBuilder().also {
      it.name = "aws-plugin"
      it.addTypes(TypeMetadata.newBuilder().apply {
        kind = asset.kind
        apiVersion = asset.apiVersion
      })
    }.build(), object : StreamObserver<RegisterAssetPluginResponse> {
      override fun onNext(value: RegisterAssetPluginResponse) {
        expect(value.succeeded).isTrue()
      }

      override fun onError(t: Throwable) {
      }

      override fun onCompleted() {
      }
    })

    whenever(plugin.current(any(), any())) doAnswer {
      val observer = it.getArgument<StreamObserver<AssetProto>>(1)
      with(observer) {
        onNext(null)
        onCompleted()
      }
    }

    subject.current(asset).also { result ->
      expect(result).isNull()
    }
  }
}
