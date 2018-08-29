package com.netflix.spinnaker.keel.plugin

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.GrpcStubManager
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.engine.AssetPluginRegistryGrpc
import com.netflix.spinnaker.keel.api.engine.AssetPluginRegistryGrpc.AssetPluginRegistryImplBase
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginResponse
import com.netflix.spinnaker.keel.api.instanceInfo
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import io.grpc.stub.StreamObserver
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import strikt.api.expect
import strikt.assertions.containsExactlyInAnyOrder

val registerSuccess = RegisterAssetPluginResponse.newBuilder().setSucceeded(true).build()

internal class PluginRegistrationTest {

  val grpc = GrpcStubManager(AssetPluginRegistryGrpc::newBlockingStub)

  val registeredTypes = mutableListOf<String>()
  val registry: AssetPluginRegistryImplBase = object : AssetPluginRegistryImplBase() {
    override fun register(request: RegisterAssetPluginRequest, responseObserver: StreamObserver<RegisterAssetPluginResponse>) {
      registeredTypes.addAll(request.typesList.map(TypeMetadata::getKind))
      with(responseObserver) {
        onNext(registerSuccess)
        onCompleted()
      }
    }
  }
  val keelRegistryAddress = "keel-test"
  val eurekaClient: EurekaClient = mock()
  val amazonAssetPlugin = mock<AssetPlugin>()
  val instanceInfo = InstanceInfo.Builder.newBuilder().run {
    setAppName("keelplugins")
    setASGName("keelplugins-test-aws-v005")
    setVIPAddress("keelplugins-test-aws")
    build()
  }
  val registrar = PluginRegistrar(
    eurekaClient,
    listOf(amazonAssetPlugin),
    keelRegistryAddress,
    instanceInfo
  )

  @BeforeEach
  fun startRegistry() {
    grpc.startServer {
      addService(registry)
    }
    whenever(eurekaClient.getNextServerFromEureka(keelRegistryAddress, false)) doReturn grpc.instanceInfo
    whenever(amazonAssetPlugin.supportedTypes) doReturn listOf(
      "aws.SecurityGroup",
      "aws.ClassicLoadBalancer"
    ).map(::typeMetadataForKind)
  }

  @AfterEach
  fun stopRegistry() {
    grpc.stopServer()
    reset(eurekaClient)
  }

  @Test
  fun `registers plugins on startup`() {
    registrar.onDiscoveryUp()

    expect(registeredTypes)
      .containsExactlyInAnyOrder(
        "aws.SecurityGroup",
        "aws.ClassicLoadBalancer"
      )
  }
}

fun typeMetadataForKind(it: String) =
  TypeMetadata.newBuilder().setKind(it).build()
