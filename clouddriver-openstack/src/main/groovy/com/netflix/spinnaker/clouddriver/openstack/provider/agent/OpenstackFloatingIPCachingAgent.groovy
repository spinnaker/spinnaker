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
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.provider.agent

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackFloatingIP
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.compute.FloatingIP

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.FLOATING_IPS
import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES

@Slf4j
class OpenstackFloatingIPCachingAgent extends AbstractOpenstackCachingAgent {

  final ObjectMapper objectMapper

  Collection<AgentDataType> providedDataTypes = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(FLOATING_IPS.ns)
  ] as Set)

  String agentType = "${accountName}/${region}/${OpenstackFloatingIPCachingAgent.simpleName}"

  OpenstackFloatingIPCachingAgent(OpenstackNamedAccountCredentials account, String region, ObjectMapper objectMapper) {
    super(account, region)
    this.objectMapper = objectMapper
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    List<FloatingIP> ips = clientProvider.listFloatingIps(region)
    buildCacheResult(ips)
  }

  private CacheResult buildCacheResult(List<FloatingIP> ips) {
    log.info("Describing items in ${agentType}")

    def cacheResultBuilder = new CacheResultBuilder()

    ips.each { FloatingIP ip ->
      String ipKey = Keys.getFloatingIPKey(ip.id, accountName, region)

      Map<String, Object> ipAttributes = objectMapper.convertValue(OpenstackFloatingIP.from(ip, accountName, region), ATTRIBUTES)

      cacheResultBuilder.namespace(FLOATING_IPS.ns).keep(ipKey).with {
        attributes = ipAttributes
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(FLOATING_IPS.ns).keepSize()} floating ips in ${agentType}")

    cacheResultBuilder.build()
  }

}
