/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.openstack.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSecurityGroup
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SECURITY_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES

@Slf4j
class OpenstackSecurityGroupCachingAgent extends AbstractOpenstackCachingAgent {

  final Set<AgentDataType> providedDataTypes = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SECURITY_GROUPS.ns)
  ] as Set)
  final String agentType = "${account.name}/${region}/${OpenstackSecurityGroupCachingAgent.simpleName}"
  final ObjectMapper objectMapper

  OpenstackSecurityGroupCachingAgent(OpenstackNamedAccountCredentials account, String region, ObjectMapper objectMapper) {
    super(account, region)
    this.objectMapper = objectMapper
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder()
    clientProvider.getSecurityGroups(region)?.each { securityGroup ->
      log.debug("Caching security group for account $accountName in region $region: $securityGroup")
      OpenstackSecurityGroup openstackSecurityGroup = OpenstackSecurityGroup.from(securityGroup, accountName, region)
      String instanceKey = Keys.getSecurityGroupKey(securityGroup.name, securityGroup.id, accountName, region)
      cacheResultBuilder.namespace(SECURITY_GROUPS.ns).keep(instanceKey).with {
        attributes = objectMapper.convertValue(openstackSecurityGroup, ATTRIBUTES)
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(SECURITY_GROUPS.ns).keepSize()} items in ${agentType}")
    cacheResultBuilder.build()
  }
}
