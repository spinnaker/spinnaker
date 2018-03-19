/*
 * Copyright 2018 Netflix, Inc.
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
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.readValue
import com.natpryce.hamkrest.equalTo
import com.natpryce.hamkrest.should.shouldMatch
import com.netflix.spinnaker.config.KeelProperties
import com.netflix.spinnaker.config.configureObjectMapper
import com.netflix.spinnaker.hamkrest.shouldEqual
import com.netflix.spinnaker.keel.intent.ReferenceSecurityGroupRule
import com.netflix.spinnaker.keel.intent.SecurityGroupPortRange
import com.netflix.spinnaker.keel.intent.SecurityGroupRule
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer.ClassSubtypeLocator
import org.junit.jupiter.api.Test

object AmazonSecurityGroupIntentTest {

  private val mapper = configureObjectMapper(
    ObjectMapper(),
    KeelProperties(),
    listOf(
      ClassSubtypeLocator(AmazonSecurityGroupSpec::class.java, listOf("com.netflix.spinnaker.keel.intent")),
      ClassSubtypeLocator(SecurityGroupRule::class.java, listOf("com.netflix.spinnaker.keel.intent"))
    )
  )

  @Test
  fun `can serialize root intent to expected JSON format`() {
    val serialized = mapper.convertValue<Map<String, Any>>(rootSg)
    val deserialized = mapper.readValue<Map<String, Any>>(rootJson)

    serialized shouldMatch equalTo(deserialized)
  }

  @Test
  fun `can deserialize root intent from expected JSON format`() {
    mapper.readValue<AmazonSecurityGroupIntent>(rootJson).apply {
      spec shouldEqual rootSg.spec
    }
  }

  val rootSg = AmazonSecurityGroupIntent(
    AmazonSecurityGroupRootSpec(
      application = "keel",
      name = "keel",
      accountName = "test",
      region = "us-west-2",
      inboundRules = mutableSetOf(
        ReferenceSecurityGroupRule(
          name = "keel",
          protocol = "tcp",
          portRanges = sortedSetOf(
            SecurityGroupPortRange(6379, 6379),
            SecurityGroupPortRange(7001, 7002)
          )
        )
      ),
      outboundRules = mutableSetOf(),
      vpcName = "myVpc",
      description = "Application security group for keel"
    )
  )

  val rootJson = """
{
  "kind": "SecurityGroup",
  "spec": {
    "kind": "aws",
    "application": "keel",
    "name": "keel",
    "accountName": "test",
    "region": "us-west-2",
    "inboundRules": [
      {
        "kind": "ref",
        "name": "keel",
        "protocol": "tcp",
        "portRanges": [
          {
            "startPort": 6379,
            "endPort": 6379
          },
          {
            "startPort": 7001,
            "endPort": 7002
          }
        ]
      }
    ],
    "outboundRules": [],
    "vpcName": "myVpc",
    "description": "Application security group for keel"
  },
  "status": "ACTIVE",
  "labels": {},
  "attributes": [],
  "policies": [],
  "id": "SecurityGroup:aws:test:us-west-2:keel",
  "schema": "0"
}
"""

  @Test
  fun `can serialize rule intent to expected JSON format`() {
    val serialized = mapper.convertValue<Map<String, Any>>(ruleSg)
    val deserialized = mapper.readValue<Map<String, Any>>(ruleJson)

    serialized shouldMatch equalTo(deserialized)
  }

  @Test
  fun `can deserialize rule intent from expected JSON format`() {
    mapper.readValue<AmazonSecurityGroupIntent>(ruleJson).apply {
      spec shouldEqual ruleSg.spec
    }
  }

  val ruleSg = AmazonSecurityGroupIntent(
    SelfReferencingAmazonSecurityGroupRuleSpec(
      application = "keel",
      name = "keel",
      label = "covfefe",
      accountName = "test",
      region = "us-west-2",
      inboundRules = mutableSetOf(
        ReferenceSecurityGroupRule(
          name = "keel",
          protocol = "tcp",
          portRanges = sortedSetOf(
            SecurityGroupPortRange(6379, 6379),
            SecurityGroupPortRange(7001, 7002)
          )
        )
      ),
      outboundRules = mutableSetOf(),
      vpcName = "myVpc",
      description = "One of them rules"
    )
  )

  val ruleJson = """
{
  "kind": "SecurityGroup",
  "spec": {
    "kind": "aws.self",
    "application": "keel",
    "name": "keel",
    "label": "covfefe",
    "accountName": "test",
    "region": "us-west-2",
    "inboundRules": [
      {
        "kind": "ref",
        "name": "keel",
        "protocol": "tcp",
        "portRanges": [
          {
            "startPort": 6379,
            "endPort": 6379
          },
          {
            "startPort": 7001,
            "endPort": 7002
          }
        ]
      }
    ],
    "outboundRules": [],
    "vpcName": "myVpc",
    "description": "One of them rules"
  },
  "status": "ACTIVE",
  "labels": {},
  "attributes": [],
  "policies": [],
  "id": "SecurityGroup:aws:test:us-west-2:keel:self:covfefe",
  "schema": "0"
}
"""

}
