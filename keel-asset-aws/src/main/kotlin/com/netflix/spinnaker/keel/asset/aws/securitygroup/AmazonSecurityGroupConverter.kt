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
package com.netflix.spinnaker.keel.asset.aws.securitygroup

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.asset.*
import com.netflix.spinnaker.keel.asset.exceptions.IllegalConverterStateException
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.exceptions.DeclarativeException
import com.netflix.spinnaker.keel.model.Job
import org.springframework.stereotype.Component

@Component
class AmazonSecurityGroupConverter(
  private val clouddriverCache: CloudDriverCache,
  private val objectMapper: ObjectMapper
) : SpecConverter<AmazonSecurityGroupSpec, SecurityGroup> {

  override fun convertToState(spec: AmazonSecurityGroupSpec): SecurityGroup =
    SecurityGroup(
      type = "ec2",
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
            ),
            range = null
          )
          is CrossAccountReferenceSecurityGroupRule -> SecurityGroup.SecurityGroupRule(
            protocol = it.protocol,
            portRanges = it.portRanges.map { SecurityGroup.SecurityGroupRulePortRange(it.startPort, it.endPort) },
            securityGroup = SecurityGroup.SecurityGroupRuleReference(
              name = it.name,
              accountName = it.account,
              region = spec.region
            ),
            range = null
          )
          is SelfReferencingSecurityGroupRule -> SecurityGroup.SecurityGroupRule(
            protocol = it.protocol,
            portRanges = it.portRanges.map { SecurityGroup.SecurityGroupRulePortRange(it.startPort, it.endPort) },
            securityGroup = SecurityGroup.SecurityGroupRuleReference(
              name = spec.name,
              accountName = spec.accountName,
              region = spec.region
            ),
            range = null
          )
          is CidrSecurityGroupRule -> SecurityGroup.SecurityGroupRule(
            protocol = it.protocol,
            portRanges = it.portRanges.map { SecurityGroup.SecurityGroupRulePortRange(it.startPort, it.endPort) },
            securityGroup = null,
            range = SecurityGroup.SecurityGroupRuleCidr(
              ip = it.blockRange.split("/")[0],
              cidr = "/${it.blockRange.split("/")[1]}"
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
        when (true) {
          it.securityGroup != null -> convertReferenceRuleFromState(state, it)
          it.range != null -> convertCidrRuleFromState(it)
          else -> throw DeclarativeException("Could not determine how to convert rule state: $it")
        }
      }.toMutableSet(),
      outboundRules = mutableSetOf()
    )

  private fun convertReferenceRuleFromState(state: SecurityGroup, rule: SecurityGroup.SecurityGroupRule): SecurityGroupRule {
    val ref = rule.securityGroup ?: throw IllegalConverterStateException("Reference rule cannot be null")
    val ports = rule.portRanges ?: throw IllegalConverterStateException("Reference rules must have port ranges")
    val protocol = rule.protocol ?: throw IllegalConverterStateException("Reference rules must have a protocol")

    val portRanges = ports.map {
      objectMapper.convertValue(it, SecurityGroupPortRange::class.java)
    }.sorted().toSortedSet()

    if (ref.accountName == null || state.vpcId == null) {
      if (state.name == ref.name) {
        return SelfReferencingSecurityGroupRule(
          portRanges = portRanges,
          protocol = protocol
        )
      }
      return ReferenceSecurityGroupRule(
        name = ref.name,
        portRanges = portRanges,
        protocol = protocol
      )
    }
    return CrossAccountReferenceSecurityGroupRule(
      name = ref.name,
      portRanges = portRanges,
      protocol = protocol,
      account = ref.accountName!!,
      vpcName = clouddriverCache.networkBy(state.vpcId!!).name
        ?: throw DeclarativeException("Could not find VPC by ID ${state.vpcId}")
    )
  }

  private fun convertCidrRuleFromState(state: SecurityGroup.SecurityGroupRule): SecurityGroupRule {
    val range = state.range ?: throw IllegalConverterStateException("CIDR rules must have a range defined")
    val protocol = state.protocol ?: throw IllegalConverterStateException("CIDR rules must have a protocol")
    val ports = state.portRanges ?: throw IllegalConverterStateException("CIDR rules must have port ranges")

    val portRanges = ports.map {
      objectMapper.convertValue(it, SecurityGroupPortRange::class.java)
    }.sorted().toSortedSet()

    return CidrSecurityGroupRule(
      protocol = protocol,
      portRanges = portRanges,
      blockRange = "${range.ip}${range.cidr}"
    )
  }

  override fun <C : ConvertToJobCommand<AmazonSecurityGroupSpec>> convertToJob(command: C, changeSummary: ChangeSummary): List<Job> {
    return command.spec.let { spec ->
      changeSummary.addMessage("Converging security group ${spec.name}")
      listOf(
        Job(
          "upsertSecurityGroup",
          mutableMapOf(
            "application" to spec.application,
            "credentials" to spec.accountName,
            "cloudProvider" to "ec2",
            "name" to spec.name,
            "regions" to listOf(spec.region),
            "vpcId" to spec.vpcName,
            "description" to spec.description,
            "securityGroupIngress" to spec.inboundRules.filter { it !is CidrSecurityGroupRule }.flatMap {
              return@flatMap when (it) {
                is PortRangeSupport -> convertPortRangeRuleToJob(changeSummary, spec, it)
                else -> throw NotImplementedError("${it.javaClass.simpleName} security group rule has not been implemented yet")
              }
            },
            "ipIngress" to spec.inboundRules.filterIsInstance<CidrSecurityGroupRule>().flatMap {
              convertCidrRuleToJob(changeSummary, it)
            },
            "accountName" to spec.accountName
          )
        )
      )
    }
  }

  private fun convertPortRangeRuleToJob(changeSummary: ChangeSummary,
                                        spec: AmazonSecurityGroupSpec,
                                        rule: PortRangeSupport): JobRules {
    changeSummary.addMessage("With ingress rules: $rule")
    return rule.portRanges.map { ports ->
      mutableMapOf<String, Any?>(
        "type" to rule.protocol,
        "startPort" to ports.startPort,
        "endPort" to ports.endPort,
        "name" to if (rule is NamedReferenceSupport) rule.name else spec.name
      ).let { m ->
        if (rule is CrossAccountReferenceSecurityGroupRule) {
          changeSummary.addMessage("Adding cross account reference support account ${rule.account}")
          m["accountName"] = rule.account
          m["crossAccountEnabled"] = true
          m["vpcId"] = clouddriverCache.networkBy(rule.vpcName, spec.accountName, spec.region)
        }
        m
      }
    }
  }

  private fun convertCidrRuleToJob(changeSummary: ChangeSummary,
                                   rule: CidrSecurityGroupRule): JobRules {
    changeSummary.addMessage("With CIDR rule: $rule")
    return rule.portRanges.map { ports ->
      mutableMapOf<String, Any?>(
        "type" to rule.protocol,
        "startPort" to ports.startPort,
        "endPort" to ports.endPort,
        "cidr" to rule.blockRange
      )
    }
  }
}

typealias JobRules = List<MutableMap<String, Any?>>
