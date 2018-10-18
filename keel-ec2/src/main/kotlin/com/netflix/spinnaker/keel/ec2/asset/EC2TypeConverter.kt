/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.keel.ec2.asset

import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.ec2.PortRange
import com.netflix.spinnaker.keel.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.ec2.SecurityGroup as SecurityGroupProto

class EC2TypeConverter(
  private val cloudDriverCache: CloudDriverCache
) {
  fun toProto(securityGroup: SecurityGroup): SecurityGroupProto =
    com.netflix.spinnaker.keel.ec2.SecurityGroup.newBuilder()
      .also { builder ->
        builder.application = securityGroup.moniker.app
        builder.name = securityGroup.name
        builder.accountName = securityGroup.accountName
        builder.region = securityGroup.region
        builder.vpcName = securityGroup.vpcId?.let { cloudDriverCache.networkBy(it).name }
        builder.description = securityGroup.description
        securityGroup.inboundRules
          .flatMap { rule ->
            rule.portRanges?.map { rule to it } ?: listOf(rule to null)
          }
          .forEach { (rule, portRange) ->
          builder.addInboundRule(
            SecurityGroupRule.newBuilder().also { builder ->
              val referencedGroup = rule.securityGroup
              val cidrRange = rule.range
              when {
                referencedGroup != null && (referencedGroup.vpcId == securityGroup.vpcId && referencedGroup.name == securityGroup.name) -> {
                  builder.selfReferencingRuleBuilder.also { ruleBuilder ->
                    ruleBuilder.protocol = rule.protocol
                    ruleBuilder.portRange = portRange?.toProto()
                  }
                }
                referencedGroup != null && (referencedGroup.accountName != securityGroup.accountName || referencedGroup.region != securityGroup.region) -> {
                  builder.crossRegionReferenceRuleBuilder.also { ruleBuilder ->
                    ruleBuilder.protocol = rule.protocol
                    ruleBuilder.account = referencedGroup.accountName
                    ruleBuilder.vpcName = referencedGroup.vpcId?.let { cloudDriverCache.networkBy(it).name }
                    ruleBuilder.name = referencedGroup.name
                    ruleBuilder.portRange = portRange?.toProto()
                  }
                }
                referencedGroup != null -> {
                  builder.referenceRuleBuilder.also { ruleBuilder ->
                    ruleBuilder.protocol = rule.protocol
                    ruleBuilder.name = referencedGroup.name
                    ruleBuilder.portRange = portRange?.toProto()
                  }
                }
                cidrRange != null -> {
                  builder.cidrRuleBuilder.also { ruleBuilder ->
                    ruleBuilder.protocol = rule.protocol
                    ruleBuilder.portRange = portRange?.toProto()
                    ruleBuilder.blockRange = cidrRange.ip + cidrRange.cidr
                  }
                }
              }
            }
              .build()
          )
        }
      }
      .build()


  private fun SecurityGroup.SecurityGroupRulePortRange.toProto() =
    PortRange
      .newBuilder()
      .let { builder ->
        builder.startPort = startPort ?: -1 // TODO: need a better way to represent "all ports"
        builder.endPort = endPort ?: -1
        builder.build()
      }
}
