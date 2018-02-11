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
import com.netflix.spinnaker.keel.intent.securitygroup.SecurityGroupPortRange
import com.netflix.spinnaker.keel.intent.securitygroup.SecurityGroupRule
import com.netflix.spinnaker.keel.intent.securitygroup.SelfReferencingSecurityGroupRule
import com.netflix.spinnaker.kork.jackson.ObjectMapperSubtypeConfigurer.ClassSubtypeLocator
import org.junit.Ignore
import org.junit.jupiter.api.Test

object SecurityGroupRuleIntentTest {

  private val mapper = configureObjectMapper(
    ObjectMapper(),
    KeelProperties(),
    listOf(
      ClassSubtypeLocator(SecurityGroupRuleSpec::class.java, listOf("com.netflix.spinnaker.keel.intent")),
      ClassSubtypeLocator(SecurityGroupRule::class.java, listOf("com.netflix.spinnaker.keel.intent"))
    )
  )

  @Test
  fun `can serialize to expected JSON format`() {
    val serialized = mapper.convertValue<Map<String, Any>>(sgRule)
    val deserialized = mapper.readValue<Map<String, Any>>(json)

    serialized shouldMatch equalTo(deserialized)
  }

//  @Test
//  @Ignore("Looks right, but failing...?")
//  fun `can deserialize from expected JSON format`() {
//    mapper.readValue<SecurityGroupRuleIntent>(json).apply {
//      spec shouldEqual sgRule.spec
//    }
//  }

  val sgRule = SecurityGroupRuleIntent(
    SelfReferencingSecurityGroupRuleSpec(
      application = "keel",
      name = "keel",
      label = "testing",
      accountName = "test",
      region = "us-west-2",
      vpcName = "myVpc",
      description = "Very important self-referencing rule",
      inboundRules = hashSetOf(
        SelfReferencingSecurityGroupRule(
          protocol = "tcp",
          portRanges = sortedSetOf(
            SecurityGroupPortRange(1234, 1234)
          )
        )
      )
    )
  )

  val json = """
{
  "kind": "SecurityGroupRule",
  "spec": {
    "kind": "self",
    "application": "keel",
    "name": "keel",
    "label": "testing",
    "accountName": "test",
    "region": "us-west-2",
    "vpcName": "myVpc",
    "description": "Very important self-referencing rule",
    "inboundRules": [
      {
        "kind": "self",
        "portRanges": [
          {
            "startPort": 1234,
            "endPort": 1234
          }
        ],
        "protocol": "tcp"
      }
    ]
  },
  "status": "ACTIVE",
  "labels": {},
  "attributes": [],
  "policies": [],
  "id": "SecurityGroupRule:aws:test:us-west-2:keel:self:testing",
  "schema": "0"
}"""

}
