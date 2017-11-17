/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.intents.processors.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.keel.clouddriver.ClouddriverService
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.intents.AmazonSecurityGroupSpec
import com.netflix.spinnaker.keel.intents.ReferenceSecurityGroupRule
import com.netflix.spinnaker.keel.intents.SecurityGroupPortRange
import com.netflix.spinnaker.keel.intents.SecurityGroupSpec
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.Test

object SecurityGroupConverterTest {

  val objectMapper = ObjectMapper()
  val clouddriverService = mock<ClouddriverService>()
  val subject = SecurityGroupConverter(clouddriverService, objectMapper)

  @Test
  fun `should convert spec to system state`() {
    whenever(clouddriverService.listNetworks()) doReturn mapOf(
      "aws" to setOf(
        Network("aws", "vpc-1", "vpcName", "test", "us-west-2"),
        Network("aws", "vpc-2", "vpcName", "prod", "us-west-2"),
        Network("aws", "vpc-3", "vpcName", "test", "us-east-1"),
        Network("aws", "vpc-4", "vpcName", "test", "eu-west-1"),
        Network("aws", "vpc-5", "otherName", "test", "us-west-2")
      )
    )

    val spec = AmazonSecurityGroupSpec(
      application = "keel",
      name = "keel",
      cloudProvider = "aws",
      accountName = "test",
      regions = setOf("us-west-2", "us-east-1"),
      inboundRules = emptySet(),
      outboundRules = emptySet(),
      vpcName = "vpcName",
      description = "application sg"
    )

    val result = subject.convertToState(spec)

    result.size shouldMatch equalTo(2)
    result.first().let {
      it.type shouldMatch equalTo("aws")
      it.name shouldMatch equalTo("keel")
      it.description shouldMatch equalTo("application sg")
      it.accountName shouldMatch equalTo("test")
      it.region shouldMatch equalTo("us-west-2")
      it.vpcId shouldMatch equalTo("vpc-1")
      it.inboundRules shouldMatch isEmpty
    }
    result.last().let {
      it.name shouldMatch equalTo("keel")
      it.region shouldMatch equalTo("us-east-1")
    }
  }

  @Test
  fun `should convert system state to spec`() {
    whenever(clouddriverService.listNetworks()) doReturn mapOf(
      "aws" to setOf(
        Network("aws", "vpc-1234", "vpcName", "test", "us-west-2"),
        Network("aws", "vpc-1235", "vpcName", "test", "us-east-1")
      )
    )

    val state = setOf(
      SecurityGroup(
        type = "aws",
        id = "sg-1234",
        name = "keel",
        description = "application sg",
        accountName = "test",
        region = "us-west-2",
        vpcId = "vpc-1234",
        inboundRules = emptyList(),
        moniker = Moniker("keel", "keel")
      ),
      SecurityGroup(
        type = "aws",
        id = "sg-1235",
        name = "keel",
        description = "application sg",
        accountName = "test",
        region = "us-east-1",
        vpcId = "vpc-1235",
        inboundRules = emptyList(),
        moniker = Moniker("keel", "keel")
      )
    )

    val result = subject.convertFromState(state)

    result shouldMatch equalTo<SecurityGroupSpec>(AmazonSecurityGroupSpec(
      application = "keel",
      name = "keel",
      cloudProvider = "aws",
      accountName = "test",
      regions = setOf("us-west-2", "us-east-1"),
      inboundRules = emptySet(),
      outboundRules = emptySet(),
      vpcName = "vpcName",
      description = "application sg"
    ))
  }

  @Test
  fun `should convert spec to orchestration job`() {
    val spec = AmazonSecurityGroupSpec(
      application = "keel",
      name = "keel",
      cloudProvider = "aws",
      accountName = "test",
      regions = setOf("us-west-2", "us-east-1"),
      inboundRules = setOf(
        ReferenceSecurityGroupRule(
          sortedSetOf(SecurityGroupPortRange(80, 80), SecurityGroupPortRange(8080, 8081)),
          "tcp",
          "other-group"
        )
      ),
      outboundRules = emptySet(),
      vpcName = "vpcName",
      description = "app sg"
    )

    val result = subject.convertToJob(spec)

    result.size shouldMatch equalTo(1)
    result[0]["application"] shouldMatch equalTo<Any>("keel")
    result[0]["cloudProvider"] shouldMatch equalTo<Any>("aws")
    result[0]["regions"] shouldMatch equalTo<Any>(setOf("us-west-2", "us-east-1"))
    result[0]["vpcId"] shouldMatch equalTo<Any>("vpcName")
    result[0]["description"] shouldMatch equalTo<Any>("app sg")
    result[0]["securityGroupIngress"] shouldMatch equalTo<Any>(listOf(
      mapOf(
        "name" to "other-group",
        "type" to "tcp",
        "startPort" to 80,
        "endPort" to 80
      ),
      mapOf(
        "name" to "other-group",
        "type" to "tcp",
        "startPort" to 8080,
        "endPort" to 8081
      )
    ))
    result[0]["ipIngress"] shouldMatch equalTo<Any>(emptyList<Any>())
    result[0]["accountName"] shouldMatch equalTo<Any>("test")
  }
}
