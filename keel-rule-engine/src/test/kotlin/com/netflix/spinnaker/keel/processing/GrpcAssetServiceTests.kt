package com.netflix.spinnaker.keel.processing

import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.GrpcStubManager
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginResponse
import com.netflix.spinnaker.keel.api.instanceInfo
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import com.netflix.spinnaker.keel.api.plugin.ConvergeResponse
import com.netflix.spinnaker.keel.api.plugin.ConvergeStatus.ACCEPTED
import com.netflix.spinnaker.keel.api.plugin.CurrentResponse
import com.netflix.spinnaker.keel.grpc.GrpcAssetService
import com.netflix.spinnaker.keel.grpc.GrpcPluginRegistry
import com.netflix.spinnaker.keel.grpc.toProto
import com.netflix.spinnaker.keel.grpc.toTypeMetaData
import com.netflix.spinnaker.keel.model.Asset
import com.netflix.spinnaker.keel.model.AssetId
import com.netflix.spinnaker.keel.registry.InMemoryPluginRepository
import com.netflix.spinnaker.keel.registry.UnsupportedAssetType
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.argWhere
import com.nhaarman.mockito_kotlin.doAnswer
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.Assertion
import strikt.api.catching
import strikt.api.expectThat
import strikt.assertions.contentEquals
import strikt.assertions.isEqualTo
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.throws
import com.netflix.spinnaker.keel.api.Asset as AssetProto
import com.netflix.spinnaker.keel.api.AssetContainer as AssetContainerProto

internal class GrpcAssetServiceTests {
  val grpc = GrpcStubManager(AssetPluginGrpc::newBlockingStub)
  val plugin: AssetPluginGrpc.AssetPluginImplBase = mock()
  val eureka: EurekaClient = mock()
  val registry = GrpcPluginRegistry(eureka, InMemoryPluginRepository())
  val subject = GrpcAssetService(registry)

  @BeforeEach
  fun startPlugin() {
    grpc.startServer {
      addService(plugin)
    }

    val pluginVip = "ec2-plugin"
    whenever(eureka.getNextServerFromEureka(pluginVip, false)) doReturn grpc.instanceInfo

    val responseCallback: StreamObserver<RegisterAssetPluginResponse> = mock()
    registry.registerAssetPlugin(RegisterAssetPluginRequest.newBuilder().also {
      it.vip = pluginVip
      it.port = grpc.port
      it.addType(asset.toTypeMetaData())
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
    id = AssetId("SecurityGroup:ec2:prod:us-west-2:keel"),
    kind = "ec2:SecurityGroup",
    spec = randomBytes()
  )

  @Test
  fun `current throws an exception if no registered plugin supports an asset type`() {
    expectThat(catching {
      subject.current(asset.copy(kind = "ElasticLoadBalancer:ec2:prod:us-west-2:keel").wrap())
    }).throws<UnsupportedAssetType>()

    verifyZeroInteractions(plugin)
  }

  @Test
  fun `current returns null if asset does not currently exist`() {
    handleCurrent { _, responseObserver ->
      with(responseObserver) {
        onNext(CurrentResponse.newBuilder().also {
          it.successBuilder.desired = asset.toProto()
        }.build())
        onCompleted()
      }
    }

    subject.current(asset.wrap()).also { result ->
      expectThat(result.current).isNull()
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
      expectThat(result.current)
        .isNotNull()
        .isIdenticalTo(asset)
    }
  }

  @Test
  fun `converge invokes the plugin`() {
    handleConverge { _, responseObserver ->
      with(responseObserver) {
        onNext(ConvergeResponse.newBuilder().setStatus(ACCEPTED).build())
        onCompleted()
      }
    }

    subject.converge(asset.wrap())

    verify(plugin).converge(eq(asset.wrap().toProto()), any())
  }

  fun Asset.toCurrentResponse(): CurrentResponse? {
    return CurrentResponse
      .newBuilder()
      .also {
        it.successBuilder.current = toProto()
        it.successBuilder.desired = toProto()
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

  fun handleConverge(handler: (AssetContainerProto, StreamObserver<ConvergeResponse>) -> Unit) {
    whenever(plugin.converge(any(), any())) doAnswer {
      val asset = it.getArgument<AssetContainerProto>(0)
      val observer = it.getArgument<StreamObserver<ConvergeResponse>>(1)
      handler(asset, observer)
    }
  }
}

fun Assertion.Builder<Asset>.isIdenticalTo(other: Asset) =
  compose("is identical to %s", other) {
    chain(Asset::id).isEqualTo(other.id)
    chain(Asset::kind).isEqualTo(other.kind)
    chain(Asset::apiVersion).isEqualTo(other.apiVersion)
    chain(Asset::dependsOn).isEqualTo(other.dependsOn)
    chain { it.spec.type }.isEqualTo(other.spec.type)
    chain { it.spec.data }.contentEquals(other.spec.data)
  } then {
    if (allPassed) pass() else fail()
  }
