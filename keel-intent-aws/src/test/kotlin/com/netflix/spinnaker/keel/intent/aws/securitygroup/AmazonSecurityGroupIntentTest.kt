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
import com.netflix.spinnaker.keel.intent.securitygroup.ReferenceSecurityGroupRule
import com.netflix.spinnaker.keel.intent.securitygroup.SecurityGroupIntent
import com.netflix.spinnaker.keel.intent.securitygroup.SecurityGroupPortRange
import com.netflix.spinnaker.keel.intent.securitygroup.SecurityGroupRule
import com.netflix.spinnaker.keel.intent.securitygroup.SecurityGroupSpec
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer.ClassSubtypeLocator
import org.junit.jupiter.api.Test

object AmazonSecurityGroupIntentTest {

  private val mapper = configureObjectMapper(
    ObjectMapper(),
    KeelProperties(),
    listOf(
      ClassSubtypeLocator(SecurityGroupSpec::class.java, listOf("com.netflix.spinnaker.keel.intent")),
      ClassSubtypeLocator(SecurityGroupRule::class.java, listOf("com.netflix.spinnaker.keel.intent"))
    )
  )

  @Test
  fun `can serialize to expected JSON format`() {
    val serialized = mapper.convertValue<Map<String, Any>>(sg)
    val deserialized = mapper.readValue<Map<String, Any>>(json)

    serialized shouldMatch equalTo(deserialized)
  }

  @Test
  fun `can deserialize from expected JSON format`() {
    mapper.readValue<SecurityGroupIntent>(json).apply {
      spec shouldEqual sg.spec
    }
  }

  val sg = SecurityGroupIntent(
    AmazonSecurityGroupSpec(
      application = "keel",
      name = "keel",
      cloudProvider = "aws",
      accountName = "test",
      region = "us-west-2",
      inboundRules = setOf(
        ReferenceSecurityGroupRule(
          name = "keel",
          protocol = "tcp",
          portRanges = sortedSetOf(
            SecurityGroupPortRange(6379, 6379),
            SecurityGroupPortRange(7001, 7002)
          )
        )
      ),
      outboundRules = setOf(),
      vpcName = "myVpc",
      description = "Application security group for keel"
    )
  )

  val json = """
{
  "kind": "SecurityGroup",
  "spec": {
    "kind": "aws",
    "application": "keel",
    "name": "keel",
    "cloudProvider": "aws",
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

}
