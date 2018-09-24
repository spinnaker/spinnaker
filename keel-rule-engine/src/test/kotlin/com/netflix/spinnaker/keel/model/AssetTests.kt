package com.netflix.spinnaker.keel.model

import com.netflix.spinnaker.keel.processing.randomBytes
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.TestFactory
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import java.util.*

internal class AssetTests {
  @TestFactory
  fun `fingerprints match if specs are the same`() =
    listOf(
      randomBytes(),
      ByteArray(1)
    )
      .map { bytes ->
        asset(bytes) to asset(bytes)
      }
      .map { (asset1, asset2) ->
        dynamicTest("fingerprints of 2 assets with spec ${asset1.spec.base64} match") {
          expectThat(asset1.fingerprint).isEqualTo(asset2.fingerprint)
        }
      }

  @TestFactory
  fun `fingerprints do not match if spec differs`() =
    listOf(
      randomBytes() to randomBytes(),
      ByteArray(2) to ByteArray(1)
    )
      .map { (bytes1, bytes2) ->
        asset(bytes1) to asset(bytes2)
      }
      .map { (asset1, asset2) ->
        dynamicTest("fingerprints of 2 assets with specs ${asset1.spec.base64} and ${asset2.spec.base64} do not match") {
          expectThat(asset1.fingerprint).isNotEqualTo(asset2.fingerprint)
        }
      }

  private fun asset(spec: ByteArray): Asset =
    Asset(
      id = AssetId("SecurityGroup:ec2:prod:us-west-2:keel"),
      kind = "SecurityGroup",
      spec = spec
    )

  private val ByteArray.base64: String
    get() = Base64.getEncoder().encodeToString(this)
}
