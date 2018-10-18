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
import strikt.assertions.all
import strikt.assertions.any
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.isTrue
import com.netflix.spinnaker.keel.ec2.SecurityGroup as SecurityGroupProto

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
          get { hasSelfReferencingRule() }.isTrue()
          get { selfReferencingRule.protocol }.isEqualTo(riverModel.inboundRules.first().protocol)
          get { selfReferencingRule.portRange.startPort }.isEqualTo(6565)
          get { selfReferencingRule.portRange.endPort }.isEqualTo(6565)
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
        expectThat(proto.inboundRuleList)
          .hasSize(2)
          .all {
            get { hasReferenceRule() }.isTrue()
            get { referenceRule.protocol }.isEqualTo(riverModel.inboundRules.first().protocol)
            get { referenceRule.name }.isEqualTo(riverModel.inboundRules.first().securityGroup?.name)
          }
          .any {
            get { referenceRule.portRange.startPort }.isEqualTo(7001)
            get { referenceRule.portRange.endPort }.isEqualTo(7001)
          }
          .any {
            get { referenceRule.portRange.startPort }.isEqualTo(7002)
            get { referenceRule.portRange.endPort }.isEqualTo(7002)
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
          get { application }.isEqualTo(riverModel.moniker.app)
          get { name }.isEqualTo(riverModel.name)
          get { accountName }.isEqualTo(riverModel.accountName)
          get { region }.isEqualTo(riverModel.region)
          get { vpcName }.isEqualTo(vpc.name)
          get { description }.isEqualTo(riverModel.description)
        }
      }

      it("maps inbound rules") {
        expectThat(proto.inboundRuleList.first()) {
          get { cidrRule.blockRange }.isEqualTo("104.24.115.229/24")
          get { cidrRule.protocol }.isEqualTo("-1")
          get { cidrRule.portRange.startPort }.isEqualTo(-1)
          get { cidrRule.portRange.endPort }.isEqualTo(-1)
        }
      }
    }
  }
})
