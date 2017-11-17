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
import com.netflix.spinnaker.keel.clouddriver.ClouddriverService
import com.netflix.spinnaker.keel.clouddriver.model.Moniker
import com.netflix.spinnaker.keel.clouddriver.model.SecurityGroup
import com.netflix.spinnaker.keel.findAllSubtypes
import com.netflix.spinnaker.keel.intent.*
import com.netflix.spinnaker.keel.model.Job
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class SecurityGroupConverter(
  private val clouddriverService: ClouddriverService,
  private val objectMapper: ObjectMapper
) : SpecConverter<SecurityGroupSpec, Set<SecurityGroup>> {

  private val log = LoggerFactory.getLogger(javaClass)

  @PostConstruct fun addSecurityGroupJsonSubtypes() {
    objectMapper.registerSubtypes(
      *findAllSubtypes(log, SecurityGroupRule::class.java, "com.netflix.spinnaker.keel.intent")
    )
  }

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
    val networks = clouddriverService.listNetworks()

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
            "securityGroupIngress" to spec.inboundRules.flatMap {
              if (it is PortRangeSupport) {
                return@flatMap it.portRanges.map { ports ->
                  mutableMapOf<String, Any?>(
                    "type" to it.protocol,
                    "startPort" to ports.startPort,
                    "endPort" to ports.endPort,
                    "name" to it.name
                  ).let { m ->
                    if (it is CrossAccountReferenceSecurityGroupRule) {
                      m["accountName"] = it.account
                      m["crossAccountEnabled"] = true
                      m["vpcId"] = networkNameToId(networks, "aws", it.region, it.vpcName)
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
