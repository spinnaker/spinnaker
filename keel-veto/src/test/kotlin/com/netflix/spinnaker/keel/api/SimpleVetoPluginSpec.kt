package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.Decision.halt
import com.netflix.spinnaker.keel.api.Decision.proceed
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import org.spekframework.spek2.Spek
import org.spekframework.spek2.style.gherkin.Feature
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal object SimpleVetoPluginSpec : Spek({

  val grpc = GrpcStubManager(VetoPluginGrpc::newBlockingStub)
  val dynamicConfigService = mock<DynamicConfigService>()

  beforeGroup {
    grpc.startServer {
      addService(SimpleVetoPlugin(dynamicConfigService))
    }
  }

  Feature("vetoing asset convergence") {
    Scenario("convergence is enabled") {
      beforeGroup {
        whenever(dynamicConfigService.isEnabled("keel.converge.enabled", false)) doReturn true
      }

      afterGroup {
        reset(dynamicConfigService)
      }

      Then("the plugin approves an asset convergence") {
        val request = Asset
          .newBuilder()
          .apply {
            typeMetadataBuilder.apply {
              kind = "ec2.SecurityGroup"
              apiVersion = "1.0"
            }
          }
          .build()

        grpc.withChannel { stub ->
          expectThat(stub.allow(request).decision).isEqualTo(proceed)
        }
      }
    }

    Scenario("convergence is disabled") {
      beforeGroup {
        whenever(dynamicConfigService.isEnabled("keel.converge.enabled", false)) doReturn false
      }

      afterGroup {
        reset(dynamicConfigService)
      }

      Then("the plugin approves an asset convergence") {
        val request = Asset
          .newBuilder()
          .apply {
            typeMetadataBuilder.apply {
              kind = "ec2.SecurityGroup"
              apiVersion = "1.0"
            }
          }
          .build()

        grpc.withChannel { stub ->
          expectThat(stub.allow(request).decision).isEqualTo(halt)
        }
      }
    }
  }

})
