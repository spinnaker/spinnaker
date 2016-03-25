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

package com.netflix.spinnaker.clouddriver.azure.resources.servergroup.cache

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Registry
import com.netflix.spinnaker.cats.agent.AgentDataType
import com.netflix.spinnaker.cats.agent.CacheResult
import com.netflix.spinnaker.cats.agent.DefaultCacheResult
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.DefaultCacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.cats.provider.ProviderCache
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.common.AzureUtilities
import com.netflix.spinnaker.clouddriver.azure.common.cache.AzureCachingAgent
import com.netflix.spinnaker.clouddriver.azure.common.cache.MutableCacheData
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import static com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys.Namespace.*
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.security.AzureCredentials
import com.netflix.spinnaker.clouddriver.cache.OnDemandAgent
import com.netflix.spinnaker.clouddriver.cache.OnDemandMetricsSupport
import groovy.util.logging.Slf4j

import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.AUTHORITATIVE
import static com.netflix.spinnaker.cats.agent.AgentDataType.Authority.INFORMATIVE

@Slf4j
class AzureServerGroupCachingAgent extends AzureCachingAgent {

  final Registry registry
  final OnDemandMetricsSupport metricsSupport

  AzureServerGroupCachingAgent(AzureCloudProvider azureCloudProvider, String accountName, AzureCredentials creds, String region, ObjectMapper objectMapper, Registry registry) {
    super(azureCloudProvider, accountName, creds, region, objectMapper)

    this.registry = registry
    this.metricsSupport = new OnDemandMetricsSupport(registry, this, "${azureCloudProvider.id}:${onDemandType}")

  }

  @Override
  CacheResult loadData(ProviderCache providerCache) {
    def currentTime = System.currentTimeMillis()
    def result = buildCacheResult(providerCache, creds.computeClient.getServerGroupsAll(region), currentTime, null, null)

    result.cacheResults[AZURE_ON_DEMAND.ns].each {
      it.attributes.processedTime = System.currentTimeMillis()
      it.attributes.processedCount = (it.attributes.processedCount ?: 0) + 1
    }

    result
  }

  CacheResult buildCacheResult(ProviderCache providerCache,
                               Collection<AzureServerGroupDescription> serverGroups,
                               long lastReadTime,
                               AzureServerGroupDescription updatedServerGroup,
                               AzureServerGroupDescription evictedServerGroup) {

    log.info("Describing items in ${agentType}")

    if (serverGroups) {

      Map<String, MutableCacheData> cachedApplications = MutableCacheData.mutableCacheMap()
      Map<String, MutableCacheData> cachedClusters = MutableCacheData.mutableCacheMap()
      Map<String, MutableCacheData> cachedServerGroups = MutableCacheData.mutableCacheMap()
      Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()

      Collection<String> identifiers = providerCache.filterIdentifiers(AZURE_ON_DEMAND.ns, Keys.getServerGroupKey(azureCloudProvider, "*", region, accountName))
      def onDemandCacheResults = providerCache.getAll(AZURE_ON_DEMAND.ns, identifiers, RelationshipCacheFilter.none())

      // Add any outdated OnDemand cache entries to the evicted list
      def (evictions, usableOnDemandCacheData) = parseOnDemandCache(onDemandCacheResults, lastReadTime)

      serverGroups.each { AzureServerGroupDescription serverGroup ->
        AzureServerGroupDescription sg = serverGroup
        def serverGroupKey = Keys.getServerGroupKey(azureCloudProvider,
          sg.name,
          region,
          accountName)

        // Look for an entry for the server Group in the On Demand list
        def onDemandServerGroup = usableOnDemandCacheData[serverGroupKey] as CacheData
        if (onDemandServerGroup) {
          if (onDemandServerGroup.attributes.cachTime > serverGroup.lastReadTime) {
            if (onDemandServerGroup.attributes.onDemandCacheType == ON_DEMAND_UPDATED) {
              sg = objectMapper.readValue(onDemandServerGroup.attributes.AzureResourceDescription as String, AzureServerGroupDescription)
            }
          }
        }

        if (sg) {

          def clusterKey = Keys.getClusterKey(azureCloudProvider,
            sg.appName,
            sg.clusterName,
            accountName)
          def appKey = Keys.getApplicationKey(azureCloudProvider, sg.appName)

          def loadBalancerKey = Keys.getLoadBalancerKey(azureCloudProvider, sg.loadBalancerName,
            sg.loadBalancerName, sg.application, sg.clusterName, region, accountName)

          cachedApplications[appKey].with {
            attributes.name = sg.appName
            relationships[AZURE_CLUSTERS.ns].add(clusterKey)
            relationships[AZURE_SERVER_GROUPS.ns].add(serverGroupKey)
            relationships[AZURE_LOAD_BALANCERS.ns].add(loadBalancerKey)
          }

          cachedClusters[clusterKey].with {
            attributes.name = sg.clusterName
            attributes.accountName = accountName
            relationships[AZURE_APPLICATIONS.ns].add(appKey)
            relationships[AZURE_SERVER_GROUPS.ns].add(serverGroupKey)
            relationships[AZURE_LOAD_BALANCERS.ns].add(loadBalancerKey)
          }

          cachedServerGroups[serverGroupKey].with {
            attributes.serverGroup = sg
            relationships[AZURE_APPLICATIONS.ns].add(appKey)
            relationships[AZURE_CLUSTERS.ns].add(clusterKey)
            relationships[AZURE_LOAD_BALANCERS.ns].add(loadBalancerKey)
          }

          creds.computeClient.getServerGroupInstances(AzureUtilities.getResourceGroupName(sg), sg.name).each { instance ->
            def instanceKey = Keys.getInstanceKey(azureCloudProvider, sg.name, instance.name, region, accountName)
            cachedInstances[instanceKey].with {
              attributes.instance = instance
              relationships[AZURE_SERVER_GROUPS.ns].add(serverGroupKey)
            }
            cachedServerGroups[serverGroupKey].relationships[AZURE_INSTANCES.ns].add(instanceKey)
          }
        }
      }

      log.info("Caching ${cachedApplications.size()} applications in ${agentType}")
      log.info("Caching ${cachedClusters.size()} clusters in ${agentType}")
      log.info("Caching ${cachedServerGroups.size()} server groups in ${agentType}")
      log.info("Caching ${cachedInstances.size()} instances in ${agentType}")

      return new DefaultCacheResult([
        (AZURE_APPLICATIONS.ns) : cachedApplications.values(),
        (AZURE_CLUSTERS.ns) : cachedClusters.values(),
        (AZURE_SERVER_GROUPS.ns) : cachedServerGroups.values(),
        (AZURE_INSTANCES.ns) : cachedInstances.values(),
        (AZURE_ON_DEMAND.ns) : onDemandCacheResults
      ], [(AZURE_ON_DEMAND.ns): evictions as List<String>])

    } else {
      if (updatedServerGroup) {
        if (updateCache(providerCache, updatedServerGroup, ON_DEMAND_UPDATED)) {
          log.info("Caching 1 OnDemand updated item in ${agentType}")
          def serverGroupKey = getServerGroupKey(updatedServerGroup)
          MutableCacheData cachedServerGroup = new MutableCacheData(serverGroupKey)
          cachedServerGroup.attributes.serverGroup = updatedServerGroup

          Map<String, MutableCacheData> cachedInstances = MutableCacheData.mutableCacheMap()
          creds.
            computeClient.
            getServerGroupInstances(AzureUtilities.getResourceGroupName(updatedServerGroup),
              updatedServerGroup.name).each {
            def instanceKey = Keys.getInstanceKey(azureCloudProvider, updatedServerGroup.name, it.name, region, accountName)
            cachedInstances[instanceKey].with {
              attributes.instance = it
              relationships[AZURE_SERVER_GROUPS.ns].add(serverGroupKey)
            }
          }
          return new DefaultCacheResult([
            (AZURE_SERVER_GROUPS.ns): [cachedServerGroup],
            (AZURE_INSTANCES.ns): [cachedInstances]])
        } else {
          return null
        }
      }

      if (evictedServerGroup) {
        if (updateCache(providerCache, evictedServerGroup, ON_DEMAND_EVICTED)) {
          log.info("Caching 1 OnDemand evicted item in ${agentType}")
          return new DefaultCacheResult([(AZURE_SERVER_GROUPS.ns): []])
        }
        else {
          return null
        }
      }
    }
    new DefaultCacheResult([(AZURE_SERVER_GROUPS.ns): []])
  }

  @Override
  Set<AgentDataType> initializeTypes() {
    Collections.unmodifiableSet([
      AUTHORITATIVE.forType(AZURE_SERVER_GROUPS.ns),
      INFORMATIVE.forType(AZURE_APPLICATIONS.ns),
      INFORMATIVE.forType(AZURE_CLUSTERS.ns),
      INFORMATIVE.forType(AZURE_INSTANCES.ns)
    ] as Set)
  }

  @Override
  Collection<Map> pendingOnDemandRequests(ProviderCache providerCache) {
    def keys = providerCache.getIdentifiers(AZURE_ON_DEMAND.ns)
    keys = keys.findAll {
      def key = Keys.parse(azureCloudProvider, it)
      key.type == AZURE_SERVER_GROUPS.ns && key.account == accountName && key.region == region
    }
    return providerCache.getAll(AZURE_ON_DEMAND.ns, keys, RelationshipCacheFilter.none()).collect {
      [
        id: it.id,
        details  : Keys.parse(azureCloudProvider, it.id),
        cacheTime: it.attributes.cacheTime,
        processedCount: it.attributes.processedCount,
        processedTime: it.attributes.processedTime
      ]
    }
  }

  @Override
  OnDemandAgent.OnDemandResult handle(ProviderCache providerCache, Map<String, ? extends Object> data) {
    if (!validKeys(data)) {
      return null
    }

    AzureServerGroupDescription updatedServerGroup = null
    AzureServerGroupDescription evictedServerGroup = null
    String serverGroupName = data.serverGroupName as String
    String resourceGroupName = AzureUtilities.getResourceGroupName(AzureUtilities.getAppNameFromAzureResourceName(serverGroupName), region)
    if (resourceGroupName == null) {
      log.info("handle->Unexpected error retrieving resource group name: null")
      return []
    }

    try {
      updatedServerGroup = metricsSupport.readData {
        creds.computeClient.getServerGroup(resourceGroupName, serverGroupName)
      }
    } catch (Exception e ) {
      log.error("handle->Unexpected exception: ${e.message}")
      return null
    }

    def cacheResult = metricsSupport.transformData {
      if (updatedServerGroup) {
        return buildCacheResult(providerCache, null, 0, updatedServerGroup, null)
      } else {
        def appName = AzureUtilities.getAppNameFromAzureResourceName(serverGroupName)
        evictedServerGroup = new AzureServerGroupDescription(
          name: serverGroupName,
          region: region,
          appName: appName,
          cloudProvider: "azure",
          clusterName: appName,
          lastReadTime: System.currentTimeMillis()
        )
        return buildCacheResult(providerCache, null, 0, null, evictedServerGroup)
      }
    }
    Map<String, Collection<String>> evictions = evictedServerGroup ? [(AZURE_SERVER_GROUPS.ns): [getServerGroupKey(evictedServerGroup)]] : [:]

    log.info("onDemand cache refresh (data: ${data}, evictions: ${evictions})")
    return new OnDemandAgent.OnDemandResult(
      sourceAgentType: getAgentType(), cacheResult: cacheResult, evictions: evictions
    )

  }

  private Boolean updateCache(ProviderCache providerCache, AzureServerGroupDescription serverGroup, String onDemandCacheType) {
    Boolean foundOnDemandEntry = false

    if (serverGroup) {
      // Get the current list of all OnDemand requests from the cache
      def cacheResults = providerCache.getAll(AZURE_ON_DEMAND.ns, [getServerGroupKey(serverGroup)])

      if (cacheResults && !cacheResults.isEmpty()) {
        cacheResults.each {
          // cacheResults.each should only return one item which is matching the given resource details
          if (it.attributes.cachedTime > serverGroup.lastReadTime) {
            // Found a newer matching entry in the cache when compared with the current OnDemand request
            foundOnDemandEntry = true
          }
        }
      }

      if (!foundOnDemandEntry) {
        // Add entry to the OnDemand respective cache
        def cacheData = new DefaultCacheData(
          getServerGroupKey(serverGroup),
          [
            azureResourceDescription: objectMapper.writeValueAsString(serverGroup),
            cachedTime: serverGroup.lastReadTime,
            onDemandCacheType : onDemandCacheType,
            processedCount: 0,
            processedTime: null
          ],
          [:]
        )
        providerCache.putCacheData(AZURE_ON_DEMAND.ns, cacheData)

        return true
      }
    }

    false
  }


  @Override
  Boolean validKeys(Map<String, ? extends Object> data) {
    (data.containsKey("serverGroupName")
      && data.containsKey("account")
      && data.containsKey("region")
      && accountName == data.account
      && region == data.region)
  }

  @Override
  OnDemandAgent.OnDemandType getOnDemandType() {
    OnDemandAgent.OnDemandType.ServerGroup
  }

  private String getServerGroupKey(AzureServerGroupDescription serverGroup) {
    Keys.getServerGroupKey(azureCloudProvider,
      serverGroup.name,
      serverGroup.region,
      accountName)
  }

}
