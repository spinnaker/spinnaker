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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.IntentRepository
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.intent.*
import com.netflix.spinnaker.keel.model.Job
import org.springframework.stereotype.Component

@Component
class AmazonSecurityGroupConverter(
  private val clouddriverCache: CloudDriverCache,
  private val objectMapper: ObjectMapper
) : SpecConverter<AmazonSecurityGroupSpec, SecurityGroup> {

  override fun convertToState(spec: AmazonSecurityGroupSpec): SecurityGroup =
    SecurityGroup(
      type = "aws",
      name = spec.name,
      description = spec.description,
      accountName = spec.accountName,
      region = spec.region,
      vpcId = clouddriverCache.networkBy(spec.vpcName!!, spec.accountName, spec.region).id,
      inboundRules = spec.inboundRules.map {
        when (it) {
          is ReferenceSecurityGroupRule -> SecurityGroup.SecurityGroupRule(
            protocol = it.protocol,
            portRanges = it.portRanges.map { SecurityGroup.SecurityGroupRulePortRange(it.startPort, it.endPort) },
            securityGroup = SecurityGroup.SecurityGroupRuleReference(
              name = it.name,
              accountName = spec.accountName,
              region = spec.region
            )
          )
          is CrossAccountReferenceSecurityGroupRule -> SecurityGroup.SecurityGroupRule(
            protocol = it.protocol,
            portRanges = it.portRanges.map { SecurityGroup.SecurityGroupRulePortRange(it.startPort, it.endPort) },
            securityGroup = SecurityGroup.SecurityGroupRuleReference(
              name = it.name,
              accountName = it.account,
              region = spec.region
            )
          )
          is SelfReferencingSecurityGroupRule -> SecurityGroup.SecurityGroupRule(
            protocol = it.protocol,
            portRanges = it.portRanges.map { SecurityGroup.SecurityGroupRulePortRange(it.startPort, it.endPort) },
            securityGroup = SecurityGroup.SecurityGroupRuleReference(
              name = spec.name,
              accountName = spec.accountName,
              region = spec.region
            )
          )
          else -> TODO(reason = "${it.javaClass.simpleName} has not been implemented yet")
        }
      }.toSet(),
      id = null,
      moniker = Moniker(spec.name)
    )

  override fun convertFromState(state: SecurityGroup): AmazonSecurityGroupSpec? =
    AmazonSecurityGroupRootSpec(
      application = state.moniker.app,
      name = state.name,
      description = state.description!!,
      accountName = state.accountName,
      region = state.region,
      vpcName = clouddriverCache.networkBy(state.vpcId!!).name,
      inboundRules = state.inboundRules.map {
        objectMapper.convertValue(state, SecurityGroupRule::class.java)
      }.toMutableSet(),
      outboundRules = mutableSetOf()
    )

  override fun convertToJob(spec: AmazonSecurityGroupSpec, changeSummary: ChangeSummary): List<Job> {
    changeSummary.addMessage("Converging security group ${spec.name}")
    return listOf(
      Job(
        "upsertSecurityGroup",
        mutableMapOf(
          "application" to spec.application,
          "credentials" to spec.accountName,
          "cloudProvider" to "aws",
          "name" to spec.name,
          "regions" to listOf(spec.region),
          "vpcId" to spec.vpcName,
          "description" to spec.description,
          "securityGroupIngress" to spec.inboundRules.flatMap {
            if (it is PortRangeSupport) {
              changeSummary.addMessage("With ingress rules: $it")
              return@flatMap it.portRanges.map { ports ->
                mutableMapOf<String, Any?>(
                  "type" to it.protocol,
                  "startPort" to ports.startPort,
                  "endPort" to ports.endPort,
                  "name" to if (it is NamedReferenceSupport) it.name else spec.name
                ).let { m ->
                  if (it is CrossAccountReferenceSecurityGroupRule) {
                    changeSummary.addMessage("Adding cross account reference support account ${it.account}")
                    m["accountName"] = it.account
                    m["crossAccountEnabled"] = true
                    m["vpcId"] = clouddriverCache.networkBy(it.vpcName, spec.accountName, spec.region)
                  }
                  m
                }
              }
            }
            throw NotImplementedError("Only 'ref' and 'crossAccountRef' security group rules are implemented at the moment")
          },
          "ipIngress" to listOf<String>(),
          "accountName" to spec.accountName
        )
      )
    )
  }
}
