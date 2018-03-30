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
package com.netflix.spinnaker.keel.intent.aws.securitygroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.isEmpty
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.config.KeelProperties
import com.netflix.spinnaker.config.configureObjectMapper
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.Network
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.intent.*
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer
import com.nhaarman.mockito_kotlin.any
import com.nhaarman.mockito_kotlin.doReturn
import com.nhaarman.mockito_kotlin.eq
import com.nhaarman.mockito_kotlin.mock
import com.nhaarman.mockito_kotlin.whenever
import org.junit.jupiter.api.Test

object AmazonSecurityGroupConverterTest {

  private val objectMapper = configureObjectMapper(
    ObjectMapper(),
    KeelProperties(),
    listOf(
      ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(AmazonSecurityGroupSpec::class.java, listOf("com.netflix.spinnaker.keel.intent")),
      ObjectMapperSubtypeConfigurer.ClassSubtypeLocator(SecurityGroupRule::class.java, listOf("com.netflix.spinnaker.keel.intent"))
    )
  )
  val clouddriverCache = mock<CloudDriverCache>()
  val subject = AmazonSecurityGroupConverter(clouddriverCache, objectMapper)

  @Test
  fun `should convert spec to system state`() {
    whenever(clouddriverCache.networkBy(any(), any(), eq("us-west-2"))) doReturn Network(
      cloudProvider = "aws",
      id = "vpc-1",
      name = "vpcName",
      account = "test",
      region = "us-west-2"
    )

    val spec = AmazonSecurityGroupRootSpec(
      application = "keel",
      name = "keel",
      accountName = "test",
      region = "us-west-2",
      inboundRules = mutableSetOf(),
      outboundRules = mutableSetOf(),
      vpcName = "vpcName",
      description = "application sg"
    )

    subject.convertToState(spec).also {
      it.type shouldMatch equalTo("aws")
      it.name shouldMatch equalTo("keel")
      it.description shouldMatch equalTo("application sg")
      it.accountName shouldMatch equalTo("test")
      it.region shouldMatch equalTo("us-west-2")
      it.vpcId shouldMatch equalTo("vpc-1")
      it.inboundRules shouldMatch isEmpty
    }
  }

  @Test
  fun `should convert system state to spec`() {
    whenever(clouddriverCache.networkBy(eq("vpc-1234"))) doReturn Network(
      cloudProvider = "aws",
      id = "vpc-1234",
      name = "vpcName",
      account = "test",
      region = "us-west-2"
    )

    val state = SecurityGroup(
      type = "aws",
      id = "sg-1234",
      name = "keel",
      description = "application sg",
      accountName = "test",
      region = "us-west-2",
      vpcId = "vpc-1234",
      inboundRules = setOf(
        SecurityGroup.SecurityGroupRule(
          protocol = "tcp",
          portRanges = listOf(
            SecurityGroup.SecurityGroupRulePortRange(80, 80)
          ),
          securityGroup = SecurityGroup.SecurityGroupRuleReference(
            name = "keel2",
            accountName = "test2",
            region = "us-west-2"
          ),
          range = null
        ),
        SecurityGroup.SecurityGroupRule(
          protocol = "tcp",
          portRanges = listOf(
            SecurityGroup.SecurityGroupRulePortRange(80, 80)
          ),
          securityGroup = null,
          range = SecurityGroup.SecurityGroupRuleCidr(
            ip = "1.1.1.1",
            cidr = "/24"
          )
        )
      ),
      moniker = Moniker("keel", "keel")
    )

    val result = subject.convertFromState(state)

    result shouldMatch equalTo<SecurityGroupSpec>(AmazonSecurityGroupRootSpec(
      application = "keel",
      name = "keel",
      accountName = "test",
      region = "us-west-2",
      inboundRules = mutableSetOf(
        CrossAccountReferenceSecurityGroupRule(
          protocol = "tcp",
          name = "keel2",
          account = "test2",
          vpcName = "vpcName",
          portRanges = sortedSetOf(
            SecurityGroupPortRange(80, 80)
          )
        ),
        CidrSecurityGroupRule(
          protocol = "tcp",
          portRanges = sortedSetOf(
            SecurityGroupPortRange(80, 80)
          ),
          blockRange = "1.1.1.1/24"
        )
      ),
      outboundRules = mutableSetOf(),
      vpcName = "vpcName",
      description = "application sg"
    ))
  }

  @Test
  fun `should convert spec to orchestration job`() {
    val changeSummary = ChangeSummary("foo")
    val spec = AmazonSecurityGroupRootSpec(
      application = "keel",
      name = "keel",
      accountName = "test",
      region = "us-west-2",
      inboundRules = mutableSetOf(
        ReferenceSecurityGroupRule(
          sortedSetOf(SecurityGroupPortRange(80, 80), SecurityGroupPortRange(8080, 8081)),
          "tcp",
          "other-group"
        )
      ),
      outboundRules = mutableSetOf(),
      vpcName = "vpcName",
      description = "app sg"
    )

    val result = subject.convertToJob(DefaultConvertToJobCommand(spec), changeSummary)

    result.size shouldMatch equalTo(1)
    result[0]["application"] shouldMatch equalTo<Any>("keel")
    result[0]["cloudProvider"] shouldMatch equalTo<Any>("aws")
    result[0]["regions"] shouldMatch equalTo<Any>(listOf("us-west-2"))
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
