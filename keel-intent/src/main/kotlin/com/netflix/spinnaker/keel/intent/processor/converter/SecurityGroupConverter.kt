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
package com.netflix.spinnaker.keel.intent.processor.converter

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.dryrun.ChangeSummary
import com.netflix.spinnaker.keel.findAllSubtypes
import com.netflix.spinnaker.keel.intent.AmazonSecurityGroupSpec
import com.netflix.spinnaker.keel.intent.CrossAccountReferenceSecurityGroupRule
import com.netflix.spinnaker.keel.intent.PortRangeSupport
import com.netflix.spinnaker.keel.intent.ReferenceSecurityGroupRule
import com.netflix.spinnaker.keel.intent.SecurityGroupRule
import com.netflix.spinnaker.keel.intent.SecurityGroupSpec
import com.netflix.spinnaker.keel.model.Job
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class SecurityGroupConverter(
  private val clouddriverCache: CloudDriverCache,
  private val objectMapper: ObjectMapper
) : SpecConverter<SecurityGroupSpec, Set<SecurityGroup>> {

  private val log = LoggerFactory.getLogger(javaClass)

  @PostConstruct fun addSecurityGroupJsonSubtypes() {
    objectMapper.registerSubtypes(
      *findAllSubtypes(log, SecurityGroupRule::class.java, "com.netflix.spinnaker.keel.intent")
    )
  }

  override fun convertToState(spec: SecurityGroupSpec): Set<SecurityGroup> {
    if (spec is AmazonSecurityGroupSpec) {
      return spec.regions.map { region ->
        SecurityGroup(
          type = "aws",
          name = spec.name,
          description = spec.description,
          accountName = spec.accountName,
          region = region,
          // TODO rz - do we even want to mess with EC2-classic support?
          vpcId = clouddriverCache.networkBy(spec.vpcName!!, spec.accountName, region).id,
          inboundRules = spec.inboundRules.map {
            when (it) {
              is ReferenceSecurityGroupRule -> SecurityGroup.SecurityGroupRule(
                protocol = it.protocol,
                portRanges = it.portRanges.map { SecurityGroup.SecurityGroupRulePortRange(it.startPort, it.endPort) },
                securityGroup = SecurityGroup.SecurityGroupRuleReference(
                  name = it.name,
                  accountName = spec.accountName,
                  region = region
                )
              )
              is CrossAccountReferenceSecurityGroupRule -> SecurityGroup.SecurityGroupRule(
                protocol = it.protocol,
                portRanges = it.portRanges.map { SecurityGroup.SecurityGroupRulePortRange(it.startPort, it.endPort) },
                securityGroup = SecurityGroup.SecurityGroupRuleReference(
                  name = it.name,
                  accountName = it.account,
                  region = it.region
                )
              )
              else -> TODO(reason = "${it.javaClass.simpleName} has not been implemented yet")
            }
          },
          id = null,
          // TODO rz - fix so not bad
          moniker = Moniker(spec.name)
        )
      }.toSet()
    }
    throw NotImplementedError("Only AWS security groups are supported at the moment")
  }

  override fun convertFromState(state: Set<SecurityGroup>): SecurityGroupSpec? {
    if (state.isEmpty()) {
      return null
    }

    state.first().let {
      if (it.type == "aws") {
        return AmazonSecurityGroupSpec(
          cloudProvider = "aws",
          application = it.moniker.app,
          name = it.name,
          description = it.description!!,
          accountName = it.accountName,
          regions = state.map { s -> s.region }.toSet(),
          vpcName = clouddriverCache.networkBy(it.vpcId!!).name,
          inboundRules = it.inboundRules.map {
            // TODO rz - ehhh? Will this work?
            objectMapper.convertValue(it, SecurityGroupRule::class.java)
          }.toSet(),
          outboundRules = setOf()
        )
      }
    }
    throw NotImplementedError("Only AWS security groups are supported at the moment")
  }

  override fun convertToJob(spec: SecurityGroupSpec, changeSummary: ChangeSummary): List<Job> {
    if (spec is AmazonSecurityGroupSpec) {
      changeSummary.addMessage("Converging security group ${spec.name}")
      return listOf(
        Job(
          "upsertSecurityGroup",
          mutableMapOf(
            "application" to spec.application,
            "credentials" to spec.accountName,
            "cloudProvider" to "aws",
            "name" to spec.name,
            "regions" to spec.regions,
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
                    "name" to it.name
                  ).let { m ->
                    if (it is CrossAccountReferenceSecurityGroupRule) {
                      changeSummary.addMessage("Adding cross account reference support account ${it.account}")
                      m["accountName"] = it.account
                      m["crossAccountEnabled"] = true
                      m["vpcId"] = clouddriverCache.networkBy(it.vpcName, spec.accountName, it.region)
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
    throw NotImplementedError("Only AWS security groups are supported at the moment")
  }
}
