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
import com.netflix.spinnaker.keel.api.AssetContainer
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.ec2.AmazonAssetHandler
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.ec2.SecurityGroupRule.RuleCase.CIDRRULE
import com.netflix.spinnaker.keel.ec2.SecurityGroupRule.RuleCase.CROSSREGIONREFERENCERULE
import com.netflix.spinnaker.keel.ec2.SecurityGroupRule.RuleCase.REFERENCERULE
import com.netflix.spinnaker.keel.ec2.SecurityGroupRule.RuleCase.RULE_NOT_SET
import com.netflix.spinnaker.keel.ec2.SecurityGroupRule.RuleCase.SELFREFERENCINGRULE
import com.netflix.spinnaker.keel.ec2.SecurityGroupRules
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.proto.pack
import com.netflix.spinnaker.keel.proto.unpack
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import retrofit.RetrofitError
import com.netflix.spinnaker.keel.ec2.SecurityGroup as SecurityGroupProto

class AmazonSecurityGroupHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService
) : AmazonAssetHandler<SecurityGroupProto> {

  private val typeConverter = EC2TypeConverter(cloudDriverCache)

  override fun current(spec: SecurityGroupProto, request: AssetContainer): Asset? =
    cloudDriverService.getSecurityGroup(spec)?.let { securityGroup ->
      Asset.newBuilder()
        .let { builder ->
          builder.typeMetadata = request.asset.typeMetadata
          builder.spec = typeConverter.toProto(securityGroup).pack()
          builder.build()
        }
    }

  override fun flattenAssetContainer(assetContainer: AssetContainer): Asset {
    val securityGroupAsset = assetContainer.asset.spec
      .unpack<SecurityGroupProto>()
      .toBuilder().apply {
        assetContainer.partialAssetList
          .map { it.spec.unpack(SecurityGroupRules::class.java) }
          .forEach { rules ->
            inboundRuleOrBuilderList.apply {
              addAllInboundRule(rules.inboundRuleList)
            }
            outboundRuleOrBuilderList.apply {
              addAllOutboundRule(rules.outboundRuleList)
            }
          }
      }.build()

    return Asset.newBuilder()
      .also {
        it.id = assetContainer.asset.id
        it.typeMetadataBuilder.apply {
          kind = "ec2.SecurityGroup"
          apiVersion = "1.0"
        }
        it.spec = securityGroupAsset.pack()
      }
      .build()
  }

  override fun converge(assetId: String, spec: SecurityGroupProto) {
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
            "cloudProvider" to "aws",
            "name" to spec.name,
            "regions" to listOf(spec.region),
            "vpcId" to spec.vpcName,
            "description" to spec.description,
            "securityGroupIngress" to portRangeRuleToJob(spec),
            "ipIngress" to spec.inboundRuleList.filter { it.hasCidrRule() }.flatMap {
              convertCidrRuleToJob(it)
            },
            // TODO rz - egress
            "accountName" to spec.accountName
          )
        )),
        OrchestrationTrigger(assetId)
      ))
    log.info("Started task {} to upsert security group", taskRef.ref)
  }

  private fun CloudDriverService.getSecurityGroup(spec: SecurityGroupProto): SecurityGroup? {
    try {
      return getSecurityGroup(
        spec.accountName,
        CLOUD_PROVIDER,
        spec.name,
        spec.region,
        spec.vpcName?.let { cloudDriverCache.networkBy(it, spec.accountName, spec.region).id }
      )
    } catch (e: RetrofitError) {
      if (e.response.status == HttpStatus.NOT_FOUND.value()) {
        return null
      }
      throw e
    }
  }

  private fun portRangeRuleToJob(spec: SecurityGroupProto): JobRules {
    return spec
      .inboundRuleList
      .filter { it.hasReferenceRule() || it.hasSelfReferencingRule() }
      .flatMap { rule ->
        when {
          rule.hasReferenceRule() -> rule.referenceRule.portRangeList
          rule.hasSelfReferencingRule() -> rule.selfReferencingRule.portRangeList
          else -> emptyList()
        }
          .map { Pair(rule, it) }
      }
      .map { (rule, ports) ->
        mutableMapOf<String, Any?>(
          "type" to rule.protocol,
          "startPort" to ports.startPort,
          "endPort" to ports.endPort,
          "name" to if (rule.hasReferenceRule()) rule.referenceRule.name else spec.name
        )
          .let { m ->
            if (rule.hasCrossRegionReferenceRule()) {
              m["accountName"] = rule.crossRegionReferenceRule.account
              m["crossAccountEnabled"] = true
              m["vpcId"] = cloudDriverCache.networkBy(
                rule.crossRegionReferenceRule.vpcName,
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

typealias JobRules = List<MutableMap<String, Any?>>

private fun convertCidrRuleToJob(rule: SecurityGroupRule): JobRules =
  when {
    rule.hasCidrRule() -> rule.cidrRule.portRangeList.map { ports ->
      mutableMapOf(
        "type" to rule.protocol,
        "startPort" to ports.startPort,
        "endPort" to ports.endPort,
        "cidr" to rule.cidrRule.blockRange
      )
    }
    else -> emptyList()
  }

private val SecurityGroupRule.protocol: String
  get() = when (ruleCase) {
    CIDRRULE -> cidrRule.protocol
    CROSSREGIONREFERENCERULE -> crossRegionReferenceRule.protocol
    SELFREFERENCINGRULE -> selfReferencingRule.protocol
    REFERENCERULE -> referenceRule.protocol
    RULE_NOT_SET -> "unknown"
    else -> "unknown"
  }
