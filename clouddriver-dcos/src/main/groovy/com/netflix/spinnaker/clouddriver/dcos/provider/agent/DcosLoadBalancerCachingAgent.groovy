/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.*
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.dcos.DcosClientProvider
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerLbId
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProvider
import com.netflix.spinnaker.clouddriver.dcos.provider.MutableCacheData
import com.netflix.spinnaker.clouddriver.dcos.security.DcosAccountCredentials
import groovy.util.logging.Slf4j
import mesosphere.dcos.client.DCOS
import mesosphere.marathon.client.model.v2.App
import mesosphere.marathon.client.model.v2.GetAppNamespaceResponse

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class DcosLoadBalancerCachingAgent implements CachingAgent, AccountAware, OnDemandAgent {

  private final String accountName
  private final String clusterName
  private final DCOS dcosClient
  private final DcosCloudProvider dcosCloudProvider = new DcosCloudProvider()
  private final ObjectMapper objectMapper
  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
                                                                        AUTHORITATIVE.forType(Keys.Namespace.LOAD_BALANCERS.ns),
                                                                        //INFORMATIVE.forType(Keys.Namespace.INSTANCES.ns)
                                                                      ] as Set)

  DcosLoadBalancerCachingAgent(String accountName,
                               String clusterName,
                               DcosAccountCredentials credentials,
                               DcosClientProvider clientProvider,
                               ObjectMapper objectMapper,
                               Registry registry) {
    this.accountName = accountName
    this.clusterName = clusterName
    this.objectMapper = objectMapper
    this.dcosClient = clientProvider.getDcosClient(credentials, clusterName)
    this.metricsSupport = new OnDemandMetricsSupport(registry,
                                                     this,
                                                     "$dcosCloudProvider.id:$OnDemandAgent.OnDemandType.LoadBalancer")
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  String getProviderName() {
    DcosProvider.name
  }

  @Override
  String getAccountName() {
    return accountName
  }

  @Override
  String getAgentType() {
    "${accountName}/${clusterName}/${DcosLoadBalancerCachingAgent.simpleName}"
  }

  @Override
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("loadBalancerName")) {
      return null
    }

    if (data.account != accountName) {
      return null
    }

    // Region may be (and currently is only) going to be 'global' for DCOS marathon-lb instances created through spinnaker.
    def dcosSpinnakerLbId = DcosSpinnakerLbId.from(data.account.toString(), data.loadBalancerName.toString())

    if (!dcosSpinnakerLbId.present) {
      return null
    }

    def appId = dcosSpinnakerLbId.get()

    App loadBalancer = metricsSupport.readData {
      loadLoadBalancer(appId)
    }

    CacheResult result = metricsSupport.transformData {
      buildCacheResult([loadBalancer], [:], [], Long.MAX_VALUE)
    }

    def jsonResult = objectMapper.writeValueAsString(result.cacheResults)

    if (result.cacheResults.values().flatten().isEmpty()) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(Keys.Namespace.ON_DEMAND.ns, [Keys.getLoadBalancerKey(appId, clusterName)])
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
          Keys.getLoadBalancerKey(appId, clusterName),
          10 * 60, // ttl is 10 minutes
          [
            cacheTime     : System.currentTimeMillis(),
            cacheResults  : jsonResult,
            processedCount: 0,
            processedTime : null
          ],
          [:]
        )

        providerCache.putCacheData(Keys.Namespace.ON_DEMAND.ns, cacheData)
      }
    }

    // Evict this load balancer if it no longer exists.
    Map<String, Collection<String>> evictions = loadBalancer ? [:] : [
      (Keys.Namespace.LOAD_BALANCERS.ns): [
        Keys.getLoadBalancerKey(appId, clusterName)
      ]
    ]

    log.info("On demand cache refresh (data: ${data}) succeeded.")

    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getOnDemandAgentType(),
      cacheResult: result,
      evictions: evictions
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keys = providerCache.getIdentifiers(Keys.Namespace.ON_DEMAND.ns)
    keys = keys.findResults {
      def parse = Keys.parse(it)
      if (parse && parse.account == accountName) {
        return it
      } else {
        return null
      }
    }

    return providerCache.getAll(Keys.Namespace.ON_DEMAND.ns, keys).collect {
      [
        details       : Keys.parse(it.id),
        cacheTime     : it.attributes.cacheTime,
        processedCount: it.attributes.processedCount,
        processedTime : it.attributes.processedTime
      ]
    }
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    OnDemandAgent.OnDemandType.LoadBalancer == type && cloudProvider == dcosCloudProvider.id
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    Long start = System.currentTimeMillis()
    List<App> loadBalancers = loadLoadBalancers()

    List<CacheData> evictFromOnDemand = []
    List<CacheData> keepInOnDemand = []

    providerCache.getAll(Keys.Namespace.ON_DEMAND.ns,
                         loadBalancers.collect {
                           Keys.getLoadBalancerKey(DcosSpinnakerLbId.parse(it.id, accountName).get(), clusterName)
                         }).each {
      // Ensure that we don't overwrite data that was inserted by the `handle` method while we retrieved the
      // replication controllers. Furthermore, cache data that hasn't been processed needs to be updated in the ON_DEMAND
      // cache, so don't evict data without a processedCount > 0.
      if (it.attributes.cacheTime < start && it.attributes.processedCount > 0) {
        evictFromOnDemand << it
      } else {
        keepInOnDemand << it
      }
    }

    def result = buildCacheResult(loadBalancers, keepInOnDemand.collectEntries { CacheData onDemandEntry ->
      [(onDemandEntry.id): onDemandEntry]
    }, evictFromOnDemand*.id, start)

    result.cacheResults[Keys.Namespace.ON_DEMAND.ns].each {
      it.attributes.processedTime = System.currentTimeMillis()
      it.attributes.processedCount = (it.attributes.processedCount ?: 0) + 1
    }

    return result
  }

  private List<App> loadLoadBalancers() {
    // Currently not supporting anything but account global load balancers - no associated region.
    final Optional<GetAppNamespaceResponse> response = dcosClient.maybeApps(accountName)
    if (!response.isPresent()) {
      log.info("The account namespace [${accountName}] does not exist in DC/OS. No load balancers will be cached.")
      return []
    }

    return response.get().apps.findAll {
      it.labels?.containsKey("SPINNAKER_LOAD_BALANCER") && DcosSpinnakerLbId.parse(it.id, accountName).isPresent()
    }
  }

  private App loadLoadBalancer(DcosSpinnakerLbId id) {
    dcosClient.maybeApp(id.toString()).orElse(null)
  }

  private
  static void cache(Map<String, List<CacheData>> cacheResults, String namespace, Map<String, CacheData> cacheDataById) {
    cacheResults[namespace].each {
      def existingCacheData = cacheDataById[it.id]
      if (!existingCacheData) {
        cacheDataById[it.id] = it
      } else {
        existingCacheData.attributes.putAll(it.attributes)
        it.relationships.each { String relationshipName, Collection<String> relationships ->
          existingCacheData.relationships[relationshipName].addAll(relationships)
        }
      }
    }
  }

  private CacheResult buildCacheResult(List<App> loadBalancers, Map<String, CacheData> onDemandKeep, List<String> onDemandEvict, Long start) {
    log.info("Describing items in ${agentType}")

    Map<String, MutableCacheData> cachedLoadBalancers = MutableCacheData.mutableCacheMap()

    for (App loadBalancer : loadBalancers) {
      if (!loadBalancer) {
        continue
      }

      Optional<DcosSpinnakerLbId> dcosSpinnakerLbId = DcosSpinnakerLbId.parse(loadBalancer.id)

      if (!dcosSpinnakerLbId.present) {
        continue
      }

      def appId = dcosSpinnakerLbId.get()

      def onDemandData = onDemandKeep ? onDemandKeep[Keys.getLoadBalancerKey(appId, clusterName)] : null

      if (onDemandData && onDemandData.attributes.cacheTime >= start) {
        Map<String, List<CacheData>> cacheResults = objectMapper.readValue(onDemandData.attributes.cacheResults as String, new TypeReference<Map<String, List<MutableCacheData>>>() {
        })
        cache(cacheResults, Keys.Namespace.LOAD_BALANCERS.ns, cachedLoadBalancers)
      } else {
        def loadBalancerKey = Keys.getLoadBalancerKey(appId, clusterName)

        cachedLoadBalancers[loadBalancerKey].with {
          attributes.name = appId.toString()
          attributes.app = loadBalancer
          // Relationships are stored in DcosServerGroupCachingAgent.
        }
      }
    }

    log.info("Caching ${cachedLoadBalancers.size()} load balancers in ${agentType}")

    return new DefaultCacheResult([
                                    (Keys.Namespace.LOAD_BALANCERS.ns): cachedLoadBalancers.values(),
                                    (Keys.Namespace.ON_DEMAND.ns)     : onDemandKeep.values()
                                  ], [
                                    (Keys.Namespace.ON_DEMAND.ns): onDemandEvict,
                                  ])
  }
}

