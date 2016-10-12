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
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.consul.model.ConsulNode
import com.netflix.spinnaker.clouddriver.consul.provider.ConsulProviderUtils
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackInstance
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.compute.Server

import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES

@Slf4j
class OpenstackInstanceCachingAgent extends AbstractOpenstackCachingAgent {

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(INSTANCES.ns)
  ] as Set)

  final ObjectMapper objectMapper

  OpenstackInstanceCachingAgent(
    final OpenstackNamedAccountCredentials account, final String region, final ObjectMapper objectMapper) {
    super(account, region)
    this.objectMapper = objectMapper
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${OpenstackInstanceCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder()

    clientProvider.getInstances(region)?.each { server ->
      String instanceKey = Keys.getInstanceKey(server.id, accountName, region)

      ConsulNode consulNode = account?.consulConfig?.enabled ?
        ConsulProviderUtils.getHealths(account.consulConfig, server.name) : null

      Map<String, Object> instanceAttributes = objectMapper.convertValue(OpenstackInstance.from(server, consulNode, accountName, region), ATTRIBUTES)

      cacheResultBuilder.namespace(INSTANCES.ns).keep(instanceKey).with {
        attributes = instanceAttributes
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(INSTANCES.ns).keepSize()} items in ${agentType}")

    cacheResultBuilder.build()
  }
}
