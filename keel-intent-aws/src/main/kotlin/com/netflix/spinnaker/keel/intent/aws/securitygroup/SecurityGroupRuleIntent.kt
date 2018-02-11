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

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonSubTypes.Type
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.fasterxml.jackson.annotation.JsonTypeName
import com.github.jonpeterson.jackson.module.versioning.JsonVersionedModel
import com.netflix.spinnaker.keel.ApplicationAwareIntentSpec
import com.netflix.spinnaker.keel.Intent
import com.netflix.spinnaker.keel.intent.SCHEMA_PROPERTY
import com.netflix.spinnaker.keel.intent.securitygroup.SecurityGroupRule
import com.netflix.spinnaker.keel.intent.securitygroup.SelfReferencingSecurityGroupRule

private const val KIND = "SecurityGroupRule"
private const val CURRENT_SCHEMA = "0"

@JsonTypeName(KIND)
@JsonVersionedModel(currentVersion = CURRENT_SCHEMA, propertyName = SCHEMA_PROPERTY)
class SecurityGroupRuleIntent
@JsonCreator constructor(spec: SecurityGroupRuleSpec) : Intent<SecurityGroupRuleSpec>(
  kind = KIND,
  schema = CURRENT_SCHEMA,
  spec = spec
) {
  @JsonIgnore override val defaultId = spec.intentId()
}

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "kind")
@JsonSubTypes(
  Type(SelfReferencingSecurityGroupRuleSpec::class),
  Type(RemoteSecurityGroupRule::class)
)
abstract class SecurityGroupRuleSpec : ApplicationAwareIntentSpec {
  abstract val name: String
  abstract val label: String
  abstract val accountName: String
  abstract val region: String
  abstract val vpcName: String
  abstract val description: String
  abstract val inboundRules: Set<SecurityGroupRule>

  abstract fun intentId(): String
}

@JsonTypeName("self")
class SelfReferencingSecurityGroupRuleSpec(
  override val application: String,
  override val name: String,
  override val label: String,
  override val accountName: String,
  override val region: String,
  override val vpcName: String,
  override val description: String,
  override val inboundRules: Set<SelfReferencingSecurityGroupRule>
) : SecurityGroupRuleSpec() {
  override fun intentId() = "$KIND:aws:$accountName:$region:$name:self:$label"
}

@JsonTypeName("remote")
class RemoteSecurityGroupRule(
  override val name: String,
  override val label: String,
  override val accountName: String,
  override val region: String,
  override val vpcName: String,
  override val description: String,
  override val inboundRules: Set<SecurityGroupRule>,
  val sourceApplication: String,
  val targetApplication: String
) : SecurityGroupRuleSpec() {
  @JsonIgnore
  override val application = sourceApplication

  override fun intentId() = "$KIND:aws:$accountName:$region:$name:remote[$sourceApplication:$targetApplication]:$label"
}
