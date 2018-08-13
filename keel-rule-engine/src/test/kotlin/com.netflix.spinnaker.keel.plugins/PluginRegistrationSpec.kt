package com.netflix.spinnaker.keel.plugins

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
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
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

    val port = grpc.port

    whenever(eurekaClient.getNextServerFromEureka(any(), eq(false))) doReturn grpc.instanceInfo
  }

  afterGroup {
    grpc.stopServer()
    reset(eurekaClient)
  }

  Feature("registering an asset plugin") {
    val subject by memoized {
      AssetPluginRegistry(eurekaClient)
    }

    Scenario("no plugin is registered for an asset type") {
      Then("no stub is returned for an unknown asset type") {
        subject.pluginFor(type).let {
          expect(it).isNull()
        }
      }
    }

    Scenario("a plugin is registered for an asset type") {
      val responseHandler: StreamObserver<RegisterAssetPluginResponse> = mock()

      afterGroup { reset(responseHandler) }

      Given("a plugin was registered") {
        subject.register(
          RegisterAssetPluginRequest
            .newBuilder()
            .apply {
              name = "aws-asset-plugin"
              addTypes(type)
            }
            .build(),
          responseHandler
        )
      }

      Then("the registration request succeeds") {
        inOrder(responseHandler) {
          verify(responseHandler).onNext(check {
            expect(it.succeeded).isTrue()
          })
          verify(responseHandler).onCompleted()
        }
      }

      Then("the registry can now supply a stub for talking to the plugin") {
        subject.pluginFor(type).let {
          expect(it).isNotNull().isA<AssetPluginBlockingStub>()
        }
      }
    }
  }

  Feature("registering a veto plugin") {
    val vetoCallback: (VetoPluginBlockingStub) -> Any = mock()

    val subject by memoized {
      VetoPluginRegistry(eurekaClient)
    }

    Scenario("no plugins are registered") {

      afterGroup { reset(vetoCallback) }

      When("applying vetos") {
        subject.applyVetos(vetoCallback)
      }

      Then("the callback is never invoked") {
        verifyZeroInteractions(vetoCallback)
      }
    }

    Scenario("a plugin is registered for an asset type") {
      val responseHandler: StreamObserver<RegisterVetoPluginResponse> = mock()

      afterGroup { reset(responseHandler, vetoCallback) }

      Given("plugins were registered") {
        sequenceOf("execution-window", "cloud-capacity").forEach {
          subject.register(
            RegisterVetoPluginRequest.newBuilder().setName(it).build(),
            responseHandler
          )
        }
      }

      Then("the registration request succeeds") {
        verify(responseHandler, times(2)).onNext(check {
          expect(it.succeeded).isTrue()
        })
      }

      When("applying vetos") {
        subject.applyVetos(vetoCallback)
      }

      Then("the registry can now supply a stub for talking to the plugin") {
        verify(vetoCallback, times(2)).invoke(check {
          expect(it).isA<VetoPluginBlockingStub>()
        })
      }
    }
  }

})
