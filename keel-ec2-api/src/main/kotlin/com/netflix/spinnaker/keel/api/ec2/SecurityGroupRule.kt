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

import com.netflix.spinnaker.keel.api.schema.Literal

sealed class SecurityGroupRule {
  abstract val protocol: Protocol
  abstract val portRange: IngressPorts

  enum class Protocol {
    ALL, TCP, UDP, ICMP
  }

  open val isSelfReference: Boolean = false
}

data class ReferenceRule(
  override val protocol: Protocol,
  val name: String? = null,
  override val portRange: IngressPorts
) : SecurityGroupRule() {
  override val isSelfReference: Boolean
    get() = name == null
}

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
  val blockRange: String
) : SecurityGroupRule()

sealed class IngressPorts

@Literal(value = "ALL")
object AllPorts : IngressPorts()

data class PortRange(
  val startPort: Int,
  val endPort: Int
) : IngressPorts()
