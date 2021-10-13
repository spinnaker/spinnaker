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

import com.netflix.spinnaker.keel.api.ExcludedFromDiff
import com.netflix.spinnaker.keel.api.schema.Literal
import com.netflix.spinnaker.keel.api.schema.Optional

abstract class SecurityGroupRule {
  abstract val protocol: Protocol
  abstract val portRange: IngressPorts

  enum class Protocol {
    ALL, TCP, UDP, ICMP
  }
}

data class ReferenceRule(
  override val protocol: Protocol,
  @Optional("defaults to the name of the security group the rule belongs to")
  val name: String,
  override val portRange: IngressPorts
) : SecurityGroupRule()

data class CrossAccountReferenceRule(
  override val protocol: Protocol,
  val name: String,
  val account: String,
  val vpc: String,
  override val portRange: IngressPorts
) : SecurityGroupRule()

data class CidrRule(
  override val protocol: Protocol,
  override val portRange: IngressPorts,
  val blockRange: String,
  @get:ExcludedFromDiff
  val description: String? = null
) : SecurityGroupRule() {
  // DO NOT REMOVE! Required due to https://github.com/SQiShER/java-object-diff/issues/216
  override fun equals(other: Any?): Boolean {
    if (this === other) return true

    return other is CidrRule
      && other.protocol == this.protocol
      && other.portRange == this.portRange
      && other.blockRange == this.blockRange
  }

  // DO NOT REMOVE! Required due to https://github.com/SQiShER/java-object-diff/issues/216
  override fun hashCode(): Int {
    var result = protocol.hashCode()
    result = 31 * result + portRange.hashCode()
    result = 31 * result + blockRange.hashCode()
    return result
  }
}

data class PrefixListRule(
  override val protocol: Protocol,
  override val portRange: IngressPorts,
  val prefixListId: String,
  @get:ExcludedFromDiff
  val description: String? = null
) : SecurityGroupRule()

sealed class IngressPorts

@Literal(value = "ALL")
object AllPorts : IngressPorts()

data class PortRange(
  val startPort: Int,
  val endPort: Int
) : IngressPorts()
