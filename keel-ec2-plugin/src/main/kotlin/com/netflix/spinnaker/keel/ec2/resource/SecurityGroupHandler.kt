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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.ResourceId
import com.netflix.spinnaker.keel.api.ResourceKind
import com.netflix.spinnaker.keel.api.SPINNAKER_API_V1
import com.netflix.spinnaker.keel.api.ec2.CidrRule
import com.netflix.spinnaker.keel.api.ec2.CrossAccountReferenceRule
import com.netflix.spinnaker.keel.api.ec2.PortRange
import com.netflix.spinnaker.keel.api.ec2.ReferenceRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol
import com.netflix.spinnaker.keel.api.ec2.SelfReferenceRule
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.model.OrchestrationRequest
import com.netflix.spinnaker.keel.model.OrchestrationTrigger
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.ResourceNormalizer
import com.netflix.spinnaker.keel.retrofit.isNotFound
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import retrofit2.HttpException

class SecurityGroupHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService,
  private val environmentResolver: EnvironmentResolver,
  override val objectMapper: ObjectMapper,
  override val normalizers: List<ResourceNormalizer<*>>
) : ResourceHandler<SecurityGroup> {
  override val log: Logger by lazy { LoggerFactory.getLogger(javaClass) }

  override val apiVersion = SPINNAKER_API_V1.subApi("ec2")
  override val supportedKind = ResourceKind(
    apiVersion.group,
    "security-group",
    "security-groups"
  ) to SecurityGroup::class.java

  override suspend fun current(resource: Resource<SecurityGroup>): SecurityGroup? =
    cloudDriverService.getSecurityGroup(resource.spec, resource.serviceAccount)

  override suspend fun create(
    resource: Resource<SecurityGroup>,
    resourceDiff: ResourceDiff<SecurityGroup>
  ): List<Task> {

    val notifications = environmentResolver.getNotificationsFor(resource.id)

    val description: String
    val taskRef = resource.spec.let { spec ->
      description = "Create security group ${spec.moniker.name} in ${spec.accountName}/${spec.region}"
      orcaService
        .orchestrate(
          resource.serviceAccount,
          OrchestrationRequest(
            description,
            spec.moniker.app,
            description,
            listOf(spec.toCreateJob()),
            OrchestrationTrigger(correlationId = resource.id.toString(), notifications = notifications)
          ))
    }
    log.info("Started task {} to create security group", taskRef.ref)
    return listOf(Task(id = taskRef.taskId, name = description))
  }

  override suspend fun update(
    resource: Resource<SecurityGroup>,
    resourceDiff: ResourceDiff<SecurityGroup>
  ): List<Task> {
    val description: String
    val taskRef = resource.spec.let { spec ->
      description = "Update security group ${spec.moniker.name} in ${spec.accountName}/${spec.region}"
      val notifications = environmentResolver.getNotificationsFor(resource.id)
      orcaService
        .orchestrate(
          resource.serviceAccount,
          OrchestrationRequest(
            description,
            spec.moniker.app,
            description,
            listOf(spec.toUpdateJob()),
            OrchestrationTrigger(correlationId = resource.id.toString(), notifications = notifications)
          ))
    }
    log.info("Started task {} to update security group", taskRef.ref)
    return listOf(Task(id = taskRef.taskId, name = description))
  }

  override suspend fun delete(resource: Resource<SecurityGroup>) {
    val notifications = environmentResolver.getNotificationsFor(resource.id)
    val taskRef = resource.spec.let { spec ->
      orcaService
        .orchestrate(
          resource.serviceAccount,
          OrchestrationRequest(
            "Delete security group ${spec.moniker.name} in ${spec.accountName}/${spec.region}",
            spec.moniker.app,
            "Delete security group ${spec.moniker.name} in ${spec.accountName}/${spec.region}",
            listOf(spec.toDeleteJob()),
            OrchestrationTrigger(correlationId = resource.id.toString(), notifications = notifications)
          ))
    }
    log.info("Started task {} to upsert security group", taskRef.ref)
  }

  override suspend fun actuationInProgress(id: ResourceId) =
    orcaService
      .getCorrelatedExecutions(id.value)
      .isNotEmpty()

  private suspend fun CloudDriverService.getSecurityGroup(spec: SecurityGroup, serviceAccount: String): SecurityGroup? =
    try {
      getSecurityGroup(
        serviceAccount,
        spec.accountName,
        CLOUD_PROVIDER,
        spec.moniker.name,
        spec.region,
        spec.vpcName?.let { cloudDriverCache.networkBy(it, spec.accountName, spec.region).id }
      )
        .let { response ->
          SecurityGroup(
            Moniker(app = response.moniker.app, stack = response.moniker.stack, detail = response.moniker.detail),
            response.accountName,
            response.region,
            response.vpcId?.let { cloudDriverCache.networkBy(it).name },
            response.description,
            response.inboundRules.flatMap { rule ->
              val ingressGroup = rule.securityGroup
              val ingressRange = rule.range
              val protocol = Protocol.valueOf(rule.protocol!!.toUpperCase())
              when {
                ingressGroup != null -> rule.portRanges
                  ?.map { PortRange(it.startPort!!, it.endPort!!) }
                  ?.map { portRange ->
                    when {
                      ingressGroup.accountName != response.accountName || ingressGroup.vpcId != response.vpcId -> CrossAccountReferenceRule(
                        protocol,
                        ingressGroup.name,
                        ingressGroup.accountName!!,
                        cloudDriverCache.networkBy(ingressGroup.vpcId!!).name!!,
                        portRange
                      )
                      ingressGroup.name != response.name -> ReferenceRule(
                        protocol,
                        ingressGroup.name,
                        portRange
                      )
                      else -> SelfReferenceRule(
                        protocol,
                        portRange
                      )
                    }
                  } ?: emptyList()
                ingressRange != null -> rule.portRanges
                  ?.map { PortRange(it.startPort!!, it.endPort!!) }
                  ?.map { portRange ->
                    CidrRule(
                      protocol,
                      portRange,
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

  private fun SecurityGroup.toCreateJob(): Job =
    Job(
      "upsertSecurityGroup",
      mapOf(
        "application" to moniker.app,
        "credentials" to accountName,
        "cloudProvider" to CLOUD_PROVIDER,
        "name" to moniker.name,
        "regions" to listOf(region),
        "vpcId" to cloudDriverCache.networkBy(vpcName, accountName, region).id,
        "description" to description,
        "securityGroupIngress" to inboundRules
          // we have to do a 2-phase create for self-referencing ingress rules as the referenced
          // security group must exist prior to the rule being applied. We filter then out here and
          // the subsequent diff will apply the additional group(s).
          .filterNot { it is SelfReferenceRule }
          .mapNotNull {
            it.referenceRuleToJob(this)
          },
        "ipIngress" to inboundRules.mapNotNull {
          it.cidrRuleToJob()
        },
        "accountName" to accountName
      )
    )

  private fun SecurityGroup.toUpdateJob(): Job =
    Job(
      "upsertSecurityGroup",
      mapOf(
        "application" to moniker.app,
        "credentials" to accountName,
        "cloudProvider" to CLOUD_PROVIDER,
        "name" to moniker.name,
        "regions" to listOf(region),
        "vpcId" to cloudDriverCache.networkBy(vpcName, accountName, region).id,
        "description" to description,
        "securityGroupIngress" to inboundRules.mapNotNull {
          it.referenceRuleToJob(this)
        },
        "ipIngress" to inboundRules.mapNotNull {
          it.cidrRuleToJob()
        },
        "accountName" to accountName
      )
    )

  private fun SecurityGroup.toDeleteJob(): Job {
    return Job(
      "deleteSecurityGroup",
      mapOf(
        "application" to moniker.app,
        "credentials" to accountName,
        "cloudProvider" to CLOUD_PROVIDER,
        "securityGroupName" to moniker.name,
        "regions" to listOf(region),
        "vpcId" to vpcName,
        "accountName" to accountName
      )
    )
  }

  private fun SecurityGroupRule.referenceRuleToJob(spec: SecurityGroup): Map<String, Any?>? =
    when (this) {
      is ReferenceRule -> mapOf(
        "type" to protocol.name.toLowerCase(),
        "startPort" to portRange.startPort,
        "endPort" to portRange.endPort,
        "name" to name
      )
      is SelfReferenceRule -> mapOf(
        "type" to protocol.name.toLowerCase(),
        "startPort" to portRange.startPort,
        "endPort" to portRange.endPort,
        "name" to spec.moniker.name
      )
      is CrossAccountReferenceRule -> mapOf(
        "type" to protocol.name.toLowerCase(),
        "startPort" to portRange.startPort,
        "endPort" to portRange.endPort,
        "name" to name,
        "accountName" to account,
        "crossAccountEnabled" to true,
        "vpcId" to cloudDriverCache.networkBy(
          vpcName,
          account,
          spec.region
        ).id
      )
      else -> null
    }

  private fun SecurityGroupRule.cidrRuleToJob(): Map<String, Any?>? =
    when (this) {
      is CidrRule -> portRange.let { ports ->
        mapOf<String, Any?>(
          "type" to protocol.name,
          "startPort" to ports.startPort,
          "endPort" to ports.endPort,
          "cidr" to blockRange
        )
      }
      else -> null
    }
}
