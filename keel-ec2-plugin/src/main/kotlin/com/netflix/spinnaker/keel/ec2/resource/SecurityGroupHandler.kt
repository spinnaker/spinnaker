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
import com.netflix.spinnaker.keel.api.Exportable
import com.netflix.spinnaker.keel.api.Resource
import com.netflix.spinnaker.keel.api.SimpleLocations
import com.netflix.spinnaker.keel.api.SimpleRegionSpec
import com.netflix.spinnaker.keel.api.SubmittedResource
import com.netflix.spinnaker.keel.api.ec2.CidrRule
import com.netflix.spinnaker.keel.api.ec2.CrossAccountReferenceRule
import com.netflix.spinnaker.keel.api.ec2.PortRange
import com.netflix.spinnaker.keel.api.ec2.ReferenceRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroup
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupOverride
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupRule.Protocol
import com.netflix.spinnaker.keel.api.ec2.SecurityGroupSpec
import com.netflix.spinnaker.keel.api.ec2.SelfReferenceRule
import com.netflix.spinnaker.keel.api.id
import com.netflix.spinnaker.keel.api.serviceAccount
import com.netflix.spinnaker.keel.clouddriver.CloudDriverCache
import com.netflix.spinnaker.keel.clouddriver.CloudDriverService
import com.netflix.spinnaker.keel.clouddriver.ResourceNotFound
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroupModel
import com.netflix.spinnaker.keel.diff.ResourceDiff
import com.netflix.spinnaker.keel.ec2.CLOUD_PROVIDER
import com.netflix.spinnaker.keel.ec2.SPINNAKER_EC2_API_V1
import com.netflix.spinnaker.keel.events.Task
import com.netflix.spinnaker.keel.model.Job
import com.netflix.spinnaker.keel.model.Moniker
import com.netflix.spinnaker.keel.orca.OrcaService
import com.netflix.spinnaker.keel.plugin.Resolver
import com.netflix.spinnaker.keel.plugin.ResourceHandler
import com.netflix.spinnaker.keel.plugin.SupportedKind
import com.netflix.spinnaker.keel.plugin.TaskLauncher
import com.netflix.spinnaker.keel.retrofit.isNotFound
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import retrofit2.HttpException

class SecurityGroupHandler(
  private val cloudDriverService: CloudDriverService,
  private val cloudDriverCache: CloudDriverCache,
  private val orcaService: OrcaService,
  private val taskLauncher: TaskLauncher,
  objectMapper: ObjectMapper,
  resolvers: List<Resolver<*>>
) : ResourceHandler<SecurityGroupSpec, Map<String, SecurityGroup>>(objectMapper, resolvers) {

  override val supportedKind =
    SupportedKind(SPINNAKER_EC2_API_V1, "security-group", SecurityGroupSpec::class.java)

  override suspend fun toResolvedType(resource: Resource<SecurityGroupSpec>): Map<String, SecurityGroup> =
    with(resource.spec) {
      locations.regions.map { region ->
        region.name to SecurityGroup(
          moniker = Moniker(app = moniker.app, stack = moniker.stack, detail = moniker.detail),
          location = SecurityGroup.Location(
            account = locations.account,
            vpc = locations.vpc ?: error("No vpc supplied or resolved"),
            region = region.name
          ),
          description = overrides[region.name]?.description ?: description,
          inboundRules = overrides[region.name]?.inboundRules ?: inboundRules
        )
      }.toMap()
    }

  override suspend fun current(resource: Resource<SecurityGroupSpec>): Map<String, SecurityGroup> =
    cloudDriverService.getSecurityGroup(resource.spec, resource.serviceAccount)

  override suspend fun upsert(
    resource: Resource<SecurityGroupSpec>,
    resourceDiff: ResourceDiff<Map<String, SecurityGroup>>
  ): List<Task> =
    coroutineScope {
      resourceDiff
        .toIndividualDiffs()
        .filter { diff -> diff.hasChanges() }
        .map { diff ->
          val spec = diff.desired
          val job: Job
          val verb: Pair<String, String>

          when (diff.current) {
            null -> {
              job = spec.toCreateJob()
              verb = Pair("Creating", "create")
            }
            else -> {
              job = spec.toUpdateJob()
              verb = Pair("Updating", "update")
            }
          }

          log.info("${verb.first} security group using task: $job")

          async {
            taskLauncher.submitJobToOrca(
              resource = resource,
              description = "${verb.first} security group ${spec.moniker.name} in ${spec.location.account}/${spec.location.region}",
              correlationId = "${resource.id}:${spec.location.region}",
              job = job
            )
          }
        }
        .map { it.await() }
    }

  override suspend fun export(exportable: Exportable): SubmittedResource<SecurityGroupSpec> {
    val summaries = exportable.regions.associateWith { region ->
      try {
        cloudDriverCache.securityGroupByName(
          account = exportable.account,
          region = region,
          name = exportable.moniker.name
        )
      } catch (e: ResourceNotFound) {
        null
      }
    }
      .filterValues { it != null }

    val securityGroups =
      coroutineScope {
        summaries.map { (region, summary) ->
          async {
            try {
              cloudDriverService.getSecurityGroup(
                exportable.user,
                exportable.account,
                CLOUD_PROVIDER,
                summary!!.name,
                region,
                summary.vpcId
              )
                .toSecurityGroup()
            } catch (e: HttpException) {
              if (e.isNotFound) {
                null
              } else {
                throw e
              }
            }
          }
        }
          .mapNotNull { it.await() }
          .associateBy { it.location.region }
      }

    if (securityGroups.isEmpty()) {
      throw ResourceNotFound("Could not find security group: ${exportable.moniker.name} " +
        "in account: ${exportable.account}")
    }

    val base = securityGroups.values.first()
    val spec = SecurityGroupSpec(
      moniker = base.moniker,
      locations = SimpleLocations(
        account = exportable.account,
        vpc = base.location.vpc,
        regions = securityGroups.keys.map {
          SimpleRegionSpec(it)
        }
          .toSet()
      ),
      description = base.description,
      inboundRules = base.inboundRules,
      overrides = mutableMapOf()
    )

    spec.generateOverrides(securityGroups)

    return SubmittedResource(
      apiVersion = supportedKind.apiVersion,
      kind = supportedKind.kind,
      spec = spec
    )
  }

  private fun ResourceDiff<Map<String, SecurityGroup>>.toIndividualDiffs() =
    desired.map { (region, desire) ->
      ResourceDiff(desire, current?.getOrDefault(region, null))
    }

  private fun SecurityGroupSpec.generateOverrides(
    regionalGroups: Map<String, SecurityGroup>
  ) =
    regionalGroups.forEach { (region, securityGroup) ->
      val inboundDiff =
        ResourceDiff(securityGroup.inboundRules, this.inboundRules)
          .hasChanges()
      val vpcDiff = securityGroup.location.vpc != this.locations.vpc
      val descriptionDiff = securityGroup.description != this.description

      if (inboundDiff || vpcDiff || descriptionDiff) {
        (this.overrides as MutableMap)[region] = SecurityGroupOverride(
          vpc = if (vpcDiff) {
            securityGroup.location.vpc
          } else {
            null
          },
          description = if (descriptionDiff) {
            securityGroup.description
          } else {
            null
          },
          inboundRules = if (inboundDiff) {
            securityGroup.inboundRules
          } else {
            null
          }
        )
      }
    }

  override suspend fun actuationInProgress(resource: Resource<SecurityGroupSpec>): Boolean =
    resource
      .spec
      .locations
      .regions
      .map { it.name }
      .any { region ->
        orcaService
          .getCorrelatedExecutions("${resource.id}:$region")
          .isNotEmpty()
      }

  private suspend fun CloudDriverService.getSecurityGroup(
    spec: SecurityGroupSpec,
    serviceAccount: String
  ): Map<String, SecurityGroup> =
    coroutineScope {
      spec.locations.regions.map { region ->
        async {
          try {
            getSecurityGroup(
              serviceAccount,
              spec.locations.account,
              CLOUD_PROVIDER,
              spec.moniker.name,
              region.name,
              cloudDriverCache.networkBy(spec.locations.vpc, spec.locations.account, region.name).id
            )
              .toSecurityGroup()
          } catch (e: HttpException) {
            if (e.isNotFound) {
              null
            } else {
              throw e
            }
          }
        }
      }
        .mapNotNull { it.await() }
        .associateBy { it.location.region }
    }

  private fun SecurityGroupModel.toSecurityGroup() =
    SecurityGroup(
      moniker = Moniker(app = moniker.app, stack = moniker.stack, detail = moniker.detail),
      location = SecurityGroup.Location(
        account = accountName,
        vpc = vpcId?.let { cloudDriverCache.networkBy(it).name }
          ?: error("Only security groups in a VPC are supported"),
        region = region
      ),
      description = description,
      inboundRules = inboundRules.flatMap { rule ->
        val ingressGroup = rule.securityGroup
        val ingressRange = rule.range
        val protocol = Protocol.valueOf(rule.protocol!!.toUpperCase())
        when {
          ingressGroup != null -> rule.portRanges
            ?.map { PortRange(it.startPort!!, it.endPort!!) }
            ?.map { portRange ->
              when {
                ingressGroup.accountName != accountName || ingressGroup.vpcId != vpcId -> CrossAccountReferenceRule(
                  protocol,
                  ingressGroup.name,
                  ingressGroup.accountName!!,
                  cloudDriverCache.networkBy(ingressGroup.vpcId!!).name!!,
                  portRange
                )
                ingressGroup.name != name -> ReferenceRule(
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
      }
        .toSet()
    )

  private fun SecurityGroup.toCreateJob(): Job =
    Job(
      "upsertSecurityGroup",
      mapOf(
        "application" to moniker.app,
        "credentials" to location.account,
        "cloudProvider" to CLOUD_PROVIDER,
        "name" to moniker.name,
        "regions" to listOf(location.region),
        "vpcId" to cloudDriverCache.networkBy(location.vpc, location.account, location.region).id,
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
        "accountName" to location.account
      )
    )

  private fun SecurityGroup.toUpdateJob(): Job =
    Job(
      "upsertSecurityGroup",
      mapOf(
        "application" to moniker.app,
        "credentials" to location.account,
        "cloudProvider" to CLOUD_PROVIDER,
        "name" to moniker.name,
        "regions" to listOf(location.region),
        "vpcId" to cloudDriverCache.networkBy(location.vpc, location.account, location.region).id,
        "description" to description,
        "securityGroupIngress" to inboundRules.mapNotNull {
          it.referenceRuleToJob(this)
        },
        "ipIngress" to inboundRules.mapNotNull {
          it.cidrRuleToJob()
        },
        "accountName" to location.account
      )
    )

  private fun SecurityGroupRule.referenceRuleToJob(securityGroup: SecurityGroup): Map<String, Any?>? =
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
        "name" to securityGroup.moniker.name
      )
      is CrossAccountReferenceRule -> mapOf(
        "type" to protocol.name.toLowerCase(),
        "startPort" to portRange.startPort,
        "endPort" to portRange.endPort,
        "name" to name,
        "accountName" to account,
        "crossAccountEnabled" to true,
        "vpcId" to cloudDriverCache.networkBy(
          vpc,
          account,
          securityGroup.location.region
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
