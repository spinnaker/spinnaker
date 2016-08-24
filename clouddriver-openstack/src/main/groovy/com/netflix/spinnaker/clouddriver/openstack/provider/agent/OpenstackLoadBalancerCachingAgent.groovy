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
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent.OnDemandResult
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.openstack.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.cache.UnresolvableKeyException
import com.netflix.spinnaker.clouddriver.openstack.deploy.exception.OpenstackProviderException
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.network.ext.HealthMonitorV2
import org.openstack4j.model.network.ext.LbPoolV2
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.clouddriver.cache.OnDemandAgent.OnDemandType.LoadBalancer
import static com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider.ID
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.FLOATING_IPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.NETWORKS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SUBNETS
import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES

@Slf4j
class OpenstackLoadBalancerCachingAgent extends AbstractOpenstackCachingAgent implements OnDemandAgent {

  final ObjectMapper objectMapper
  final OnDemandMetricsSupport metricsSupport

  Collection<AgentDataType> providedDataTypes = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(LOAD_BALANCERS.ns)
  ] as Set)

  String agentType = "${accountName}/${region}/${OpenstackLoadBalancerCachingAgent.simpleName}"
  String onDemandAgentType = "${agentType}-OnDemand"

  OpenstackLoadBalancerCachingAgent(final OpenstackNamedAccountCredentials account,
                                    final String region,
                                    final ObjectMapper objectMapper,
                                    final Registry registry) {
    super(account, region)
    this.objectMapper = objectMapper
    this.metricsSupport = new OnDemandMetricsSupport(
      registry,
      this,
      "${ID}:${LoadBalancer}")
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")

    //Get all data in parallel to cut down on processing time
    Future<Set<? extends LoadBalancerV2>> loadBalancers = CompletableFuture.supplyAsync {
      clientProvider.getLoadBalancers(region)?.toSet()
    }
    Future<Set<? extends ListenerV2>> listeners = CompletableFuture.supplyAsync {
      clientProvider.getListeners(region)?.toSet()
    }
    Future<Set<? extends LbPoolV2>> pools = CompletableFuture.supplyAsync {
      clientProvider.getPools(region)?.toSet()
    }
    Future<Set<? extends HealthMonitorV2>> healthMonitors = CompletableFuture.supplyAsync {
      clientProvider.getHealthMonitors(region)?.toSet()
    }
    CompletableFuture.allOf(loadBalancers, listeners, pools, healthMonitors).join()

    List<String> loadBalancerKeys = loadBalancers.get().collect { Keys.getLoadBalancerKey(it.name, it.id, accountName, region) }

    buildLoadDataCache(providerCache, loadBalancerKeys) { CacheResultBuilder cacheResultBuilder ->
      buildCacheResult(providerCache, loadBalancers.get(), listeners.get(), pools.get(), healthMonitors.get(), cacheResultBuilder)
    }
  }

  CacheResult buildCacheResult(ProviderCache providerCache,
                               Set<LoadBalancerV2> loadBalancers,
                               Set<ListenerV2> listeners,
                               Set<LbPoolV2> pools,
                               Set<HealthMonitorV2> healthMonitors,
                               CacheResultBuilder cacheResultBuilder) {
    loadBalancers?.each { loadBalancer ->
      String loadBalancerKey = Keys.getLoadBalancerKey(loadBalancer.name, loadBalancer.id, accountName, region)
      if (shouldUseOnDemandData(cacheResultBuilder, loadBalancerKey)) {
        moveOnDemandDataToNamespace(objectMapper, typeReference, cacheResultBuilder, loadBalancerKey)
      } else {
        Set<ListenerV2> resultlisteners = [].toSet()
        LbPoolV2 pool = null
        HealthMonitorV2 healthMonitor = null
        if (listeners) {
          resultlisteners = loadBalancer.listeners.collect { lblistener ->
            listeners.find { listener -> listener.id == lblistener.id }
          }
          if (resultlisteners) {
            pool = resultlisteners.collect { listener -> pools.find { p -> p.id == listener.defaultPoolId } }.first()
            if (pool) {
              healthMonitor = healthMonitors.find { hm -> hm.id == pool.healthMonitorId }
            }
          }
        }
        //create load balancer. Server group relationships are not cached here as they are cached in the server group caching agent.
        OpenstackLoadBalancer openstackLoadBalancer = OpenstackLoadBalancer.from(loadBalancer, resultlisteners, pool, healthMonitor, accountName, region)

        //ips cached
        Collection<String> ipFilters = providerCache.filterIdentifiers(FLOATING_IPS.ns, Keys.getFloatingIPKey('*', accountName, region))
        Collection<CacheData> ipsData = providerCache.getAll(FLOATING_IPS.ns, ipFilters, RelationshipCacheFilter.none())
        CacheData ipCacheData = ipsData.find { i -> i.attributes?.fixedIpAddress == loadBalancer.vipAddress }
        String floatingIpKey = ipCacheData?.id

        //subnets cached
        String subnetKey = Keys.getSubnetKey(loadBalancer.vipSubnetId, accountName, region)

        //networks cached
        String networkKey = ipCacheData ? Keys.getNetworkKey(ipCacheData.attributes.networkId.toString(), accountName, region) : null

        cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keep(loadBalancerKey).with {
          attributes = objectMapper.convertValue(openstackLoadBalancer, ATTRIBUTES)
          relationships[FLOATING_IPS.ns] = [floatingIpKey]
          relationships[NETWORKS.ns] = [networkKey]
          relationships[SUBNETS.ns] = [subnetKey]
        }
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keepSize()} load balancers in ${agentType}")
    log.info("Caching ${cacheResultBuilder.onDemand.toKeep.size()} onDemand entries in ${agentType}")
    log.info("Evicting ${cacheResultBuilder.onDemand.toEvict.size()} onDemand entries in ${agentType}")

    cacheResultBuilder.build()
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == LoadBalancer && cloudProvider == ID
  }

  @Override
  OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    OnDemandResult result = null

    if (data.containsKey("loadBalancerName") && data.account == accountName && data.region == region) {
      String loadBalancerName = data.loadBalancerName.toString()

      LoadBalancerV2 loadBalancer = metricsSupport.readData {
        try {
          clientProvider.getLoadBalancerByName(region, loadBalancerName)
        } catch (OpenstackProviderException e) {
        }
      }

      Set<LoadBalancerV2> loadBalancers = [].toSet()
      Set<ListenerV2> listeners = [].toSet()
      Set<LbPoolV2> pools = [].toSet()
      Set<HealthMonitorV2> healthMonitors = [].toSet()
      String loadBalancerKey = Keys.getLoadBalancerKey(loadBalancerName, '*', accountName, region)

      if (loadBalancer) {
        loadBalancers << loadBalancer
        loadBalancer.listeners.each { listenerItem ->
          ListenerV2 listener = clientProvider.getListener(region, listenerItem.id)
          if (listener) {
            LbPoolV2 pool = clientProvider.getPool(region, listener.defaultPoolId)
            if (pool) {
              HealthMonitorV2 healthMonitor = clientProvider.getMonitor(region, pool.healthMonitorId)
              if (healthMonitor) {
                healthMonitors << healthMonitor
              }
              pools << pool
            }
            listeners << listener
          }
        }
        loadBalancerKey = Keys.getLoadBalancerKey(loadBalancerName, loadBalancer.id, accountName, region)
      }

      CacheResult cacheResult = metricsSupport.transformData {
        buildCacheResult(providerCache, loadBalancers, listeners, pools, healthMonitors, new CacheResultBuilder(startTime: Long.MAX_VALUE))
      }

      String namespace = LOAD_BALANCERS.ns
      String resolvedKey = null
      try {
        resolvedKey = resolveKey(providerCache, namespace, loadBalancerKey)
        processOnDemandCache(cacheResult, objectMapper, metricsSupport, providerCache, resolvedKey)
      } catch (UnresolvableKeyException uke) {
        log.info("Load balancer ${loadBalancerName} is not resolvable", uke)
      }

      result = buildOnDemandCache(loadBalancer, onDemandAgentType, cacheResult, namespace, resolvedKey)
    }

    log.info("On demand cache refresh (data: ${data}) succeeded.")

    result
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    getAllOnDemandCacheByRegionAndAccount(providerCache, accountName, region)
  }
}
