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

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.databind.JsonDeserializer.None
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.netflix.spinnaker.keel.ec2.jackson.IngressPortsDeserializer
import com.netflix.spinnaker.keel.ec2.jackson.IngressPortsSerializer
import com.netflix.spinnaker.keel.ec2.jackson.SecurityGroupRuleDeserializer
import io.swagger.v3.oas.annotations.media.Schema

@JsonDeserialize(using = SecurityGroupRuleDeserializer::class)
sealed class SecurityGroupRule {
  abstract val protocol: Protocol
  abstract val portRange: IngressPorts

  enum class Protocol {
    ALL, TCP, UDP, ICMP
  }

  @JsonIgnore
  open val isSelfReference: Boolean = false
}

@JsonDeserialize(using = None::class)
data class ReferenceRule(
  override val protocol: Protocol,
  val name: String? = null,
  override val portRange: IngressPorts
) : SecurityGroupRule() {
  @get:JsonIgnore
  override val isSelfReference: Boolean
    get() = name == null
}

@JsonDeserialize(using = None::class)
data class CrossAccountReferenceRule(
  override val protocol: Protocol,
  val name: String,
  val account: String,
  val vpc: String,
  override val portRange: IngressPorts
) : SecurityGroupRule()

@JsonDeserialize(using = None::class)
data class CidrRule(
  override val protocol: Protocol,
  override val portRange: IngressPorts,
  val blockRange: String
) : SecurityGroupRule()

@JsonSerialize(using = IngressPortsSerializer::class)
@JsonDeserialize(using = IngressPortsDeserializer::class)
sealed class IngressPorts

@Schema(type = "string", allowableValues = ["ALL"])
object AllPorts : IngressPorts()

data class PortRange(
  val startPort: Int,
  val endPort: Int
) : IngressPorts()
