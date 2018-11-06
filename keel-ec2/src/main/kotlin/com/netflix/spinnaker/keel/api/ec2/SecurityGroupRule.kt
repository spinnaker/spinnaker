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

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY)
@JsonSubTypes(
  Type(value = ReferenceSecurityGroupRule::class, name = "Reference"),
  Type(value = CidrSecurityGroupRule::class, name = "Cidr")
)
@JsonInclude(NON_NULL)
sealed class SecurityGroupRule {
  abstract val protocol: Protocol
  abstract val portRange: PortRange

  enum class Protocol {
    TCP, UDP, ICMP
  }
}

data class ReferenceSecurityGroupRule(
  override val protocol: Protocol,
  val name: String? = null,
  val account: String? = null,
  val vpcName: String? = null,
  override val portRange: PortRange
) : SecurityGroupRule()

data class CidrSecurityGroupRule(
  override val protocol: Protocol,
  override val portRange: PortRange,
  val blockRange: String
) : SecurityGroupRule()

data class PortRange(
  val startPort: Int,
  val endPort: Int
)
