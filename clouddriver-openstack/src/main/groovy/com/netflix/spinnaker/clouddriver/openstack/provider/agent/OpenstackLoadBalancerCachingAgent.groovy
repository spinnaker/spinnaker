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
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackFloatingIP
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackPort
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSubnet
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackVip
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.network.ext.HealthMonitor
import org.openstack4j.model.network.ext.LbPool

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.FLOATING_IPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.PORTS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SUBNETS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.VIPS
import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES

@Slf4j
class OpenstackLoadBalancerCachingAgent extends AbstractOpenstackCachingAgent {

  final ObjectMapper objectMapper

  Collection<AgentDataType> providedDataTypes = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(LOAD_BALANCERS.ns)
  ] as Set)

  String agentType = "${accountName}/${region}/${OpenstackLoadBalancerCachingAgent.simpleName}"

  OpenstackLoadBalancerCachingAgent(OpenstackNamedAccountCredentials account,
                                    String region,
                                    final ObjectMapper objectMapper) {
    super(account, region)
    this.objectMapper = objectMapper
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")

    CacheResultBuilder cacheResultBuilder = new CacheResultBuilder(startTime: Long.MAX_VALUE)

    List<LbPool> pools = clientProvider.getAllLoadBalancerPools(region)

    pools.collect { pool ->
      //health monitors get looked up
      Set<HealthMonitor> healthMonitors = pool.healthMonitors?.collect { healthId ->
        clientProvider.getHealthMonitor(region, healthId)
      }?.toSet()

      //vips cached
      Map<String, Object> vipMap = providerCache.get(VIPS.ns, Keys.getVipKey(pool.vipId, accountName, region))?.attributes
      OpenstackVip vip = vipMap ? objectMapper.convertValue(vipMap, OpenstackVip) : null

      //ips cached
      OpenstackFloatingIP ip = null
      if (vip) {
        Collection<String> portFilters = providerCache.filterIdentifiers(PORTS.ns, Keys.getPortKey('*', accountName, region))
        Collection<CacheData> portsData = providerCache.getAll(PORTS.ns, portFilters, RelationshipCacheFilter.none())
        CacheData portCacheData = portsData?.find { p -> p.attributes?.name == "vip-${vip.id}" }
        Map<String, Object> portAttributes = portCacheData?.attributes
        OpenstackPort port = objectMapper.convertValue(portAttributes, OpenstackPort)
        if (port) {
          Collection<String> ipFilters = providerCache.filterIdentifiers(FLOATING_IPS.ns, Keys.getFloatingIPKey('*', accountName, region))
          Collection<CacheData> ipsData = providerCache.getAll(FLOATING_IPS.ns, ipFilters, RelationshipCacheFilter.none())
          CacheData ipCacheData = ipsData.find { i -> i.attributes?.portId == port.id }
          Map<String, Object> ipAttributes = ipCacheData?.attributes
          ip = objectMapper.convertValue(ipAttributes, OpenstackFloatingIP)
        }
      }

      //subnets cached
      Map<String, Object> subnetMap = providerCache.get(SUBNETS.ns, Keys.getSubnetKey(pool.subnetId, accountName, region))?.attributes
      OpenstackSubnet subnet = subnetMap ? objectMapper.convertValue(subnetMap, OpenstackSubnet) : null

      //create load balancer. Server group relationships are not cached here as they are cached in the server group caching agent.
      OpenstackLoadBalancer loadBalancer = OpenstackLoadBalancer.from(pool, vip, subnet, ip, healthMonitors, accountName, region)
      String loadBalancerKey = Keys.getLoadBalancerKey(pool.name, pool.id, accountName, region)
      cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keep(loadBalancerKey).with {
        attributes = objectMapper.convertValue(loadBalancer, ATTRIBUTES)
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keepSize()} load balancers in ${agentType}")

    cacheResultBuilder.build()
  }

}
