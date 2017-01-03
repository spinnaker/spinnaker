/*
 * Copyright 2016 Google, Inc.
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

package com.netflix.spinnaker.clouddriver.appengine.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.appengine.AppEngineCloudProvider
import com.netflix.spinnaker.clouddriver.appengine.cache.Keys
import com.netflix.spinnaker.clouddriver.appengine.model.*
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.appengine.cache.Keys.Namespace.*

@Component
class AppEngineClusterProvider implements ClusterProvider<AppEngineCluster> {
  @Autowired
  Cache cacheView

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  AppEngineApplicationProvider appEngineApplicationProvider

  @Override
  Set<AppEngineCluster> getClusters(String applicationName, String account) {
    CacheData application = cacheView.get(APPLICATIONS.ns,
                                          Keys.getApplicationKey(applicationName),
                                          RelationshipCacheFilter.include(CLUSTERS.ns))
    if (!application) {
      return []
    }

    Collection<String> clusterKeys = application.relationships[CLUSTERS.ns]
      .findAll { Keys.parse(it).account == account }
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns, clusterKeys)

    translateClusters(clusterData, true)
  }

  @Override
  Map<String, Set<AppEngineCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns)
    translateClusters(clusterData, true).groupBy { it.accountName } as Map<String, Set<AppEngineCluster>>
  }

  @Override
  AppEngineCluster getCluster(String applicationName, String account, String clusterName) {
    List<CacheData> clusterData =
      [cacheView.get(CLUSTERS.ns, Keys.getClusterKey(account, applicationName, clusterName))] - null

    clusterData ? translateClusters(clusterData, true).head() : null
  }

  @Override
  AppEngineServerGroup getServerGroup(String account, String region, String serverGroupName) {
    String serverGroupKey = Keys.getServerGroupKey(account, serverGroupName, region)
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey)

    if (!serverGroupData) {
      return null
    }

    Set<AppEngineInstance> instances = cacheView.getAll(INSTANCES.ns, serverGroupData.relationships[INSTANCES.ns])
      .findResults { AppEngineProviderUtils.instanceFromCacheData(objectMapper, it) }

    AppEngineProviderUtils.serverGroupFromCacheData(objectMapper, serverGroupData, instances)
  }

  @Override
  Map<String, Set<AppEngineCluster>> getClusterSummaries(String applicationName) {
    translateClusters(getClusterData(applicationName), false)?.groupBy { it.accountName } as Map<String, Set<AppEngineCluster>>
  }

  @Override
  Map<String, Set<AppEngineCluster>> getClusterDetails(String applicationName) {
    translateClusters(getClusterData(applicationName), true)?.groupBy { it.accountName } as Map<String, Set<AppEngineCluster>>
  }

  Set<CacheData> getClusterData(String applicationName) {
    AppEngineApplication application = appEngineApplicationProvider.getApplication(applicationName)

    def clusterKeys = []
    application?.clusterNames?.each { String accountName, Set<String> clusterNames ->
      clusterKeys.addAll(clusterNames.collect { clusterName ->
        Keys.getClusterKey(accountName, applicationName, clusterName)
      })
    }

    cacheView.getAll(CLUSTERS.ns,
                     clusterKeys,
                     RelationshipCacheFilter.include(SERVER_GROUPS.ns, LOAD_BALANCERS.ns))
  }

  @Override
  String getCloudProviderId() {
    AppEngineCloudProvider.ID
  }

  Collection<AppEngineCluster> translateClusters(Collection<CacheData> clusterData, boolean includeDetails) {
    if (!clusterData) {
      return []
    }

    Map<String, AppEngineLoadBalancer> loadBalancers = includeDetails ?
      translateLoadBalancers(AppEngineProviderUtils.resolveRelationshipDataForCollection(
        cacheView,
        clusterData,
        LOAD_BALANCERS.ns)) : null

    Map<String, Set<AppEngineServerGroup>> serverGroups = includeDetails ?
      translateServerGroups(AppEngineProviderUtils.resolveRelationshipDataForCollection(
        cacheView,
        clusterData,
        SERVER_GROUPS.ns,
        RelationshipCacheFilter.include(INSTANCES.ns, LOAD_BALANCERS.ns))) : null

    return clusterData.collect { CacheData clusterDataEntry ->
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.id)
      AppEngineCluster cluster = new AppEngineCluster(accountName: clusterKey.account, name: clusterKey.name)

      if (includeDetails) {
        cluster.loadBalancers = clusterDataEntry.relationships[LOAD_BALANCERS.ns]?.findResults { id ->
          loadBalancers.get(id)
        }
        cluster.serverGroups = serverGroups[cluster.name]?.findAll { it.account == cluster.accountName } ?: []
      } else {
        cluster.loadBalancers = clusterDataEntry.relationships[LOAD_BALANCERS.ns].collect { loadBalancerKey ->
          def parts = Keys.parse(loadBalancerKey)
          new AppEngineLoadBalancer(name: parts.name, account: parts.account)
        }
        cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns].collect { serverGroupKey ->
          def parts = Keys.parse(serverGroupKey)
          new AppEngineServerGroup(name: parts.name, account: parts.account)
        }
      }
      cluster
    }
  }

  Map<String, Set<AppEngineServerGroup>> translateServerGroups(Collection<CacheData> serverGroupData) {
    def instanceCacheDataMap = AppEngineProviderUtils
      .preserveRelationshipDataForCollection(cacheView,
                                             serverGroupData,
                                             INSTANCES.ns,
                                             RelationshipCacheFilter.none())

    def instances = instanceCacheDataMap.collectEntries { String key, Collection<CacheData> cacheData ->
        [(key): cacheData.findResults { AppEngineProviderUtils.instanceFromCacheData(objectMapper, it) } as Set ]
    }

    return serverGroupData
      .inject([:].withDefault { [] }, { Map<String, Set<AppEngineServerGroup>> acc, CacheData cacheData ->
        def serverGroup = AppEngineProviderUtils.serverGroupFromCacheData(objectMapper,
                                                                          cacheData,
                                                                          instances[cacheData.id])
        acc[Names.parseName(serverGroup.name).cluster].add(serverGroup)
        acc
      })
  }

  static Map<String, AppEngineLoadBalancer> translateLoadBalancers(Collection<CacheData> loadBalancerData) {
    loadBalancerData.collectEntries { loadBalancerEntry ->
      def parts = Keys.parse(loadBalancerEntry.id)
      [(loadBalancerEntry.id): new AppEngineLoadBalancer(name: parts.name, account: parts.account)]
    }
  }
}
