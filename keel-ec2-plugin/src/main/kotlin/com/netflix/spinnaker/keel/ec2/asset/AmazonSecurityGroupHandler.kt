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
package com.netflix.spinnaker.keel.ec2.asset

import com.netflix.spinnaker.keel.api.Asset
import com.netflix.spinnaker.keel.api.AssetName
import com.netflix.spinnaker.keel.api.ec2.CidrSecurityGroupRule
import com.netflix.spinnaker.keel.api.ec2.ReferenceSecurityGroupRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.ec2.AmazonAssetHandler
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus.NOT_FOUND
import retrofit.RetrofitError
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup as RiverSecurityGroup

class AmazonSecurityGroupHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService
) : AmazonAssetHandler<SecurityGroup> {

  override fun current(spec: SecurityGroup, request: Asset<SecurityGroup>): SecurityGroup? =
    cloudDriverService.getSecurityGroup(spec)

  override fun converge(assetName: AssetName, spec: SecurityGroup) {
    val taskRef = orcaService
      .orchestrate(OrchestrationRequest(
        "Upsert security group ${spec.name} in ${spec.accountName}/${spec.region}",
        spec.application,
        "Upsert security group ${spec.name} in ${spec.accountName}/${spec.region}",
        listOf(Job(
          "upsertSecurityGroup",
          mutableMapOf(
            "application" to spec.application,
            "credentials" to spec.accountName,
            "cloudProvider" to CLOUD_PROVIDER,
            "name" to spec.name,
            "regions" to listOf(spec.region),
            "vpcId" to spec.vpcName,
            "description" to spec.description,
            "ingressAppendOnly" to true,
            // TODO: would be nice if these two things were more homeomorphic
            "securityGroupIngress" to portRangeRuleToJob(spec),
            "ipIngress" to spec.inboundRules.flatMap { convertCidrRuleToJob(it) },
            "accountName" to spec.accountName
          )
        )),
        OrchestrationTrigger(assetName.toString())
      ))
    log.info("Started task {} to upsert security group", taskRef.ref)
  }

  override fun delete(assetName: AssetName, spec: SecurityGroup) {
    val taskRef = orcaService
      .orchestrate(OrchestrationRequest(
        "Delete security group ${spec.name} in ${spec.accountName}/${spec.region}",
        spec.application,
        "Delete security group ${spec.name} in ${spec.accountName}/${spec.region}",
        listOf(Job(
          "deleteSecurityGroup",
          mutableMapOf(
            "application" to spec.application,
            "credentials" to spec.accountName,
            "cloudProvider" to CLOUD_PROVIDER,
            "securityGroupName" to spec.name,
            "regions" to listOf(spec.region),
            "vpcId" to spec.vpcName,
            "accountName" to spec.accountName
          )
        )),
        OrchestrationTrigger(assetName.toString())
      ))
    log.info("Started task {} to upsert security group", taskRef.ref)
  }

  private fun CloudDriverService.getSecurityGroup(spec: SecurityGroup): SecurityGroup? {
    try {
      return getSecurityGroup(
        spec.accountName,
        CLOUD_PROVIDER,
        spec.name,
        spec.region,
        spec.vpcName?.let { cloudDriverCache.networkBy(it, spec.accountName, spec.region).id }
      ).let { response ->
        SecurityGroup(
          response.moniker.app,
          response.name,
          response.accountName,
          response.region,
          response.vpcId?.let { cloudDriverCache.networkBy(it).name },
          response.description
        )
      }
    } catch (e: RetrofitError) {
      if (e.response.status == NOT_FOUND.value()) {
        return null
      }
      throw e
    }
  }

  private fun portRangeRuleToJob(spec: SecurityGroup): JobRules {
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
          "type" to rule.protocol.name,
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
              )
            }
            m
          }
      }
  }

  private val log by lazy { LoggerFactory.getLogger(javaClass) }
}

private typealias JobRules = List<MutableMap<String, Any?>>

private fun convertCidrRuleToJob(rule: SecurityGroupRule): JobRules =
  when(rule) {
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
