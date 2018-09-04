package com.netflix.spinnaker.keel.plugin

import com.netflix.appinfo.InstanceInfo
import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.AllowResponse
import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.Decision.proceed
import com.netflix.spinnaker.keel.api.VetoPluginGrpc
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.reflection.v1alpha.ServerReflectionGrpc
import io.grpc.reflection.v1alpha.ServerReflectionRequest
import io.grpc.reflection.v1alpha.ServerReflectionResponse
import io.grpc.stub.StreamObserver
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.lognet.springboot.grpc.GRpcServerRunner
import org.lognet.springboot.grpc.GRpcService
import org.lognet.springboot.grpc.autoconfigure.GRpcServerProperties
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT
import org.springframework.boot.test.mock.mockito.MockBean
import org.springframework.context.ApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.test.context.junit4.SpringRunner
import strikt.api.expect
import strikt.assertions.contains
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEmpty
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit.SECONDS


@RunWith(SpringRunner::class)
@SpringBootTest(
  classes = [TestPlugin::class],
  webEnvironment = RANDOM_PORT,
  properties = ["grpc.enableReflection=true"]
)
internal class GrpcStartupTest {

  @Autowired(required = false)
  @Qualifier("grpcServerRunner")
  lateinit var grpcServerRunner: GRpcServerRunner

  @Autowired(required = false)


  lateinit var channel: ManagedChannel

  @Autowired
  lateinit var context: ApplicationContext

  @Autowired
  lateinit var gRpcServerProperties: GRpcServerProperties

  @Before
  fun setupChannels() {
    if (gRpcServerProperties.isEnabled) {
      channel = ManagedChannelBuilder
        .forAddress("localhost", getPort())
        .usePlaintext()
        .build()
    }
  }

  fun getPort() = gRpcServerProperties.port

  @After
  fun shutdownChannels() {
    channel?.shutdownNow()
  }

  @Test
  fun `exposes the plugin service over gRPC on startup`() {
    val discoveredServiceNames = mutableListOf<String>()
    val request = ServerReflectionRequest.newBuilder().setListServices("services").setHost("localhost").build()
    val latch = CountDownLatch(1)
    ServerReflectionGrpc.newStub(channel).serverReflectionInfo(object : StreamObserver<ServerReflectionResponse> {
      override fun onNext(value: ServerReflectionResponse) {
        val serviceList = value.listServicesResponse.serviceList
        for (serviceResponse in serviceList) {
          discoveredServiceNames.add(serviceResponse.name)
        }
      }

      override fun onError(t: Throwable) {

      }

      override fun onCompleted() {
        latch.countDown()
      }
    }).onNext(request)

    latch.await(1, SECONDS)
    expect(discoveredServiceNames)
      .isNotEmpty()
      .contains(VetoPluginGrpc.SERVICE_NAME)
  }

  @Test
  fun `can invoke RPC endpoints on the plugin`() {
    val response = ManagedChannelBuilder
      .forAddress("localhost", getPort())
      .usePlaintext()
      .build()
      .let(VetoPluginGrpc::newFutureStub)
      .allow(Asset.getDefaultInstance())
    expect(response.get()).map(AllowResponse::getDecision).isEqualTo(proceed)
  }
}

@SpringBootApplication
class TestPlugin {
  @MockBean
  lateinit var eurekaClient: EurekaClient

  @Bean
  fun currentInstance(): InstanceInfo = InstanceInfo.Builder
    .newBuilder()
    .run {
      setAppName("keel-test-plugin")
      setASGName("keel-test-plugin-test-v005")
      setVIPAddress("keel-test-plugin.localhost")
      build()
    }
}

@GRpcService
class TestVetoPlugin : VetoPlugin() {
  override fun allow(request: Asset, responseObserver: StreamObserver<AllowResponse>) {
    with(responseObserver) {
      onNext(AllowResponse
        .newBuilder()
        .apply { decision = proceed }
        .build())
      onCompleted()
    }
  }
}
