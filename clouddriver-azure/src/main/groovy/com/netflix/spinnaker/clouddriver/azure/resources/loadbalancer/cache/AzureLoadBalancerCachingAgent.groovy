/*
 * Copyright 2015 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AccountAware
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.CachingAgent
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.provider.AzureInfrastructureProvider
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancerDescription
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import groovy.transform.WithWriteLock
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class AzureLoadBalancerCachingAgent implements CachingAgent, OnDemandAgent, AccountAware {

  final AzureCloudProvider azureCloudProvider
  final String accountName
  final AzureCredentials creds
  final String region
  final ObjectMapper objectMapper
  final Registry registry
  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.AZURE_LOAD_BALANCERS.ns)
  ] as Set)

  AzureLoadBalancerCachingAgent(AzureCloudProvider azureCloudProvider,
                           String accountName,
                           AzureCredentials creds,
                           String region,
                           ObjectMapper objectMapper,
                           Registry registry) {
    this.azureCloudProvider = azureCloudProvider
    this.accountName = accountName
    this.creds = creds
    this.region = region
    this.objectMapper = objectMapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    this.registry = registry
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${azureCloudProvider.id}:${OnDemandAgent.OnDemandType.LoadBalancer}")
  }

  @Override
  String getProviderName() {
    AzureInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${accountName}/${region}/${AzureLoadBalancerCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    accountName
  }

  @Override
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.LoadBalancer && cloudProvider == azureCloudProvider.id
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("loadBalancerName") ||
        !data.containsKey("account") ||
        !data.containsKey("region")  ||
        accountName != data.account ||
        region != data.region) {
      return null
    }

    AzureLoadBalancerDescription updatedLoadBalancer = null
    AzureLoadBalancerDescription evictedLoadBalancer = null
    String loadBalancerName = data.loadBalancerName as String
    String resourceGroupName = AzureUtilities.getResourceGroupName(AzureUtilities.getAppNameFromAzureResourceName(loadBalancerName), region)
    if (resourceGroupName == null) {
      log.info("handle->Unexpected error retrieving resource group name: null")
      return []
    }

    try {
      updatedLoadBalancer = metricsSupport.readData {
        creds.networkClient.getLoadBalancer(resourceGroupName, loadBalancerName)
      }
    } catch (Exception e ) {
      log.error("handle->Unexpected exception", e)
      return null
    }

    def cacheResult = metricsSupport.transformData {
      if (updatedLoadBalancer) {
        return buildCacheResult(providerCache, null, 0, updatedLoadBalancer, null)
      } else {
        evictedLoadBalancer = new AzureLoadBalancerDescription(
          loadBalancerName: loadBalancerName,
          region: region,
          appName: AzureUtilities.getAppNameFromAzureResourceName(loadBalancerName),
          cloudProvider: "azure",
          cluster: "none",
          lastReadTime: System.currentTimeMillis()
        )
        return buildCacheResult(providerCache, null, 0, null, evictedLoadBalancer)
      }
    }
    Map<String, Collection<String>> evictions = evictedLoadBalancer ? [(Keys.Namespace.AZURE_LOAD_BALANCERS.ns): [getLoadBalancerKey(evictedLoadBalancer)]] : [:]

    log.info("onDemand cache refresh (data: ${data}, evictions: ${evictions})")
    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getAgentType(), cacheResult: cacheResult, evictions: evictions
    )
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    return []
  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    log.info("Describing items in ${agentType}")
    def currentTime = System.currentTimeMillis()
    buildCacheResult(providerCache, creds.networkClient.getLoadBalancersAll(region), currentTime, null, null)
  }

  @WithWriteLock
  // Allow only one thread at a given time to go through the OnDemand cache entries and read/modify them
  private CacheResult buildCacheResult(ProviderCache providerCache,
                                       Collection<AzureLoadBalancerDescription> loadBalancers,
                                       long lastReadTime,
                                       AzureLoadBalancerDescription updatedLoadBalancer,
                                       AzureLoadBalancerDescription evictedLoadBalancer) {
    if (loadBalancers) {
      List<CacheData> data = new ArrayList<CacheData>()
      Collection<String> identifiers = providerCache.filterIdentifiers(Keys.Namespace.AZURE_ON_DEMAND.ns, Keys.getLoadBalancerKey(azureCloudProvider, "*", "*", "*", "*", region, accountName))
      def onDemandCacheResults = providerCache.getAll(Keys.Namespace.AZURE_ON_DEMAND.ns, identifiers, RelationshipCacheFilter.none())

      // Add any outdated OnDemand cache entries to the evicted list
      List<String> evictions = new ArrayList<String>()
      Map<String, CacheData> usableOnDemandCacheDatas = [:]
      onDemandCacheResults.each {
        if(it.attributes.cachedTime < lastReadTime){
          evictions.add(it.id)
        } else {
          usableOnDemandCacheDatas[it.id] = it
        }
      }

      loadBalancers.each { AzureLoadBalancerDescription item ->
        AzureLoadBalancerDescription loadBalancer = item
        // Skip for internal type ALB (Which only serve for connection to VMSS instance)
        if (loadBalancer.internal) return

        String lbKey = getLoadBalancerKey(loadBalancer)

        // Search the current OnDemand update map entries and look for a load balancer match
        def onDemandLB = usableOnDemandCacheDatas[lbKey]
        if (onDemandLB) {
          if (onDemandLB.attributes.cachedTime > loadBalancer.lastReadTime) {
            if (onDemandLB.attributes.onDemandCacheType == "OnDemandUpdated") {
              // Found a load balancer that has been updated since last time was read from Azure cloud
              loadBalancer = objectMapper.readValue(onDemandLB.attributes.azureResourceDescription as String, AzureLoadBalancerDescription)
            } else {
              // Found a load balancer that has been deleted since last time was read from Azure cloud
              loadBalancer = null
            }
          }
        }

        if (loadBalancer) {
          data.add(buildCacheData(loadBalancer))
        }
      }

      log.info("Caching ${data.size()} items in ${agentType}")

      return new DefaultCacheResult(
        [(Keys.Namespace.AZURE_LOAD_BALANCERS.ns): data],
        [(Keys.Namespace.AZURE_ON_DEMAND.ns): evictions])

    } else {
      if (updatedLoadBalancer) {
        // This is an OnDemand update/edit request for a given load balancer resource
        // Attempt to add entry into the OnDemand respective cache
        if (updateCache(providerCache, updatedLoadBalancer, "OnDemandUpdated")) {
          CacheData data = buildCacheData(updatedLoadBalancer)

          log.info("Caching 1 OnDemand updated item in ${agentType}")
          return new DefaultCacheResult([(Keys.Namespace.AZURE_LOAD_BALANCERS.ns): [data]])
        } else {
          return null
        }
      }

      if (evictedLoadBalancer) {
        // This is an OnDemand delete request for a given load balancer resource
        // Attempt to add entry into the OnDemand respective cache
        if (updateCache(providerCache, evictedLoadBalancer, "OnDemandEvicted")) {
          log.info("Caching 1 OnDemand evicted item in ${agentType}")
          return new DefaultCacheResult([(Keys.Namespace.AZURE_LOAD_BALANCERS.ns): []])
        } else {
          return null
        }
      }
    }

    return new DefaultCacheResult([(Keys.Namespace.AZURE_LOAD_BALANCERS.ns): []])
  }

  // Update current cache only if the current entry is a new OnDemand request or if the current entry is more recent
  //  than the cache entry
  private Boolean updateCache(ProviderCache providerCache, AzureLoadBalancerDescription loadBalancer, String onDemandCacheType) {
    Boolean foundUpdatedOnDemandLB = false

    if (loadBalancer) {
      // Get the current list of all OnDemand requests from the cache
      def cacheResults = providerCache.getAll(Keys.Namespace.AZURE_ON_DEMAND.ns, [getLoadBalancerKey(loadBalancer)])

      if (cacheResults && !cacheResults.isEmpty()) {
        cacheResults.each {
          // cacheResults.each should only return one item which is matching the given load balancer details
          if (it.attributes.cachedTime > loadBalancer.lastReadTime) {
            // Found a newer matching entry in the cache when compared with the current OnDemand request
            foundUpdatedOnDemandLB = true
          }
        }
      }

      if (!foundUpdatedOnDemandLB) {
        // Add entry to the OnDemand respective cache
        def cacheData = new DefaultCacheData(
          getLoadBalancerKey(loadBalancer),
          [
            azureResourceDescription: objectMapper.writeValueAsString(loadBalancer),
            cachedTime: loadBalancer.lastReadTime,
            onDemandCacheType : onDemandCacheType
          ],
          [:]
        )
        providerCache.putCacheData(Keys.Namespace.AZURE_ON_DEMAND.ns, cacheData)

        return true
      }
    }

    false
  }

  private CacheData buildCacheData(AzureLoadBalancerDescription loadBalancer) {
    Map<String, Object> attributes = [loadbalancer: loadBalancer]

    new DefaultCacheData(getLoadBalancerKey(loadBalancer), attributes, [:])
  }

  // return a key corresponding to a specific load balancer resource to be used for map indexing
  private String getLoadBalancerKey(AzureLoadBalancerDescription loadBalancer) {
    Keys.getLoadBalancerKey(azureCloudProvider,
      loadBalancer.loadBalancerName,
      loadBalancer.loadBalancerName,
      loadBalancer.appName ?: "none",
      loadBalancer.cluster ?: "none",
      region,
      accountName)
  }
}
