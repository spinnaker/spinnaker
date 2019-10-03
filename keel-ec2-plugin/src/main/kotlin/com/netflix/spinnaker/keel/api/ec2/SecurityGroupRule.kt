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
package com.netflix.spinnaker.keel.api.ec2

import com.fasterxml.jackson.databind.JsonDeserializer.None
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.netflix.spinnaker.keel.ec2.jackson.SecurityGroupRuleDeserializer

@JsonDeserialize(using = SecurityGroupRuleDeserializer::class)
sealed class SecurityGroupRule {
  abstract val protocol: Protocol
  abstract val portRange: PortRange

  enum class Protocol {
    TCP, UDP, ICMP
  }
}

@JsonDeserialize(using = None::class)
data class SelfReferenceRule(
  override val protocol: Protocol,
  override val portRange: PortRange
) : SecurityGroupRule()

@JsonDeserialize(using = None::class)
data class ReferenceRule(
  override val protocol: Protocol,
  val name: String,
  override val portRange: PortRange
) : SecurityGroupRule() {
  constructor(protocol: Protocol, reference: SecurityGroupSpec, portRange: PortRange) : this(
    protocol = protocol,
    name = reference.moniker.name,
    portRange = portRange
  )
}

@JsonDeserialize(using = None::class)
data class CrossAccountReferenceRule(
  override val protocol: Protocol,
  val name: String,
  val account: String,
  val vpcName: String,
  override val portRange: PortRange
) : SecurityGroupRule() {
  constructor(protocol: Protocol, reference: SecurityGroupSpec, portRange: PortRange) : this(
    protocol = protocol,
    name = reference.moniker.name,
    account = reference.locations.accountName,
    vpcName = reference.vpcName!!,
    portRange = portRange
  )
}

@JsonDeserialize(using = None::class)
data class CidrRule(
  override val protocol: Protocol,
  override val portRange: PortRange,
  val blockRange: String
) : SecurityGroupRule()

data class PortRange(
  val startPort: Int,
  val endPort: Int
)
