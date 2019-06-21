package com.netflix.spinnaker.keel.api

import com.netflix.spinnaker.keel.plugin.Halt
import com.netflix.spinnaker.keel.plugin.Proceed
import com.netflix.spinnaker.keel.plugin.VetoPlugin
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService
import dev.minutest.junit.JUnit5Minutests
import dev.minutest.rootContext
import io.mockk.every
import io.mockk.mockk
import strikt.api.expectThat
import strikt.assertions.isA
import strikt.assertions.isEqualTo
import java.util.UUID

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
        dynamicConfigService = mockk(),
        request = Resource(
          apiVersion = SPINNAKER_API_V1,
          kind = "ec2.SecurityGroup",
          metadata = mapOf(
            "name" to "ec2.SecurityGroup:keel:prod:us-east-1:keel",
            "uid" to randomUID()
          ),
          spec = randomData()
        )
      )
    }

    context("convergence is enabled") {
      before {
        every { dynamicConfigService.isEnabled("keel.converge.enabled", false) } returns true
      }

      test("it approves resource convergence") {
        expectThat(subject.allow(request)).isEqualTo(Proceed)
      }
    }

    context("convergence is disabled") {
      before {
        every { dynamicConfigService.isEnabled("keel.converge.enabled", false) } returns false
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
