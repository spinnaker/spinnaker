package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.api.Decision.halt
import com.netflix.spinnaker.keel.api.Decision.proceed
import com.netflix.spinnaker.keel.api.VetoPluginGrpc.VetoPluginBlockingStub
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.reset
import com.nhaarman.mockito_kotlin.whenever
import com.oneeyedmen.minutest.junit.junitTests
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.isEqualTo

internal object SimpleVetoPluginSpec {

  data class Fixture(
    val grpc: GrpcStubManager<VetoPluginBlockingStub>,
    val dynamicConfigService: DynamicConfigService
  )

  @TestFactory
  fun `vetoing asset convergence`() = junitTests<Fixture> {
    fixture {
      Fixture(
        grpc = GrpcStubManager(VetoPluginGrpc::newBlockingStub),
        dynamicConfigService = mock()
      )
    }

    before {
      grpc.startServer {
        addService(SimpleVetoPlugin(dynamicConfigService))
      }
    }

    context("convergence is enabled") {
      before {
        whenever(dynamicConfigService.isEnabled("keel.converge.enabled", false)) doReturn true
      }

      after {
        reset(dynamicConfigService)
      }

      test("it approves asset convergence") {
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

    context("convergence is disabled") {
      before {
        whenever(dynamicConfigService.isEnabled("keel.converge.enabled", false)) doReturn false
      }

      after {
        reset(dynamicConfigService)
      }

      test("it denies asset convergence") {
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
}
