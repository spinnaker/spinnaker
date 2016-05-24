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

package com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.cache

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
import com.netflix.spinnaker.clouddriver.azure.resources.securitygroup.model.AzureSecurityGroupDescription
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import groovy.transform.WithWriteLock
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE

@Slf4j
class AzureSecurityGroupCachingAgent implements CachingAgent, OnDemandAgent, AccountAware {

  final AzureCloudProvider azureCloudProvider
  final String accountName
  final AzureCredentials creds
  final String region
  final ObjectMapper objectMapper
  final Registry registry
  final OnDemandMetricsSupport metricsSupport

  static final Set<AgentDataType> types = Collections.unmodifiableSet([
    AUTHORITATIVE.forType(Keys.Namespace.SECURITY_GROUPS.ns)
  ] as Set)

  AzureSecurityGroupCachingAgent(AzureCloudProvider azureCloudProvider,
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
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${azureCloudProvider.id}:${OnDemandAgent.OnDemandType.SecurityGroup}")
  }

  @Override
  String getProviderName() {
    AzureInfrastructureProvider.PROVIDER_NAME
  }

  @Override
  String getAgentType() {
    "${accountName}/${region}/${AzureSecurityGroupCachingAgent.simpleName}"
  }

  @Override
  String getAccountName() {
    accountName
  }

  @Override
  Collection<AgentDataType> getProvidedDataTypes() {
    return types
  }

  @Override
  String getOnDemandAgentType() {
    "${getAgentType()}-OnDemand"
  }

  @Override
  boolean handles(OnDemandAgent.OnDemandType type, String cloudProvider) {
    type == OnDemandAgent.OnDemandType.SecurityGroup && cloudProvider == azureCloudProvider.id
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!data.containsKey("securityGroupName") ||
        !data.containsKey("account") ||
        !data.containsKey("region")  ||
        accountName != data.account ||
        region != data.region) {
      return null
    }

    AzureSecurityGroupDescription updatedSecurityGroup = null
    AzureSecurityGroupDescription evictedSecurityGroup = null
    String securityGroupName = data.securityGroupName as String
    String resourceGroupName = AzureUtilities.getResourceGroupName(AzureUtilities.getAppNameFromAzureResourceName(securityGroupName), region)
    if (resourceGroupName == null) {
      log.info("handle->Unexpected error retrieving resource group name: null")
      return []
    }

    try {
      updatedSecurityGroup = metricsSupport.readData {
        creds.networkClient.getNetworkSecurityGroup(resourceGroupName, securityGroupName)
      }
    } catch (Exception e ) {
      log.error("handle->Unexpected exception", e)
      return null
    }

    def cacheResult = metricsSupport.transformData {
      if (updatedSecurityGroup) {
        return buildCacheResult(providerCache, null, 0, updatedSecurityGroup, null)
      } else {
        evictedSecurityGroup = new AzureSecurityGroupDescription(
          name: securityGroupName,
          region: region,
          appName: AzureUtilities.getAppNameFromAzureResourceName(securityGroupName),
          cloudProvider: "azure",
          lastReadTime: System.currentTimeMillis()
        )
        return buildCacheResult(providerCache, null, 0, null, evictedSecurityGroup)
      }
    }
    Map<String, Collection<String>> evictions = evictedSecurityGroup ? [(Keys.Namespace.SECURITY_GROUPS.ns): [getSecurityGroupKey(evictedSecurityGroup)]] : [:]

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

    buildCacheResult(providerCache, creds.networkClient.getNetworkSecurityGroupsAll(region), currentTime, null, null)
  }

  @WithWriteLock
  // Allow only one thread at a given time to go through the OnDemand maps and read/modify them
  private CacheResult buildCacheResult(ProviderCache providerCache,
                                       Collection<AzureSecurityGroupDescription> securityGroups,
                                       long lastReadTime,
                                       AzureSecurityGroupDescription updatedSecurityGroup,
                                       AzureSecurityGroupDescription evictedSecurityGroup) {
    if (securityGroups) {
      List<CacheData> data = new ArrayList<CacheData>()
      Collection<String> identifiers = providerCache.filterIdentifiers(Keys.Namespace.AZURE_ON_DEMAND.ns, Keys.getSecurityGroupKey(azureCloudProvider, "*", "*", region, accountName))
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


      securityGroups.each { AzureSecurityGroupDescription item ->
        AzureSecurityGroupDescription securityGroup = item
        String sgKey = getSecurityGroupKey(securityGroup)

        // Search the current OnDemand update map entries and look for a security group match
        def onDemandSG = usableOnDemandCacheDatas[sgKey]
        if (onDemandSG) {
          if (onDemandSG.attributes.cachedTime > securityGroup.lastReadTime) {
            // Found a security group resource that has been updated since last time was read from Azure cloud
            securityGroup = objectMapper.readValue(onDemandSG.attributes.azureResourceDescription as String, AzureSecurityGroupDescription)
          } else {
            // Found a load balancer that has been deleted since last time was read from Azure cloud
            securityGroup = null
          }

          // There's no need to keep this entry in the map
          usableOnDemandCacheDatas.remove(sgKey)
        }

        if (securityGroup) {
          data.add(buildCacheData(securityGroup))
        }
      }

      log.info("Caching ${data.size()} items in ${agentType}")

      return new DefaultCacheResult(
        [(Keys.Namespace.SECURITY_GROUPS.ns): data],
        [(Keys.Namespace.AZURE_ON_DEMAND.ns): evictions])
    } else {
      if (updatedSecurityGroup) {
        // This is an OnDemand update/edit request for a given security group resource
        // Attempt to add entry into the OnDemand respective cache
        if (updateCache(providerCache, updatedSecurityGroup, "OnDemandUpdated")) {
          CacheData data = buildCacheData(updatedSecurityGroup)
          log.info("Caching 1 OnDemand updated item in ${agentType}")
          return new DefaultCacheResult([(Keys.Namespace.SECURITY_GROUPS.ns): [data]])
        } else {
          return null
        }
      }

      if (evictedSecurityGroup) {
        // This is an OnDemand delete request for a given Azure network security group resource
        // Attempt to add entry into the OnDemand respective cache
        if (updateCache(providerCache, evictedSecurityGroup, "OnDemandEvicted")) {
          log.info("Caching 1 OnDemand evicted item in ${agentType}")
          return new DefaultCacheResult([(Keys.Namespace.SECURITY_GROUPS.ns): []])
        } else {
          return null
        }
      }
    }

    return new DefaultCacheResult([(Keys.Namespace.SECURITY_GROUPS.ns): []])
  }

  // Update current cache only if the current entry is a new OnDemand request or if the current entry is more recent
  //  than the cache entry
  private Boolean updateCache(ProviderCache providerCache, AzureSecurityGroupDescription securityGroup, String onDemandCacheType) {
    Boolean foundUpdatedOnDemandSG = false

    if (securityGroup) {
      // Get the current list of all OnDemand requests from the cache
      def cacheResults = providerCache.getAll(Keys.Namespace.AZURE_ON_DEMAND.ns, [getSecurityGroupKey(securityGroup)])

      if (cacheResults && !cacheResults.isEmpty()) {
        cacheResults.each {
          // cacheResults.each should only return one item which is matching the given security group details
          if (it.attributes.cachedTime > securityGroup.lastReadTime) {
            // Found a newer matching entry in the cache when compared with the current OnDemand request
            foundUpdatedOnDemandSG = true
          }
        }
      }

      if (!foundUpdatedOnDemandSG) {
        // Add entry to the OnDemand respective cache
        def cacheData = new DefaultCacheData(
          getSecurityGroupKey(securityGroup),
          [
            azureResourceDescription: objectMapper.writeValueAsString(securityGroup),
            cachedTime: securityGroup.lastReadTime,
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

  private CacheData buildCacheData(AzureSecurityGroupDescription securityGroup) {
    Map<String, Object> attributes = [securitygroup: securityGroup]

    new DefaultCacheData(getSecurityGroupKey(securityGroup), attributes, [:])
  }

  // return a key corresponding to a specific Azure network security group resource to be used for map indexing
  private String getSecurityGroupKey(AzureSecurityGroupDescription securityGroup) {
    Keys.getSecurityGroupKey(azureCloudProvider, securityGroup.name, securityGroup.id, region, accountName)  }
}
