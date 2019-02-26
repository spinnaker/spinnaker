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
package com.netflix.spinnaker.keel.ec2.resource

import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.CidrSecurityGroupRule
import com.netflix.spinnaker.keel.api.ec2.PortRange
import com.netflix.spinnaker.keel.api.ec2.ReferenceSecurityGroupRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.retrofit.isNotFound
import de.danielbechler.diff.node.DiffNode
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import retrofit2.HttpException
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup as RiverSecurityGroup

class SecurityGroupHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService
) : ResourceHandler<SecurityGroup> {

  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "security-group",
    "security-groups"
  ) to SecurityGroup::class.java

  override fun current(resource: Resource<SecurityGroup>): SecurityGroup? =
    cloudDriverService.getSecurityGroup(resource.spec)

  override fun upsert(resource: Resource<SecurityGroup>, diff: DiffNode?) {
    val taskRef = runBlocking {
      resource.spec.let { spec ->
        orcaService
          .orchestrate(OrchestrationRequest(
            "Upsert security group ${spec.name} in ${spec.accountName}/${spec.region}",
            spec.application,
            "Upsert security group ${spec.name} in ${spec.accountName}/${spec.region}",
            listOf(Job(
              "upsertSecurityGroup",
              mapOf(
                "application" to spec.application,
                "credentials" to spec.accountName,
                "cloudProvider" to CLOUD_PROVIDER,
                "name" to spec.name,
                "regions" to listOf(spec.region),
                "vpcId" to cloudDriverCache.networkBy(spec.vpcName, spec.accountName, spec.region).id,
                "description" to spec.description,
                // TODO: would be nice if these two things were more homeomorphic
                "securityGroupIngress" to referenceRuleToJob(spec),
                "ipIngress" to spec.inboundRules.flatMap { cidrRuleToJob(it) },
                "accountName" to spec.accountName
              )
            )),
            OrchestrationTrigger(resource.metadata.name.toString())
          ))
          .await()
      }
    }
    log.info("Started task {} to upsert security group", taskRef.ref)
  }

  override fun delete(resource: Resource<SecurityGroup>) {
    val taskRef = runBlocking {
      resource.spec.let { spec ->
        orcaService
          .orchestrate(OrchestrationRequest(
            "Delete security group ${spec.name} in ${spec.accountName}/${spec.region}",
            spec.application,
            "Delete security group ${spec.name} in ${spec.accountName}/${spec.region}",
            listOf(Job(
              "deleteSecurityGroup",
              mapOf(
                "application" to spec.application,
                "credentials" to spec.accountName,
                "cloudProvider" to CLOUD_PROVIDER,
                "securityGroupName" to spec.name,
                "regions" to listOf(spec.region),
                "vpcId" to spec.vpcName,
                "accountName" to spec.accountName
              )
            )),
            OrchestrationTrigger(resource.metadata.name.toString())
          ))
          .await()
      }
    }
    log.info("Started task {} to upsert security group", taskRef.ref)
  }

  private fun CloudDriverService.getSecurityGroup(spec: SecurityGroup): SecurityGroup? =
    runBlocking {
      try {
        getSecurityGroup(
          spec.accountName,
          CLOUD_PROVIDER,
          spec.name,
          spec.region,
          spec.vpcName?.let { cloudDriverCache.networkBy(it, spec.accountName, spec.region).id }
        )
          .await()
          .let { response ->
            SecurityGroup(
              response.moniker.app,
              response.name,
              response.accountName,
              response.region,
              response.vpcId?.let { cloudDriverCache.networkBy(it).name },
              response.description,
              response.inboundRules.flatMap { rule ->
                val ingressGroup = rule.securityGroup
                val ingressRange = rule.range
                when {
                  ingressGroup != null -> rule.portRanges?.map { portRange ->
                    ReferenceSecurityGroupRule(
                      Protocol.valueOf(rule.protocol!!.toUpperCase()),
                      ingressGroup.name,
                      ingressGroup.accountName,
                      cloudDriverCache.networkBy(ingressGroup.vpcId!!).name,
                      portRange.let {
                        PortRange(it.startPort!!, it.endPort!!)
                      }
                    )
                  } ?: emptyList()
                  ingressRange != null -> rule.portRanges?.map { portRange ->
                    CidrSecurityGroupRule(
                      Protocol.valueOf(rule.protocol!!.toUpperCase()),
                      portRange.let {
                        PortRange(it.startPort!!, it.endPort!!)
                      },
                      ingressRange.ip + ingressRange.cidr
                    )
                  } ?: emptyList()
                  else -> emptyList()
                }
              }.toSet()
            )
          }
      } catch (e: HttpException) {
        if (e.isNotFound) {
          null
        } else {
          throw e
        }
      }
    }

  private fun referenceRuleToJob(spec: SecurityGroup): JobRules {
    return spec
      .inboundRules
      .mapNotNull { rule ->
        when (rule) {
          is ReferenceSecurityGroupRule -> rule to rule.portRange
          else -> null
        }
      }
      .map { (rule, ports) ->
        mutableMapOf<String, Any?>(
          "type" to rule.protocol.name.toLowerCase(),
          "startPort" to ports.startPort,
          "endPort" to ports.endPort,
          "name" to (rule.name ?: spec.name)
        )
          .let { m ->
            if (rule.account != null && rule.vpcName != null) {
              m["accountName"] = rule.account
              m["crossAccountEnabled"] = true
              m["vpcId"] = cloudDriverCache.networkBy(
                rule.vpcName,
                spec.accountName,
                spec.region
              ).id
            }
            m
          }
      }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

private typealias JobRules = List<MutableMap<String, Any?>>

private fun cidrRuleToJob(rule: SecurityGroupRule): JobRules =
  when (rule) {
    is CidrSecurityGroupRule -> rule.portRange.let { ports ->
      listOf(mutableMapOf<String, Any?>(
        "type" to rule.protocol.name,
        "startPort" to ports.startPort,
        "endPort" to ports.endPort,
        "cidr" to rule.blockRange
      ))
    }
    else -> emptyList()
  }
