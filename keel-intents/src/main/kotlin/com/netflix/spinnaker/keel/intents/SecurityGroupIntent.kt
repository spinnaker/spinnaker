/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.keel.intents

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.IntentSpec
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import java.util.*

private const val KIND = "SecurityGroup"
private const val CURRENT_SCHEMA = "1"

@JsonTypeName(KIND)
@JsonVersionedModel(currentVersion = CURRENT_SCHEMA, propertyName = SCHEMA_PROPERTY)
class SecurityGroupIntent
@JsonCreator constructor(spec: SecurityGroupSpec) : Intent<SecurityGroupSpec>(
  kind = KIND,
  schema = CURRENT_SCHEMA,
  spec = spec
) {
  override fun getId() = "$KIND:${spec.cloudProvider}:${spec.accountName}:${spec.region}:${spec.name}"
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
abstract class SecurityGroupSpec : IntentSpec {
  abstract val application: String
  abstract val name: String
  abstract val cloudProvider: String
  abstract val accountName: String
  abstract val region: String
  abstract val inboundRules: Set<SecurityGroupRule>
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
abstract class SecurityGroupRule {
  abstract val portRanges: SortedSet<SecurityGroupPortRange>
  abstract val protocol: String
  abstract val securityGroup: SecurityGroupSpec
}

@JsonTypeName("standardRule")
data class StandardSecurityGroupRule(
  override val portRanges: SortedSet<SecurityGroupPortRange>,
  override val protocol: String,
  override val securityGroup: SecurityGroupSpec
) : SecurityGroupRule()

data class SecurityGroupPortRange(
  val startPort: Int,
  val endPort: Int
)

@JsonTypeName("httpRule")
data class HttpSecurityGroupRule(
  override val portRanges: SortedSet<SecurityGroupPortRange>,
  override val protocol: String,
  override val securityGroup: SecurityGroupSpec,
  val paths: List<String>,
  val host: String
) : SecurityGroupRule()

@JsonTypeName("ipRangeRule")
data class IpRangeSecurityGroupRule(
  override val portRanges: SortedSet<SecurityGroupPortRange>,
  override val protocol: String,
  override val securityGroup: SecurityGroupSpec,
  val paths: List<String>,
  val host: String
) : SecurityGroupRule()

@JsonTypeName("amazonSecurityGroup")
data class AmazonSecurityGroupSpec(
  override val application: String,
  override val name: String,
  override val cloudProvider: String,
  override val accountName: String,
  override val region: String,
  override val inboundRules: Set<SecurityGroupRule>,
  val outboundRules: Set<SecurityGroupRule>,
  val vpcId: String?, // TODO rz - Exposing vpc id to the end user would kinda suck
  val description: String,
  val accountId: String?
) : SecurityGroupSpec()
