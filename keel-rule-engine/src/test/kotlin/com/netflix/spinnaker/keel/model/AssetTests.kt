package com.netflix.spinnaker.keel.model

import com.netflix.spinnaker.keel.ec2.SecurityGroup
import com.netflix.spinnaker.keel.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.grpc.fromProto
import com.netflix.spinnaker.keel.grpc.toProto
import com.netflix.spinnaker.keel.processing.randomBytes
import com.netflix.spinnaker.keel.proto.pack
import org.junit.jupiter.api.DynamicTest.dynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import strikt.api.expect
import strikt.api.expectThat
import strikt.assertions.isEqualTo
import strikt.assertions.isNotEqualTo
import java.util.*

internal class AssetTests {
  @TestFactory
  fun `fingerprints match if specs are the same`() =
    listOf(
      randomBytes()
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
      randomBytes() to randomBytes()
    )
      .map { (bytes1, bytes2) ->
        asset(bytes1) to asset(bytes2)
      }
      .map { (asset1, asset2) ->
        dynamicTest("fingerprints of 2 assets with specs ${asset1.spec.base64} and ${asset2.spec.base64} do not match") {
          expectThat(asset1.fingerprint).isNotEqualTo(asset2.fingerprint)
        }
      }

  @Test
  fun `converting an asset model to a proto keeps the spec`() {
    val spec = SecurityGroup.newBuilder().run {
      application = "keel"
      name = "keel"
      accountName = "mgmttest"
      region = "us-west-2"
      vpcName = "vpc0"
      description = "Keel application security group"
      addInboundRule(
        SecurityGroupRule.newBuilder().apply {
          selfReferencingRuleBuilder.apply {
            protocol = "tcp"
            portRangeBuilder.apply {
              startPort = 6565
              endPort = 6565
            }
          }
        }
      )
      listOf(7001, 7002).forEach { port ->
        addInboundRule(
          SecurityGroupRule.newBuilder().apply {
            referenceRuleBuilder.apply {
              name = "keel-elb"
              protocol = "tcp"
              portRangeBuilder.apply {
                startPort = port
                endPort = port
              }
            }
          }
        )
      }
      build().pack()
    }

    val assetModel = Asset(
      id = AssetId("keel:ec2:SecurityGroup:mgmttest:us-west-2:keel"),
      kind = "ec2.SecurityGroup",
      apiVersion = "1.0",
      spec = spec.fromProto()
    )

    with(assetModel.toProto()) {
      expect {
        that(spec.typeUrl).isEqualTo(spec.typeUrl)
        that(spec.value).isEqualTo(spec.value)
      }
    }
  }

  private fun asset(spec: TypedByteArray): Asset =
    Asset(
      id = AssetId("SecurityGroup:ec2:prod:us-west-2:keel"),
      kind = "SecurityGroup",
      spec = spec
    )

  private val TypedByteArray.base64: String
    get() = Base64.getEncoder().encodeToString(data)
}
