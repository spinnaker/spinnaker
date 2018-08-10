package com.netflix.spinnaker.config

import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.GrpcStubManager
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.engine.AssetPluginRegistryGrpc
import com.netflix.spinnaker.keel.api.engine.AssetPluginRegistryGrpc.AssetPluginRegistryImplBase
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginResponse
import com.netflix.spinnaker.keel.aws.AmazonAssetPlugin
import com.netflix.spinnaker.keel.aws.PluginRegistrar
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

internal class PluginRegistrationTest {

  val grpc = GrpcStubManager(AssetPluginRegistryGrpc::newBlockingStub)

  val registeredTypes = mutableListOf<String>()
  val registry: AssetPluginRegistryImplBase = object : AssetPluginRegistryImplBase() {
    override fun register(request: RegisterAssetPluginRequest, responseObserver: StreamObserver<RegisterAssetPluginResponse>) {
      registeredTypes.addAll(request.typesList.map(TypeMetadata::getKind))
      with(responseObserver) {
        onNext(RegisterAssetPluginResponse.newBuilder().setSucceeded(true).build())
        onCompleted()
      }
    }
  }
  val eurekaClient: EurekaClient = mock()
  val amazonAssetPlugin = AmazonAssetPlugin(mock(), mock(), mock())
  val registrar = PluginRegistrar(eurekaClient, listOf(amazonAssetPlugin))

  @BeforeEach
  fun startRegistry() {
    grpc.startServer {
      addService(registry)
    }
    whenever(eurekaClient.getNextServerFromEureka("keel", false)) doReturn grpc.instanceInfo
  }

  @AfterEach
  fun stopRegistry() {
    grpc.stopServer()
    reset(eurekaClient)
  }

  @Test
  fun `registers plugins on startup`() {
    registrar.registerPlugins()

    expect(registeredTypes)
      .containsExactlyInAnyOrder(
        "aws.SecurityGroup",
        "aws.ClassicLoadBalancer"
      )
  }
}
