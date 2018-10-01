package com.netflix.spinnaker.keel.ec2.asset

import com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.given
import org.jetbrains.spek.api.dsl.it
import strikt.api.expectThat
import strikt.assertions.any
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue

internal object EC2TypeConverterSpec : Spek({

  val objectMapper = ObjectMapper()
    .registerModule(KotlinModule())
    .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
  val cloudDriverCache: CloudDriverCache = mock()
  val subject = EC2TypeConverter(cloudDriverCache)

  describe("converting CloudDriver security group JSON to Keel protos") {
    given("a security group with a self-referencing security group rule") {
      val json = javaClass.getResource("/sg-with-self-ref.json")
      val riverModel = objectMapper.readValue<SecurityGroup>(json)

      val vpc = Network("aws", riverModel.vpcId!!, "vpc name", riverModel.accountName, riverModel.region)
      whenever(cloudDriverCache.networkBy(riverModel.vpcId!!)) doReturn vpc

      val proto = subject.toProto(riverModel)

      it("maps inbound rules") {
        expectThat(proto.inboundRuleList.first()) {
          chain { it.hasSelfReferencingRule() }.isTrue()
          chain { it.selfReferencingRule.protocol }.isEqualTo(riverModel.inboundRules.first().protocol)
          chain { it.selfReferencingRule.portRangeList } and {
            hasSize(1)
            first() and {
              chain { it.startPort }.isEqualTo(6565)
              chain { it.endPort }.isEqualTo(6565)
            }
          }
        }
      }
    }

    given("a security group with a security group reference rule") {
      val json = javaClass.getResource("/sg-with-ref.json")
      val riverModel = objectMapper.readValue<SecurityGroup>(json)

      val vpc = Network("aws", riverModel.vpcId!!, "vpc name", riverModel.accountName, riverModel.region)
      whenever(cloudDriverCache.networkBy(riverModel.vpcId!!)) doReturn vpc

      val proto = subject.toProto(riverModel)

      it("maps inbound rules") {
        expectThat(proto.inboundRuleList.first()) {
          chain { it.hasReferenceRule() }.isTrue()
          chain { it.referenceRule.protocol }.isEqualTo(riverModel.inboundRules.first().protocol)
          chain { it.referenceRule.name }.isEqualTo(riverModel.inboundRules.first().securityGroup?.name)
          chain { it.referenceRule.portRangeList } and {
            hasSize(2)
            any {
              chain { it.startPort }.isEqualTo(7001)
              chain { it.endPort }.isEqualTo(7001)
            }
            any {
              chain { it.startPort }.isEqualTo(7002)
              chain { it.endPort }.isEqualTo(7002)
            }
          }
        }
      }
    }

    given("a security group with a CIDR rule") {
      val json = javaClass.getResource("/sg-with-cidr.json")
      val riverModel = objectMapper.readValue<SecurityGroup>(json)

      val vpc = Network("aws", riverModel.vpcId!!, "vpc name", riverModel.accountName, riverModel.region)
      whenever(cloudDriverCache.networkBy(riverModel.vpcId!!)) doReturn vpc

      val proto = subject.toProto(riverModel)

      it("maps the core properties of the security group") {
        expectThat(proto) {
          chain { it.application }.isEqualTo(riverModel.moniker.app)
          chain { it.name }.isEqualTo(riverModel.name)
          chain { it.accountName }.isEqualTo(riverModel.accountName)
          chain { it.region }.isEqualTo(riverModel.region)
          chain { it.vpcName }.isEqualTo(vpc.name)
          chain { it.description }.isEqualTo(riverModel.description)
        }
      }

      it("maps inbound rules") {
        expectThat(proto.inboundRuleList.first()) {
          chain { it.cidrRule.blockRange }.isEqualTo("104.24.115.229/24")
          chain { it.cidrRule.protocol }.isEqualTo("-1")
          chain { it.cidrRule.portRangeList } and {
            hasSize(1)
            first().chain { it.startPort }.isEqualTo(-1)
            first().chain { it.endPort }.isEqualTo(-1)
          }
        }
      }
    }
  }

})
