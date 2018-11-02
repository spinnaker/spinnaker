package com.netflix.spinnaker.keel.api

import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import java.util.UUID.randomUUID

internal class AssetTests {
  @TestFactory
  fun `fingerprints match if names and specs are the same`() =
    listOf(
      randomData()
    )
      .map { spec ->
        asset(spec) to asset(spec)
      }
      .map { (asset1, asset2) ->
        dynamicTest("fingerprints of 2 assets with spec ${asset1.spec} match") {
          expectThat(asset1.fingerprint).isEqualTo(asset2.fingerprint)
        }
      }

  @TestFactory
  fun `fingerprints do not match if spec differs`() =
    listOf(
      randomData() to randomData()
    )
      .map { (spec1, spec2) ->
        asset(spec1) to asset(spec2)
      }
      .map { (asset1, asset2) ->
        dynamicTest("fingerprints of 2 assets with specs ${asset1.spec} and ${asset2.spec} do not match") {
          expectThat(asset1.fingerprint).isNotEqualTo(asset2.fingerprint)
        }
      }

  private fun asset(spec: Map<String, Any>): Asset =
    Asset(
      apiVersion = SPINNAKER_API_V1,
      metadata = AssetMetadata(
        name = AssetName("SecurityGroup:ec2:prod:us-west-2:keel")
      ),
      kind = "SecurityGroup",
      spec = spec
    )
}

fun randomData(length: Int = 4): Map<String, Any> {
  val map = mutableMapOf<String, Any>()
  (0 until length).forEach { _ ->
    map[randomString()] = randomString()
  }
  return map
}

fun randomString(length: Int = 8) =
  randomUUID()
    .toString()
    .map { it.toInt().toString(16) }
    .joinToString("")
    .substring(0 until length)
