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
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackInstanceType
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.compute.Flavor

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCE_TYPES
import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES

@Slf4j
class OpenstackInstanceTypeCachingAgent extends AbstractOpenstackCachingAgent {

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(INSTANCE_TYPES.ns)
  ] as Set)

  final ObjectMapper objectMapper

  OpenstackInstanceTypeCachingAgent(
    final OpenstackNamedAccountCredentials account, final String region, final ObjectMapper objectMapper) {
    super(account, region)
    this.objectMapper = objectMapper
  }

  @Override
  String getAgentType() {
    "${account.name}/${region}/${OpenstackInstanceTypeCachingAgent.simpleName}"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    types
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder()

    clientProvider.listFlavors(region)?.each { Flavor flavor ->
      String instanceTypeKey = Keys.getInstanceTypeKey(flavor.id, accountName, region)
      Map<String, Object> instanceTypeAttributes = objectMapper.convertValue(buildInstanceType(flavor), ATTRIBUTES)

      cacheResultBuilder.namespace(INSTANCE_TYPES.ns).keep(instanceTypeKey).with {
        attributes = instanceTypeAttributes
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(INSTANCE_TYPES.ns).keepSize()} items in ${agentType}")

    cacheResultBuilder.build()
  }

  OpenstackInstanceType buildInstanceType(Flavor flavor) {
    OpenstackInstanceType.builder()
      .account(accountName)
      .region(region)
      .id(flavor.id)
      .name(flavor.name)
      .available(flavor.isPublic())
      .disabled(flavor.isDisabled())
      .disk(flavor.disk)
      .ephemeral(flavor.ephemeral)
      .ram(flavor.ram)
      .swap(flavor.swap)
      .vcpus(flavor.vcpus)
      .build()
  }
}
