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
package com.netflix.spinnaker.keel.clouddriver.model

import com.netflix.spinnaker.keel.api.Moniker

data class SecurityGroupModel(
  val type: String,
  val id: String?,
  val name: String,
  val description: String?,
  val accountName: String,
  val region: String,
  val vpcId: String?,
  val inboundRules: Set<SecurityGroupRule> = setOf(),
  val moniker: Moniker
) {
  data class SecurityGroupRule(
    val protocol: String?,
    val portRanges: List<SecurityGroupRulePortRange>?,
    val securityGroup: SecurityGroupRuleReference?,
    val range: SecurityGroupRuleCidr?
  )

  data class SecurityGroupRulePortRange(
    val startPort: Int?,
    val endPort: Int?
  )

  data class SecurityGroupRuleReference(
    val name: String,
    val accountName: String?,
    val region: String?,
    val vpcId: String?
  )

  data class SecurityGroupRuleCidr(
    val ip: String,
    val cidr: String
  )
}
