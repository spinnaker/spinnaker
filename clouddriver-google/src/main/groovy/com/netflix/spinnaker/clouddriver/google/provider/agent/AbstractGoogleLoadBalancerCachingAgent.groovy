/*
 * Copyright 2017 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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
 */

package com.netflix.spinnaker.clouddriver.google.provider.agent

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.CacheResultBuilder
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.security.GoogleNamedAccountCredentials
import groovy.transform.Canonical
import groovy.util.logging.Slf4j

import java.util.concurrent.TimeUnit

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE
import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

@Slf4j
abstract class AbstractGoogleLoadBalancerCachingAgent extends AbstractGoogleCachingAgent implements OnDemandAgent {

  String agentType = "${accountName}/${region}/${this.class.simpleName}"
  String onDemandAgentType = "${agentType}-OnDemand"

  final String region
  final OnDemandMetricsSupport metricsSupport

  final Set<AgentDataType> providedDataTypes = [
    AUTHORITATIVE.forType(LOAD_BALANCERS.ns),
    INFORMATIVE.forType(INSTANCES.ns),
  ] as Set

  AbstractGoogleLoadBalancerCachingAgent(String clouddriverUserAgentApplicationName,
                                         GoogleNamedAccountCredentials credentials,
                                         ObjectMapper objectMapper,
                                         Registry registry,
                                         String region) {
    super(clouddriverUserAgentApplicationName,
          credentials,
          objectMapper,
          registry)
    this.region = region
    this.metricsSupport = new OnDemandMetricsSupport(
      registry,
      this,
      "${GoogleCloudProvider.ID}:${OnDemandAgent.OnDemandType.LoadBalancer}")
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.LoadBalancer && cloudProvider == GoogleCloudProvider.ID
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("loadBalancerName") || data.account != accountName || data.region != region) {
      return null
    }

    GoogleLoadBalancer loadBalancer

    try {
      loadBalancer = metricsSupport.readData {
        getLoadBalancer(data.loadBalancerName as String)
      }
    } catch (IllegalArgumentException e) {
      // If after retrieving the root of the load balancer resource tree, the caching agent determines that it is not
      // responsible for caching the type of load balancer retrieved, it will throw this exception. We then return
      // null to avoid evicting the load balancer from the on demand namespace (since the correct caching agent will
      // have added it there).
      return null
    }

    def loadBalancerKey
    Collection<String> identifiers = []

    if (loadBalancer) {
      loadBalancerKey = Keys.getLoadBalancerKey(loadBalancer.region, accountName, loadBalancer.name)
    } else {
      loadBalancerKey = Keys.getLoadBalancerKey(region, accountName, data.loadBalancerName as String)

      // TODO(duftler): Is this right? Seems like this should use a wildcard.
      // No load balancer was found, so need to find identifiers for all load balancers in the region.
      identifiers = providerCache.filterIdentifiers(LOAD_BALANCERS.ns, loadBalancerKey)
    }

    def cacheResultBuilder = new CacheResultBuilder(startTime: Long.MAX_VALUE)
    CacheResult result = metricsSupport.transformData {
      buildCacheResult(cacheResultBuilder, loadBalancer ? [loadBalancer] : [])
    }

    if (result.cacheResults.values().flatten().empty) {
      // Avoid writing an empty onDemand cache record (instead delete any that may have previously existed).
      providerCache.evictDeletedItems(ON_DEMAND.ns, identifiers)
    } else {
      metricsSupport.onDemandStore {
        def cacheData = new DefaultCacheData(
          loadBalancerKey,
          TimeUnit.MINUTES.toSeconds(10) as Integer, // ttl
          [
            cacheTime     : System.currentTimeMillis(),
            cacheResults  : objectMapper.writeValueAsString(result.cacheResults),
            processedCount: 0,
            processedTime : null
          ],
          [:]
        )

        providerCache.putCacheData(ON_DEMAND.ns, cacheData)
      }
    }

    Map<String, Collection<String>> evictions = [:].withDefault {_ -> []}
    if (!loadBalancer) {
      evictions[LOAD_BALANCERS.ns].addAll(identifiers)
    }

    log.info("On demand cache refresh succeeded. Data: ${data}. Added ${loadBalancer ? 1 : 0} items to the cache.")

    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getOnDemandAgentType(),
      cacheResult: result,
      evictions: evictions,
      // Do not include "authoritativeTypes" here, as it will result in all other cache entries getting deleted!
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keyOwnedByThisAgent = { Map<String, String> parsedKey ->
      parsedKey && parsedKey.account == accountName && parsedKey.region == region
    }

    def keys = providerCache.getIdentifiers(ON_DEMAND.ns).findAll { String key ->
      keyOwnedByThisAgent(Keys.parse(key))
    }

    return providerCache.getAll(ON_DEMAND.ns, keys).collect { CacheData cacheData ->
      def details = Keys.parse(cacheData.id)
      [
          details       : details,
          moniker       : convertOnDemandDetails(details),
          cacheTime     : cacheData.attributes.cacheTime,
          processedCount: cacheData.attributes.processedCount,
          processedTime : cacheData.attributes.processedTime
      ]
    }
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    def cacheResultBuilder = new CacheResultBuilder(startTime: System.currentTimeMillis())

    List<GoogleLoadBalancer> loadBalancers = getLoadBalancers()
    def loadBalancerKeys = loadBalancers.collect { Keys.getLoadBalancerKey(it.region, it.account, it.name) }

    providerCache.getAll(ON_DEMAND.ns, loadBalancerKeys).each { CacheData cacheData ->
      // Ensure that we don't overwrite data that was inserted by the `handle` method while we retrieved the
      // load balancers. Furthermore, cache data that hasn't been moved to the proper namespace needs to be
      // updated in the ON_DEMAND cache, so don't evict data without a processedCount > 0.
      if (cacheData.attributes.cacheTime < cacheResultBuilder.startTime && cacheData.attributes.processedCount > 0) {
        cacheResultBuilder.onDemand.toEvict << cacheData.id
      } else {
        cacheResultBuilder.onDemand.toKeep[cacheData.id] = cacheData
      }
    }

    CacheResult cacheResults = buildCacheResult(cacheResultBuilder, loadBalancers)

    cacheResults.cacheResults[ON_DEMAND.ns].each { CacheData cacheData ->
      cacheData.attributes.processedTime = System.currentTimeMillis()
      cacheData.attributes.processedCount = (cacheData.attributes.processedCount ?: 0) + 1
    }

    return cacheResults
  }

  List<GoogleLoadBalancer> getLoadBalancers() {
    constructLoadBalancers()
  }

  GoogleLoadBalancer getLoadBalancer(String onDemandLoadBalancerName) {
    def loadBalancers = constructLoadBalancers(onDemandLoadBalancerName)

    return loadBalancers ? loadBalancers.first() : null
  }

  abstract List<GoogleLoadBalancer> constructLoadBalancers(String onDemandLoadBalancerName = null)

  CacheResult buildCacheResult(CacheResultBuilder cacheResultBuilder, List<GoogleLoadBalancer> googleLoadBalancers) {
    log.info "Describing items in ${agentType}"

    googleLoadBalancers.each { GoogleLoadBalancer loadBalancer ->
      // TODO(duftler): Pull out getLoadBalancerKey() like getServerGroupKey()?
      def loadBalancerKey = Keys.getLoadBalancerKey(loadBalancer.region, loadBalancer.account, loadBalancer.name)
      def instanceKeys = loadBalancer.healths.collect { GoogleLoadBalancerHealth health ->
        determineInstanceKey(loadBalancer, health)
      }

      instanceKeys.each { String instanceKey ->
        cacheResultBuilder.namespace(INSTANCES.ns).keep(instanceKey).with {
          relationships[LOAD_BALANCERS.ns].add(loadBalancerKey)
        }
      }

      if (shouldUseOnDemandData(cacheResultBuilder, loadBalancerKey)) {
        moveOnDemandDataToNamespace(cacheResultBuilder, loadBalancer)
      } else {
        cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keep(loadBalancerKey).with {
          attributes = objectMapper.convertValue(loadBalancer, ATTRIBUTES)
          relationships[INSTANCES.ns].addAll(instanceKeys)
        }
      }
    }

    log.info "Caching ${cacheResultBuilder.namespace(LOAD_BALANCERS.ns).keepSize()} load balancers in ${agentType}"
    log.info "Caching ${cacheResultBuilder.namespace(INSTANCES.ns).keepSize()} instance relationships in ${agentType}"
    log.info "Caching ${cacheResultBuilder.onDemand.toKeep.size()} onDemand entries in ${agentType}"
    log.info "Evicting ${cacheResultBuilder.onDemand.toEvict.size()} onDemand entries in ${agentType}"

    return cacheResultBuilder.build()
  }

  String determineInstanceKey(GoogleLoadBalancer loadBalancer, GoogleLoadBalancerHealth health) {
    return Keys.getInstanceKey(accountName, loadBalancer.region, health.instanceName)
  }

  static boolean shouldUseOnDemandData(CacheResultBuilder cacheResultBuilder, String loadBalancerKey) {
    CacheData cacheData = cacheResultBuilder.onDemand.toKeep[loadBalancerKey]

    return cacheData ? cacheData.attributes.cacheTime >= cacheResultBuilder.startTime : false
  }

  void moveOnDemandDataToNamespace(CacheResultBuilder cacheResultBuilder,
                                   GoogleLoadBalancer googleLoadBalancer) {
    def loadBalancerKey = Keys.getLoadBalancerKey(region, accountName, googleLoadBalancer.name)
    Map<String, List<MutableCacheData>> onDemandData = objectMapper.readValue(
      cacheResultBuilder.onDemand.toKeep[loadBalancerKey].attributes.cacheResults as String,
      new TypeReference<Map<String, List<MutableCacheData>>>() {})

    onDemandData.each { String namespace, List<MutableCacheData> cacheDatas ->
      cacheDatas.each { MutableCacheData cacheData ->
        cacheResultBuilder.namespace(namespace).keep(cacheData.id).with { it ->
          it.attributes = cacheData.attributes
          it.relationships = Utils.mergeOnDemandCacheRelationships(cacheData.relationships, it.relationships)
        }
        cacheResultBuilder.onDemand.toKeep.remove(cacheData.id)
      }
    }
  }

  // TODO(lwander) this was taken from the netflix cluster caching, and should probably be shared between all providers.
  @Canonical
  static class MutableCacheData implements CacheData {
    String id
    int ttlSeconds = -1
    Map<String, Object> attributes = [:]
    Map<String, Collection<String>> relationships = [:].withDefault { [] as Set }
  }
}
