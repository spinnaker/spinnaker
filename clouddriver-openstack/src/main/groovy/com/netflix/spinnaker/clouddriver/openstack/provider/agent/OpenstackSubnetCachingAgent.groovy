/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSubnet
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.network.Subnet

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SUBNETS
import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES

@Slf4j
class OpenstackSubnetCachingAgent extends AbstractOpenstackCachingAgent {

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(SUBNETS.ns)
  ] as Set)

  final ObjectMapper objectMapper

  OpenstackSubnetCachingAgent(
    final OpenstackNamedAccountCredentials account, final String region, final ObjectMapper objectMapper) {
    super(account, region)

    this.objectMapper = objectMapper
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${OpenstackSubnetCachingAgent.simpleName}"
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")

    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder()

    clientProvider.listSubnets(region)?.each { Subnet subnet ->
      String subnetKey = Keys.getSubnetKey(subnet.id, region, accountName)

      Map<String, Object> subnetAttributes = objectMapper.convertValue(OpenstackSubnet.from(subnet, accountName, region), ATTRIBUTES)

      cacheResultBuilder.namespace(SUBNETS.ns).keep(subnetKey).with {
        attributes = subnetAttributes
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(SUBNETS.ns).keepSize()} items in ${agentType}")

    cacheResultBuilder.build()
  }
}
