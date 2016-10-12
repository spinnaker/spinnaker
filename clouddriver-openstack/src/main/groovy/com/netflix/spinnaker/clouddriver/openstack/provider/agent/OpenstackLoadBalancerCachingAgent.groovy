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
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancerHealth.PlatformStatus
import com.netflix.spinnaker.clouddriver.openstack.security.OpenstackNamedAccountCredentials
import groovy.util.logging.Slf4j
import org.openstack4j.model.network.Port
import org.openstack4j.model.network.ext.HealthMonitorV2
import org.openstack4j.model.network.ext.LbPoolV2
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2
import org.openstack4j.model.network.ext.LoadBalancerV2StatusTree
import org.openstack4j.model.network.ext.status.LbPoolV2Status
import org.openstack4j.model.network.ext.status.ListenerV2Status
import org.openstack4j.model.network.ext.status.MemberV2Status

import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.cache.OnDemandAgent.OnDemandType.LoadBalancer
import static com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider.ID
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.FLOATING_IPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.INSTANCES
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.NETWORKS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SECURITY_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SUBNETS
import static com.netflix.spinnaker.clouddriver.openstack.provider.OpenstackInfrastructureProvider.ATTRIBUTES

/*
  TODO drmaas - we could cache the load balancer status tree with each load balancer too, which would be used
  in the server group caching agent instead of re-querying openstack for the status trees
 */
@Slf4j
class OpenstackLoadBalancerCachingAgent extends AbstractOpenstackCachingAgent implements OnDemandAgent {

  final ObjectMapper objectMapper
  final OnDemandMetricsSupport metricsSupport

  Collection<AgentDataType> providedDataTypes = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(LOAD_BALANCERS.ns),
    INFORMATIVE.forType(INSTANCES.ns)
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
    Future<Map<String, ? extends LoadBalancerV2StatusTree>> statusTrees = loadBalancers.thenApplyAsync { lbs ->
      lbs.collectEntries { lb ->
        [(lb.id): clientProvider.getLoadBalancerStatusTree(region, lb.id)]
      }
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
    Future<Map<String, ? extends Port>> ports = CompletableFuture.supplyAsync {
      clientProvider.listPorts(region)?.collectEntries { [it.id, it] }
    }

    CompletableFuture.allOf(loadBalancers, listeners, pools, healthMonitors, statusTrees, ports).join()

    List<String> loadBalancerKeys = loadBalancers.get().collect {
      Keys.getLoadBalancerKey(it.name, it.id, accountName, region)
    }

    buildLoadDataCache(providerCache, loadBalancerKeys) { CacheResultBuilder cacheResultBuilder ->
      buildCacheResult(providerCache, loadBalancers.get(), listeners.get(), pools.get(), healthMonitors.get(), statusTrees.get(), ports.get(), cacheResultBuilder)
    }
  }

  CacheResult buildCacheResult(ProviderCache providerCache,
                               Set<LoadBalancerV2> loadBalancers,
                               Set<ListenerV2> listeners,
                               Set<LbPoolV2> pools,
                               Set<HealthMonitorV2> healthMonitors,
                               Map<String, LoadBalancerV2StatusTree> statusTreeMap,
                               Map<String, Port> portMap,
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

        // Populate load balancer healths and find instance ids which are members of the current lb via membership
        Set<OpenstackLoadBalancerHealth> healths = []
        Set<String> instanceKeys = []

        Map<String, String> memberStatusMap = statusTreeMap?.get(loadBalancer.id)?.loadBalancerV2Status?.listenerStatuses?.collectEntries { ListenerV2Status listenerStatus ->
          listenerStatus.lbPoolV2Statuses?.collectEntries { LbPoolV2Status poolStatus ->
            poolStatus.memberStatuses?.collectEntries { MemberV2Status memberStatus ->
              [memberStatus.address, memberStatus.operatingStatus]
            }
          }
        }

        // Read instances from cache and create a map indexed by ipv6 address to compare to load balancer member status
        Collection<String> instanceFilters = providerCache.filterIdentifiers(INSTANCES.ns, Keys.getInstanceKey('*', accountName, region))
        Collection<CacheData> instancesData = providerCache.getAll(INSTANCES.ns, instanceFilters, RelationshipCacheFilter.none())
        Map<String, CacheData> addressCacheDataMap = instancesData.collectEntries { data ->
          [(data.attributes.ipv4): data, (data.attributes.ipv6): data]
        }

        // Find corresponding instance id, save key for caching below, and add new lb health based upon current member status
        memberStatusMap.each { String key, String value ->
          CacheData instanceData = addressCacheDataMap[key] ?: null
          if (instanceData) {
            String instanceId = instanceData.attributes.instanceId
            instanceKeys << Keys.getInstanceKey(instanceId, accountName, region)
            PlatformStatus status = PlatformStatus.valueOf(value)
            healths << new OpenstackLoadBalancerHealth(
              instanceId: instanceId,
              status: status,
              lbHealthSummaries: [new OpenstackLoadBalancerHealth.LBHealthSummary(
                loadBalancerName: loadBalancer.name
                , instanceId: instanceId
                , state: status?.toServiceStatus())])
          }
        }
        openstackLoadBalancer.healths = healths

        //ips cached
        Collection<String> ipFilters = providerCache.filterIdentifiers(FLOATING_IPS.ns, Keys.getFloatingIPKey('*', accountName, region))
        Collection<CacheData> ipsData = providerCache.getAll(FLOATING_IPS.ns, ipFilters, RelationshipCacheFilter.none())
        CacheData ipCacheData = ipsData.find { i -> i.attributes?.fixedIpAddress == loadBalancer.vipAddress }
        String floatingIpKey = ipCacheData?.id

        //subnets cached
        String subnetKey = Keys.getSubnetKey(loadBalancer.vipSubnetId, accountName, region)

        //networks cached
        String networkKey = ipCacheData ? Keys.getNetworkKey(ipCacheData.attributes.networkId.toString(), accountName, region) : null

        //instances cached
        instanceKeys.each { String instanceKey ->
          cacheResultBuilder.namespace(INSTANCES.ns).keep(instanceKey).with {
            relationships[LOAD_BALANCERS.ns].add(loadBalancerKey)
          }
        }

        // security groups cached
        Port vipPort = portMap.get(loadBalancer.vipPortId)
        Set<String> securityGroupKeys = []
        if (vipPort) {
          vipPort.securityGroups?.each {
            securityGroupKeys << Keys.getSecurityGroupKey('*', it, accountName, region)
          }
        }

        cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keep(loadBalancerKey).with {
          attributes = objectMapper.convertValue(openstackLoadBalancer, ATTRIBUTES)
          relationships[FLOATING_IPS.ns] = [floatingIpKey]
          relationships[NETWORKS.ns] = [networkKey]
          relationships[SUBNETS.ns] = [subnetKey]
          relationships[SECURITY_GROUPS.ns] = securityGroupKeys
        }
      }
    }

    log.info("Caching ${cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keepSize()} load balancers in ${agentType}")
    log.info "Caching ${cacheResultBuilder.namespace(INSTANCES.ns).keepSize()} instance relationships in ${agentType}"
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
      Map<String, LoadBalancerV2StatusTree> statusMap = [:]
      Map<String, Port> portMap = [:]
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
        statusMap[loadBalancer.id] = clientProvider.getLoadBalancerStatusTree(region, loadBalancer.id)
        portMap[loadBalancer.vipPortId] = clientProvider.getPort(region, loadBalancer.vipPortId)
      }

      CacheResult cacheResult = metricsSupport.transformData {
        buildCacheResult(providerCache, loadBalancers, listeners, pools, healthMonitors, statusMap, portMap, new CacheResultBuilder(startTime: Long.MAX_VALUE))
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
