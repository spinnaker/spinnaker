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
import strikt.api.Assertion
import strikt.api.expectThat
import strikt.assertions.all
import strikt.assertions.any
import strikt.assertions.first
import strikt.assertions.hasSize
import strikt.assertions.isEqualTo
import strikt.assertions.map

internal object EC2TypeConverterSpec : Spek({

  val objectMapper = ObjectMapper()
    .registerModule(KotlinModule())
    .configure(FAIL_ON_UNKNOWN_PROPERTIES, false)
  val cloudDriverCache: CloudDriverCache = mock()
  val subject = EC2TypeConverter(cloudDriverCache)

  describe("converting CloudDriver security group JSON to Keel protos") {
    given("a security group with a self-referencing security group rule") {

    }

    given("a security group with a cross-region security group reference rule") {

    }

    given("a security group with a security group reference rule") {

    }

    given("a security group with a CIDR rule") {
      val json = javaClass.getResource("/sg-with-cidr.json")
      val riverModel = objectMapper.readValue<SecurityGroup>(json)

      val vpc = Network("aws", riverModel.vpcId!!, "vpc name", riverModel.accountName, riverModel.region)
      whenever(cloudDriverCache.networkBy(riverModel.vpcId!!)) doReturn vpc

      val proto = subject.toProto(riverModel)

      expectThat(proto) {
        chain { it.application }.isEqualTo(riverModel.moniker.app)
        chain { it.name }.isEqualTo(riverModel.name)
        chain { it.accountName }.isEqualTo(riverModel.accountName)
        chain { it.region }.isEqualTo(riverModel.region)
        chain { it.vpcName }.isEqualTo(vpc.name)
        chain { it.description }.isEqualTo(riverModel.description)
        chain { it.inboundRuleList } and {
          hasSize(riverModel.inboundRules.size)
          any {
            chain { it.cidrRule.blockRange }.isEqualTo("104.24.115.229/24")
          }
          any {
            chain { it.cidrRule.blockRange }.isEqualTo("23.227.38.32/27")
          }
          map { it.cidrRule.protocol }.all {
            isEqualTo("-1")
          }
          map { it.cidrRule.portRangeList }.all {
            hasSize(1)
            first().chain { it.startPort }.isEqualTo(-1)
            first().chain { it.endPort }.isEqualTo(-1)
          }
          all {
            chain { it.cidrRule.portRangeList }.hasSize(1)
          }
        }
      }
    }
  }

})

private fun <T : Iterable<E>, E> Assertion.Builder<T>.second(): Assertion.Builder<E> =
  chain("second element %s") { it.toList()[1] }
