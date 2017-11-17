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
package com.netflix.spinnaker.keel.intents.processors.converters

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.keel.clouddriver.ClouddriverService
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.intents.ANY_MAP_TYPE
import com.netflix.spinnaker.keel.intents.AmazonSecurityGroupSpec
import com.netflix.spinnaker.keel.intents.SecurityGroupRule
import com.netflix.spinnaker.keel.intents.SecurityGroupSpec
import com.netflix.spinnaker.keel.model.Job
import org.springframework.stereotype.Component

@Component
class SecurityGroupConverter(
  private val clouddriverService: ClouddriverService,
  private val objectMapper: ObjectMapper
) : SpecConverter<SecurityGroupSpec, Set<SecurityGroup>> {

  override fun convertToState(spec: SecurityGroupSpec): Set<SecurityGroup> {
    // TODO rz - cache
    val networks = clouddriverService.listNetworks()

    if (spec is AmazonSecurityGroupSpec) {
      return spec.regions.map { region ->
        SecurityGroup(
          type = "aws",
          name = spec.name,
          description = spec.description,
          accountName = spec.accountName,
          region = region,
          vpcId = networkNameToId(networks, "aws", region, spec.vpcName),
          inboundRules = spec.inboundRules.map { objectMapper.convertValue<MutableMap<String, Any>>(it, ANY_MAP_TYPE) },
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

    // TODO rz - cache
    val networks = clouddriverService.listNetworks()

    state.first().let {
      if (it.type == "aws") {
        return AmazonSecurityGroupSpec(
          cloudProvider = "aws",
          application = it.moniker.app,
          name = it.name,
          description = it.description!!,
          accountName = it.accountName,
          regions = state.map { s -> s.region }.toSet(),
          vpcName = networkIdToName(networks, "aws", it.region, it.vpcId),
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

  override fun convertToJob(spec: SecurityGroupSpec): List<Job> {
    if (spec is AmazonSecurityGroupSpec) {
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
            "securityGroupIngress" to spec.inboundRules,
            "ipIngress" to listOf<String>(),
            "accountName" to spec.accountName
          )
        )
      )
    }
    throw NotImplementedError("Only AWS security groups are supported at the moment")
  }
}
