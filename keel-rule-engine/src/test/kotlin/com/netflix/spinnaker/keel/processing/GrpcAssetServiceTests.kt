package com.netflix.spinnaker.keel.processing

import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.GrpcStubManager
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginResponse
import com.netflix.spinnaker.keel.api.instanceInfo
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import com.netflix.spinnaker.keel.api.plugin.CurrentResponse
import com.netflix.spinnaker.keel.grpc.GrpcAssetService
import com.netflix.spinnaker.keel.grpc.toProto
import com.netflix.spinnaker.keel.grpc.toTypeMetaData
import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.registry.GrpcPluginRegistry
import com.netflix.spinnaker.keel.registry.UnsupportedAssetType
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argWhere
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.Assertion
import strikt.api.expect
import strikt.api.throws
import strikt.assertions.contentEquals
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import com.netflix.spinnaker.keel.api.Asset as AssetProto
import com.netflix.spinnaker.keel.api.AssetContainer as AssetContainerProto

internal class GrpcAssetServiceTests {
  val grpc = GrpcStubManager(AssetPluginGrpc::newBlockingStub)
  val plugin: AssetPluginGrpc.AssetPluginImplBase = mock()
  val eureka: EurekaClient = mock()
  val registry = GrpcPluginRegistry(eureka)
  val subject = GrpcAssetService(registry)

  @BeforeEach
  fun startPlugin() {
    grpc.startServer {
      addService(plugin)
    }

    val pluginAddress = "aws-plugin"
    whenever(eureka.getNextServerFromEureka(pluginAddress, false)) doReturn grpc.instanceInfo

    val responseCallback: StreamObserver<RegisterAssetPluginResponse> = mock()
    registry.registerAssetPlugin(RegisterAssetPluginRequest.newBuilder().also {
      it.vipAddress = pluginAddress
      it.addTypes(asset.toTypeMetaData())
    }.build(), responseCallback)
    verify(responseCallback).onNext(argWhere {
      it.succeeded
    })
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
      subject.current(asset.copy(kind = "ElasticLoadBalancer:aws:prod:us-west-2:keel").wrap())
    }

    verifyZeroInteractions(plugin)
  }

  @Test
  fun `current returns null if asset does not currently exist`() {
    handleCurrent { _, responseObserver ->
      with(responseObserver) {
        onNext(CurrentResponse.newBuilder().also {
          it.desired = asset.toProto()
        }.build())
        onCompleted()
      }
    }

    subject.current(asset.wrap()).also { result ->
      expect(result.current).isNull()
    }
  }

  @Test
  fun `current returns the asset if asset does exist`() {
    handleCurrent { _, responseObserver ->
      with(responseObserver) {
        onNext(asset.toCurrentResponse())
        onCompleted()
      }
    }

    subject.current(asset.wrap()).also { result ->
      expect(result.current) {
        isNotNull().isIdenticalTo(asset)
      }
    }
  }

  fun Asset.toCurrentResponse(): CurrentResponse? {
    return CurrentResponse
      .newBuilder()
      .also {
        it.current = toProto()
        it.desired = toProto()
      }
      .build()
  }

  fun handleCurrent(handler: (AssetContainerProto, StreamObserver<CurrentResponse>) -> Unit) {
    whenever(plugin.current(any(), any())) doAnswer {
      val asset = it.getArgument<AssetContainerProto>(0)
      val observer = it.getArgument<StreamObserver<CurrentResponse>>(1)
      handler(asset, observer)
    }
  }
}

fun Assertion.Builder<Asset>.isIdenticalTo(other: Asset) =
  compose("is identical to %s", other) {
    map(Asset::id).isEqualTo(other.id)
    map(Asset::kind).isEqualTo(other.kind)
    map(Asset::apiVersion).isEqualTo(other.apiVersion)
    map(Asset::dependsOn).isEqualTo(other.dependsOn)
    map(Asset::spec).contentEquals(other.spec)
  } then {
    if (allPassed) pass() else fail()
  }
