/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.oort.titan.caching.providers
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.oort.model.ClusterProvider
import com.netflix.spinnaker.oort.titan.caching.Keys
import com.netflix.spinnaker.oort.titan.caching.TitanCachingProvider
import com.netflix.spinnaker.oort.titan.model.TitanCluster
import com.netflix.spinnaker.oort.titan.model.TitanInstance
import com.netflix.spinnaker.oort.titan.model.TitanServerGroup
import com.netflix.titanclient.model.Job
import com.netflix.titanclient.model.Task
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static Keys.Namespace.APPLICATIONS
import static Keys.Namespace.CLUSTERS
import static Keys.Namespace.HEALTH
import static Keys.Namespace.INSTANCES
import static Keys.Namespace.SERVER_GROUPS

@Component
class TitanClusterProvider implements ClusterProvider<TitanCluster> {

  private final Cache cacheView
  private final TitanCachingProvider titanCachingProvider
  private final ObjectMapper objectMapper

  @Autowired
  TitanClusterProvider(TitanCachingProvider titanCachingProvider,
                       Cache cacheView,
                       ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.titanCachingProvider = titanCachingProvider
    this.objectMapper = objectMapper
  }

  /**
   *
   * @return
   */
  @Override
  Map<String, Set<TitanCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns)
    Collection<TitanCluster> clustersList = translateClusters(clusterData, false)
    Map<String, Set<TitanCluster>> clusters = clustersList.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
    clusters
  }

  /**
   *
   * @param applicationName
   * @return
   */
  @Override
  Map<String, Set<TitanCluster>> getClusterSummaries(String applicationName) {
    Map<String, Set<TitanCluster>> clusters = getClustersInternal(applicationName, false)
    clusters
  }

  /**
   *
   * @param applicationName
   * @return
   */
  @Override
  Map<String, Set<TitanCluster>> getClusterDetails(String applicationName) {
    Map<String, Set<TitanCluster>> clusters = getClustersInternal(applicationName, true)
    clusters
  }

  /**
   *
   * @param applicationName
   * @param account
   * @return
   */
  @Override
  Set<TitanCluster> getClusters(String applicationName, String account) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName),
      RelationshipCacheFilter.include(CLUSTERS.ns))
    if (application == null) {
      return [] as Set
    }
    Collection<String> clusterKeys = application.relationships[CLUSTERS.ns].findAll { Keys.parse(it).account == account }
    Collection<CacheData> clusters = cacheView.getAll(CLUSTERS.ns, clusterKeys)
    translateClusters(clusters, true) as Set<TitanCluster>
  }

  /**
   *
   * @param application
   * @param account
   * @param name
   * @return
   */
  @Override
  TitanCluster getCluster(String application, String account, String name) {
    CacheData cluster = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(name, application, account))
    TitanCluster titanCluster = cluster ? translateClusters([cluster], true)[0] : null
    titanCluster
  }

  /**
   *
   * @param account
   * @param region
   * @param name
   * @return
   */
  @Override
  TitanServerGroup getServerGroup(String account, String region, String name) {
    String serverGroupKey = Keys.getServerGroupKey(name, account, region)
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey)
    if (serverGroupData == null) {
      return null
    }
    String json = objectMapper.writeValueAsString(serverGroupData.attributes.job)
    Job job = objectMapper.readValue(json, Job)
    TitanServerGroup serverGroup = new TitanServerGroup(job)
    serverGroup.placement.account = account
    serverGroup.placement.region = region
    serverGroup.instances = translateInstances(resolveRelationshipData(serverGroupData, INSTANCES.ns)).values()
    serverGroup
  }

  // Private methods

  private Map<String, Set<TitanCluster>> getClustersInternal(String applicationName, boolean includeDetails) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    if (application == null) return null
    Collection<TitanCluster> clusters = translateClusters(resolveRelationshipData(application, CLUSTERS.ns), includeDetails)
    clusters.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
  }

  /**
   * Translate clusters
   */
  private Collection<TitanCluster> translateClusters(Collection<CacheData> clusterData, boolean includeDetails) {
    Map<String, TitanServerGroup> serverGroups
    if (includeDetails) {
      Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(clusterData, SERVER_GROUPS.ns, RelationshipCacheFilter.include(INSTANCES.ns))
      serverGroups = translateServerGroups(allServerGroups)
    }
    Collection<TitanCluster> clusters = clusterData.collect { CacheData clusterDataEntry ->
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.id)
      TitanCluster cluster = new TitanCluster()
      cluster.accountName = clusterKey.account
      cluster.name = clusterKey.cluster
      if (includeDetails) {
        cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns]?.findResults { serverGroups.get(it) }
      } else {
        cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns]?.collect { serverGroupKey ->
          Map<String, String> keyParts = Keys.parse(serverGroupKey)
          TitanServerGroup titanServerGroup = new TitanServerGroup()
          titanServerGroup.placement.account = keyParts.account
          titanServerGroup.placement.region = keyParts.region
          titanServerGroup.application = keyParts.application
          titanServerGroup.name = keyParts.serverGroup
          titanServerGroup
        }
      }
      cluster
    }
    clusters
  }

  /**
   * Translate server groups
   */
  private Map<String, TitanServerGroup> translateServerGroups(Collection<CacheData> serverGroupData) {
    Collection<CacheData> allInstances = resolveRelationshipDataForCollection(serverGroupData, INSTANCES.ns, RelationshipCacheFilter.none())
    Map<String, TitanInstance> instances = translateInstances(allInstances)
    Map<String, TitanServerGroup> serverGroups = serverGroupData.collectEntries { serverGroupEntry ->
      String json = objectMapper.writeValueAsString(serverGroupEntry.attributes.job)
      Job job = objectMapper.readValue(json, Job)
      TitanServerGroup serverGroup = new TitanServerGroup(job)
      serverGroup.instances = serverGroupEntry.relationships[INSTANCES.ns]?.findResults { instances.get(it) } as Set
      [(serverGroupEntry.id) : serverGroup]
    }
    serverGroups
  }

  /**
   * Translate instances
   */
  private Map<String, TitanInstance> translateInstances(Collection<CacheData> instanceData) {
    Map<String, TitanInstance> instances = instanceData.collectEntries { instanceEntry ->
      String json = objectMapper.writeValueAsString(instanceEntry.attributes.task)
      Task task = objectMapper.readValue(json, Task)
      TitanInstance instance = new TitanInstance(task)
      instance.health = instanceEntry.attributes[HEALTH.ns]
      [(instanceEntry.id): instance]
    }

    // Adding health to instances
    Map<String, String> healthKeysToInstance = [:]
    instanceData.each { instanceEntry ->
      Map<String, String> instanceKey = Keys.parse(instanceEntry.id)
      titanCachingProvider.healthAgents.each {
        def key = Keys.getInstanceHealthKey(instanceKey.instanceId, instanceKey.account, instanceKey.region, it.healthId)
        healthKeysToInstance.put(key, instanceEntry.id)
      }
    }
    Collection<CacheData> healths = cacheView.getAll(HEALTH.ns, healthKeysToInstance.keySet(), RelationshipCacheFilter.none())
    healths.each { healthEntry ->
      def instanceId = healthKeysToInstance.get(healthEntry.id)
      instances[instanceId].health << healthEntry.attributes
    }

    instances
  }

  // Resolving cache data relationships

  private Collection<CacheData> resolveRelationshipDataForCollection(Collection<CacheData> sources, String relationship, CacheFilter cacheFilter = null) {
    Collection<String> relationships = sources.findResults { it.relationships[relationship]?: [] }.flatten()
    relationships ? cacheView.getAll(relationship, relationships, cacheFilter) : []
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship) {
    resolveRelationshipData(source, relationship) { true }
  }

  private Collection<CacheData> resolveRelationshipData(CacheData source, String relationship, Closure<Boolean> relFilter) {
    Collection<String> filteredRelationships = source.relationships[relationship]?.findAll(relFilter)
    filteredRelationships ? cacheView.getAll(relationship, filteredRelationships) : []
  }

}
