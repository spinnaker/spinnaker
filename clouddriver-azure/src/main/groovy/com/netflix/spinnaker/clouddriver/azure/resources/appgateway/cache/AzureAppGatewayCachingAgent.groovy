/*
 * Copyright 2016 The original authors.
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

package com.netflix.spinnaker.clouddriver.azure.resources.appgateway.cache

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
import com.netflix.spinnaker.clouddriver.azure.common.cache.AzureCachingAgent
import com.netflix.spinnaker.clouddriver.azure.resources.appgateway.model.AzureAppGatewayDescription
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.provider.AzureInfrastructureProvider
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import groovy.transform.WithWriteLock
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class AzureAppGatewayCachingAgent extends AzureCachingAgent {
  final Registry registry
  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.AZURE_APP_GATEWAYS.ns)
  ] as Set)

  AzureAppGatewayCachingAgent(AzureCloudProvider azureCloudProvider,
                              String accountName,
                              AzureCredentials creds,
                              String region,
                              ObjectMapper objectMapper,
                              Registry registry) {
    super(azureCloudProvider, accountName, creds, region, objectMapper)
    this.registry = registry
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${azureCloudProvider.id}:${OnDemandAgent.OnDemandType.LoadBalancer}")
  }

  @Override
  Set<AgentDataType> initializeTypes() {
    Collections.unmodifiableSet([
      AUTHORITATIVE.forType(Keys.Namespace.AZURE_APP_GATEWAYS.ns)
    ] as Set)
  }

  @Override
  Boolean validKeys(Map<String, ? extends Object> data) {
    data.containsKey("loadBalancerName") &&
      data.containsKey("account") &&
      data.containsKey("region") &&
      accountName == data.account &&
      region == data.region
  }

  @Override
  OnDemandAgent.OnDemandType getOnDemandType() {
    OnDemandAgent.OnDemandType.LoadBalancer
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.LoadBalancer && cloudProvider == azureCloudProvider.id
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (validKeys(data)) {
      return null
    }

    AzureAppGatewayDescription updatedAppGateway = null
    AzureAppGatewayDescription evictedAppGateway = null
    String appGatewayName = data.loadBalancerName as String
    String resourceGroupName = AzureUtilities.getResourceGroupName(AzureUtilities.getAppNameFromAzureResourceName(appGatewayName), region)
    if (resourceGroupName == null) {
      log.info("handle->Unexpected error retrieving resource group name: null")
      return []
    }

    try {
      updatedAppGateway = metricsSupport.readData {
        creds.networkClient.getAppGateway(resourceGroupName, appGatewayName)
      }
    } catch (Exception e ) {
      log.error("handle->Unexpected exception", e)
      return null
    }

    def cacheResult = metricsSupport.transformData {
      if (updatedAppGateway) {
        return buildCacheResult(providerCache, null, 0, updatedAppGateway, null)
      } else {
        evictedAppGateway = new AzureAppGatewayDescription(
          loadBalancerName: appGatewayName,
          name: appGatewayName,
          region: region,
          appName: AzureUtilities.getAppNameFromAzureResourceName(appGatewayName),
          cloudProvider: "azure",
          cluster: "none",
          lastReadTime: System.currentTimeMillis()
        )
        return buildCacheResult(providerCache, null, 0, null, evictedAppGateway)
      }
    }
    Map<String, Collection<String>> evictions = evictedAppGateway ? [(Keys.Namespace.AZURE_APP_GATEWAYS.ns): [getAppGatewayKey(evictedAppGateway)]] : [:]

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
    buildCacheResult(providerCache, creds.networkClient.getAppGatewaysAll(region), currentTime, null, null)
  }

  @WithWriteLock
  // Allow only one thread at a given time to go through the AppGateway specific cache entries and modify them
  private CacheResult buildCacheResult(ProviderCache providerCache,
                                       Collection<AzureAppGatewayDescription> appGateways,
                                       long lastReadTime,
                                       AzureAppGatewayDescription updatedAppGateway,
                                       AzureAppGatewayDescription evictedAppGateway) {
    if (appGateways) {
      List<CacheData> data = new ArrayList<CacheData>()
      Collection<String> identifiers = providerCache.filterIdentifiers(
        Keys.Namespace.AZURE_ON_DEMAND.ns,
        Keys.getAppGatewayKey(azureCloudProvider, "*", "*", region, accountName)
      )
      def onDemandCacheResults = providerCache.getAll(
        Keys.Namespace.AZURE_ON_DEMAND.ns,
        identifiers,
        RelationshipCacheFilter.none()
      )

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

      appGateways.each { AzureAppGatewayDescription item ->
        AzureAppGatewayDescription appGateway = item
        String agKey = getAppGatewayKey(appGateway)

        // Search the current OnDemand update map entries and look for an application gateway match
        def onDemandAG = usableOnDemandCacheDatas[agKey]
        if (onDemandAG) {
          if (onDemandAG.attributes.cachedTime > appGateway.lastReadTime) {
            if (onDemandAG.attributes.onDemandCacheType == "OnDemandUpdated") {
              // Found an application gateway that has been updated since last time was read from Azure cloud
              appGateway = objectMapper.readValue(onDemandAG.attributes.azureResourceDescription as String, AzureAppGatewayDescription)
            } else {
              // Found an application gateway that has been deleted since last time was read from Azure cloud
              appGateway = null
            }
          }
        }

        if (appGateway) {
          data.add(buildCacheData(appGateway))
        }
      }

      log.info("Caching ${data.size()} items in ${agentType}")

      return new DefaultCacheResult(
        [(Keys.Namespace.AZURE_APP_GATEWAYS.ns): data],
        [(Keys.Namespace.AZURE_ON_DEMAND.ns): evictions])

    } else {
      if (updatedAppGateway) {
        // This is an OnDemand update/edit request for a given application gateway resource
        // Attempt to add entry into the OnDemand respective cache
        if (updateCache(providerCache, updatedAppGateway, "OnDemandUpdated")) {
          CacheData data = buildCacheData(updatedAppGateway)

          log.info("Caching 1 OnDemand updated item in ${agentType}")
          return new DefaultCacheResult([(Keys.Namespace.AZURE_APP_GATEWAYS.ns): [data]])
        } else {
          return null
        }
      }

      if (evictedAppGateway) {
        // This is an OnDemand delete request for a given application gateway resource
        // Attempt to add entry into the OnDemand respective cache
        if (updateCache(providerCache, evictedAppGateway, "OnDemandEvicted")) {
          log.info("Caching 1 OnDemand evicted item in ${agentType}")
          return new DefaultCacheResult([(Keys.Namespace.AZURE_APP_GATEWAYS.ns): []])
        } else {
          return null
        }
      }
    }

    return new DefaultCacheResult([(Keys.Namespace.AZURE_APP_GATEWAYS.ns): []])
  }

  // Update current cache only if the current entry is a new OnDemand request or if the current entry is more recent
  //  than the cache entry
  private Boolean updateCache(ProviderCache providerCache, AzureAppGatewayDescription appGateway, String onDemandCacheType) {
    Boolean foundUpdatedOnDemandLB = false

    if (appGateway) {
      // Get the current list of all OnDemand requests from the cache
      def cacheResults = providerCache.getAll(Keys.Namespace.AZURE_ON_DEMAND.ns, [getAppGatewayKey(appGateway)])

      if (cacheResults && !cacheResults.isEmpty()) {
        cacheResults.each {
          // cacheResults.each should only return one item which is matching the given application gateway details object
          if (it.attributes.cachedTime > appGateway.lastReadTime) {
            // Found a newer matching entry in the cache when compared with the current OnDemand request
            foundUpdatedOnDemandLB = true
          }
        }
      }

      if (!foundUpdatedOnDemandLB) {
        // Add entry to the OnDemand respective cache
        def cacheData = new DefaultCacheData(
          getAppGatewayKey(appGateway),
          [
            azureResourceDescription: objectMapper.writeValueAsString(appGateway),
            cachedTime: appGateway.lastReadTime,
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

  private CacheData buildCacheData(AzureAppGatewayDescription appGateway) {
    Map<String, Object> attributes = [appgateway: appGateway]

    new DefaultCacheData(getAppGatewayKey(appGateway), attributes, [:])
  }

  // return a key corresponding to a specific application gateway resource to be used for map indexing
  private String getAppGatewayKey(AzureAppGatewayDescription appGateway) {
    Keys.getAppGatewayKey(
      azureCloudProvider,
      appGateway.appName ?: "none",
      appGateway.name,
      region,
      accountName)
  }
}
