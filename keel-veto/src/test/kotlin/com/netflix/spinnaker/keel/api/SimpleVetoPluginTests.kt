package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.plugin.Halt
import com.netflix.spinnaker.keel.plugin.Proceed
import com.netflix.spinnaker.keel.plugin.VetoPlugin
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.reset
import com.nhaarman.mockitokotlin2.stub
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.util.*

internal object SimpleVetoPluginTests : JUnit5Minutests {

  data class Fixture(
    val dynamicConfigService: DynamicConfigService,
    val request: Resource<*>
  ) {
    val subject: VetoPlugin = SimpleVetoPlugin(dynamicConfigService)
  }

  fun tests() = rootContext<Fixture> {
    fixture {
      Fixture(
        dynamicConfigService = mock(),
        request = Resource(
          apiVersion = SPINNAKER_API_V1,
          kind = "ec2.SecurityGroup",
          metadata = ResourceMetadata(
            name = ResourceName("ec2.SecurityGroup:keel:prod:us-east-1:keel"),
            uid = randomUID(),
            resourceVersion = 1234L
          ),
          spec = randomData()
        )
      )
    }

    context("convergence is enabled") {
      before {
        dynamicConfigService.stub {
          on { isEnabled("keel.converge.enabled", false) } doReturn true
        }
      }

      after {
        reset(dynamicConfigService)
      }

      test("it approves resource convergence") {
        expectThat(subject.allow(request)).isEqualTo(Proceed)
      }
    }

    context("convergence is disabled") {
      before {
        dynamicConfigService.stub {
          on { isEnabled("keel.converge.enabled", false) } doReturn false
        }
      }

      after {
        reset(dynamicConfigService)
      }

      test("it denies resource convergence") {
        expectThat(subject.allow(request)).isA<Halt>()
      }
    }
  }
}

fun randomData(length: Int = 4): Map<String, Any> {
  val map = mutableMapOf<String, Any>()
  (0 until length).forEach { _ ->
    map[randomString()] = randomString()
  }
  return map
}

fun randomString(length: Int = 8) =
  UUID.randomUUID()
    .toString()
    .map { it.toInt().toString(16) }
    .joinToString("")
    .substring(0 until length)
