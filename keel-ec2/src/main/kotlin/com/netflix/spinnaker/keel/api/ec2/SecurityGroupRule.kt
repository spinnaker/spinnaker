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

sealed class SecurityGroupRule {
  abstract val protocol: String // TODO: enum
  abstract val portRange: PortRange
}

data class ReferenceSecurityGroupRule(
  override val protocol: String,
  val name: String?,
  val account: String?,
  val vpcName: String?,
  override val portRange: PortRange
) : SecurityGroupRule()

data class CrossAccountSecurityGroupRule(
  override val protocol: String,
  val name: String,
  val account: String,
  val vpcName: String,
  override val portRange: PortRange
) : SecurityGroupRule()

data class SelfReferencingSecurityGroupRule(
  override val protocol: String,
  override val portRange: PortRange
) : SecurityGroupRule()

data class CidrSecurityGroupRule(
  override val protocol: String,
  override val portRange: PortRange,
  val blockRange: String
) : SecurityGroupRule()

data class PortRange(
  val startPort: Int,
  val endPort: Int
)
