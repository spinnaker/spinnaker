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

import com.fasterxml.jackson.annotation.*
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.intent.*
import java.util.*

private const val KIND = "SecurityGroup"
private const val CURRENT_SCHEMA = "0"

@JsonTypeName(KIND)
@JsonVersionedModel(currentVersion = CURRENT_SCHEMA, propertyName = SCHEMA_PROPERTY)
class AmazonSecurityGroupIntent
@JsonCreator constructor(spec: AmazonSecurityGroupSpec): Intent<AmazonSecurityGroupSpec>(
  kind = KIND,
  schema = CURRENT_SCHEMA,
  spec = spec
) {
  @JsonIgnore override val id = spec.intentId()

  @JsonIgnore fun parentId(): String? {
    if (spec is AmazonSecurityGroupRootSpec) {
      return null
    }
    return "${KIND}:aws:${spec.accountName}:${spec.region}:${spec.name}"
  }
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes(
  Type(AmazonSecurityGroupRootSpec::class),
  Type(SelfReferencingAmazonSecurityGroupRuleSpec::class),
  Type(RemoteAmazonSecurityGroupRuleSpec::class)
)
abstract class AmazonSecurityGroupSpec : SecurityGroupSpec() {
  abstract val vpcName: String?
  abstract val outboundRules: MutableSet<SecurityGroupRule>
  abstract val description: String

  abstract fun intentId(): String
}

@JsonTypeName("aws")
data class AmazonSecurityGroupRootSpec(
  override val application: String,
  override val name: String,
  override val accountName: String,
  override val region: String,
  override val vpcName: String?,
  override val inboundRules: MutableSet<SecurityGroupRule>,
  override val outboundRules: MutableSet<SecurityGroupRule>,
  override val description: String
) : AmazonSecurityGroupSpec() {
  @JsonIgnore override val cloudProvider = "aws"
  @JsonIgnore override fun intentId() = "$KIND:$cloudProvider:$accountName:$region:$name"
}

/**
 * Marker interface for security group rules
 */
interface AmazonSecurityGroupRuleSpec {
  val label: String
}

@JsonTypeName("aws.self")
data class SelfReferencingAmazonSecurityGroupRuleSpec(
  override val application: String,
  override val name: String,
  override val label: String,
  override val accountName: String,
  override val region: String,
  override val vpcName: String?,
  override val inboundRules: MutableSet<SecurityGroupRule>,
  override val outboundRules: MutableSet<SecurityGroupRule>,
  override val description: String
) : AmazonSecurityGroupSpec(), AmazonSecurityGroupRuleSpec {
  @JsonIgnore override val cloudProvider = "aws"
  @JsonIgnore override fun intentId() = "$KIND:aws:$accountName:$region:$name:self:$label"
}

@JsonTypeName("aws.remote")
data class RemoteAmazonSecurityGroupRuleSpec(
  override val name: String,
  override val label: String,
  override val accountName: String,
  override val region: String,
  override val vpcName: String?,
  override val inboundRules: MutableSet<SecurityGroupRule>,
  override val outboundRules: MutableSet<SecurityGroupRule>,
  override val description: String,
  val sourceApplication: String,
  val targetApplication: String
) : AmazonSecurityGroupSpec(), AmazonSecurityGroupRuleSpec {
  @JsonIgnore override val cloudProvider = "aws"
  @JsonIgnore override val application = sourceApplication
  @JsonIgnore override fun intentId() = "$KIND:aws:$accountName:$region:$name:remote[$sourceApplication:$targetApplication]:$label"
}

@JsonTypeName("crossAccountRef")
data class CrossAccountReferenceSecurityGroupRule(
  override val portRanges: SortedSet<SecurityGroupPortRange>,
  override val protocol: String,
  override val name: String,
  val account: String,
  val vpcName: String
) : SecurityGroupRule(), PortRangeSupport, NamedReferenceSupport
