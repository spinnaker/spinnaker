package com.netflix.spinnaker.keel.registry

import com.netflix.discovery.EurekaClient
import com.netflix.spinnaker.keel.api.GrpcStubManager
import com.netflix.spinnaker.keel.api.TypeMetadata
import com.netflix.spinnaker.keel.api.VetoPluginGrpc.VetoPluginBlockingStub
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterAssetPluginResponse
import com.netflix.spinnaker.keel.api.engine.RegisterVetoPluginRequest
import com.netflix.spinnaker.keel.api.engine.RegisterVetoPluginResponse
import com.netflix.spinnaker.keel.api.instanceInfo
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc.AssetPluginBlockingStub
import com.netflix.spinnaker.keel.api.plugin.AssetPluginGrpc.AssetPluginImplBase
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.check
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.inOrder
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.times
import com.nhaarman.mockito_kotlin.verify
import com.nhaarman.mockito_kotlin.verifyZeroInteractions
import com.nhaarman.mockito_kotlin.whenever
import io.grpc.stub.StreamObserver
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import org.jetbrains.spek.api.dsl.on
import strikt.api.expect
import strikt.assertions.isA
import strikt.assertions.isNotNull
import strikt.assertions.isNull
import strikt.assertions.isTrue

internal object PluginRegistrationSpec : Spek({

  val eurekaClient: EurekaClient = mock()
  val type = TypeMetadata
    .newBuilder()
    .apply {
      apiVersion = "1.0"
      kind = "aws/SecurityGroup"
    }
    .build()

  val grpc = GrpcStubManager(AssetPluginGrpc::newBlockingStub)

  beforeGroup {
    grpc.startServer {
      addService(object : AssetPluginImplBase() {})
    }

    whenever(eurekaClient.getNextServerFromEureka(any(), eq(false))) doReturn grpc.instanceInfo
  }

  afterGroup {
    grpc.stopServer()
    reset(eurekaClient)
  }

  describe("registering an asset plugin") {
    given("no plugin is registered for an asset type") {
      val subject = GrpcPluginRegistry(eurekaClient)
      it("no stub is returned for an unknown asset type") {
        subject.pluginFor(type).let {
          expect(it).isNull()
        }
      }
    }

    given("a plugin is registered for an asset type") {
      val subject = GrpcPluginRegistry(eurekaClient)
      val responseHandler: StreamObserver<RegisterAssetPluginResponse> = mock()

      afterGroup { reset(responseHandler) }

      given("a plugin was registered") {
        subject.registerAssetPlugin(
          RegisterAssetPluginRequest
            .newBuilder()
            .apply {
              vipAddress = "aws-asset-plugin"
              addTypes(type)
            }
            .build(),
          responseHandler
        )
      }

      it("the registration request succeeds") {
        inOrder(responseHandler) {
          verify(responseHandler).onNext(check {
            expect(it.succeeded).isTrue()
          })
          verify(responseHandler).onCompleted()
        }
      }

      it("the registry can now supply a stub for talking to the plugin") {
        subject.pluginFor(type).let {
          expect(it).isNotNull().isA<AssetPluginBlockingStub>()
        }
      }
    }
  }

  describe("registering a veto plugin") {
    val vetoCallback: (VetoPluginBlockingStub) -> Any = mock()

    given("no plugins are registered") {
      val subject = GrpcPluginRegistry(eurekaClient)

      afterGroup { reset(vetoCallback) }

      on("applying vetos") {
        subject.applyVetos(vetoCallback)
      }

      it("the callback is never invoked") {
        verifyZeroInteractions(vetoCallback)
      }
    }

    given("a veto plugin is registered") {
      val subject = GrpcPluginRegistry(eurekaClient)
      val responseHandler: StreamObserver<RegisterVetoPluginResponse> = mock()

      afterGroup { reset(responseHandler, vetoCallback) }

      given("plugins were registered") {
        sequenceOf("execution-window", "cloud-capacity").forEach {
          subject.registerVetoPlugin(
            RegisterVetoPluginRequest.newBuilder().setVipAddress(it).build(),
            responseHandler
          )
        }
      }

      it("the registration request succeeds") {
        verify(responseHandler, times(2)).onNext(check {
          expect(it.succeeded).isTrue()
        })
      }

      on("applying vetos") {
        subject.applyVetos(vetoCallback)
      }

      it("the registry can now supply a stub for talking to the plugin") {
        verify(vetoCallback, times(2)).invoke(check {
          expect(it).isA<VetoPluginBlockingStub>()
        })
      }
    }
  }
})
