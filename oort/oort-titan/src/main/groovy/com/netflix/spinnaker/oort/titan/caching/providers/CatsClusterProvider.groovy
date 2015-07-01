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

import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.oort.aws.data.Keys
import com.netflix.spinnaker.oort.model.ClusterProvider
import com.netflix.spinnaker.oort.titan.caching.TitanCachingProvider
import com.netflix.spinnaker.oort.titan.model.TitanCluster
import com.netflix.spinnaker.oort.titan.model.TitanInstance
import com.netflix.spinnaker.oort.titan.model.TitanServerGroup
import com.netflix.titanclient.model.Job
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.oort.aws.data.Keys.Namespace.*

@Component
class CatsClusterProvider implements ClusterProvider<TitanCluster> {

  private final Cache cacheView
  private final TitanCachingProvider titanProvider

  @Value('${default.build.host:http://builds.netflix.com/}')
  String defaultBuildHost

  @Autowired
  CatsClusterProvider(Cache cacheView, TitanCachingProvider titanProvider) {
    this.cacheView = cacheView
    this.titanProvider = titanProvider
  }

  @Override
  Map<String, Set<TitanCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns)
    Collection<TitanCluster> clusters = translateClusters(clusterData, false)
    mapResponse(clusters)
  }

  @Override
  Map<String, Set<TitanCluster>> getClusterSummaries(String applicationName) {
    getClusters0(applicationName, false)
  }

  @Override
  Map<String, Set<TitanCluster>> getClusterDetails(String applicationName) {
    getClusters0(applicationName, true)
  }

  @Override
  Set<TitanCluster> getClusters(String applicationName, String account) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName), RelationshipCacheFilter.include(CLUSTERS.ns))
    if (application == null) {
      return [] as Set
    }
    Collection<String> clusterKeys = application.relationships[CLUSTERS.ns].findAll { Keys.parse(it).account == account }
    Collection<CacheData> clusters = cacheView.getAll(CLUSTERS.ns, clusterKeys)
    translateClusters(clusters, true) as Set<TitanCluster>
  }

  @Override
  TitanCluster getCluster(String application, String account, String name) {
    CacheData cluster = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(name, application, account))
    if (cluster == null) {
      null
    } else {
      translateClusters([cluster], true)[0]
    }
  }

  @Override
  TitanServerGroup getServerGroup(String account, String region, String name) {
    String serverGroupKey = Keys.getServerGroupKey(name, account, region)
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey)
    if (serverGroupData == null) {
      return null
    }
    Job job = serverGroupData.attributes.job
    TitanServerGroup serverGroup = new TitanServerGroup(job)
    serverGroup.account = account
    serverGroup.instances = translateInstances(resolveRelationshipData(serverGroupData, INSTANCES.ns)).values()
    serverGroup
  }

  private static Map<String, Set<TitanCluster>> mapResponse(Collection<TitanCluster> clusters) {
    clusters.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
  }

  private Collection<TitanCluster> translateClusters(Collection<CacheData> clusterData, boolean includeDetails) {
    Map<String, TitanServerGroup> serverGroups
    if (includeDetails) {
      Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(clusterData, SERVER_GROUPS.ns, RelationshipCacheFilter.include(INSTANCES.ns))
      serverGroups = translateServerGroups(allServerGroups)
    }
    Collection<TitanCluster> clusters = clusterData.collect { CacheData clusterDataEntry ->
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.id)

      def cluster = new TitanCluster()
      cluster.accountName = clusterKey.account
      cluster.name = clusterKey.cluster
      if (includeDetails) {
        cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns]?.findResults { serverGroups.get(it) }
      } else {
        cluster.serverGroups = clusterDataEntry.relationships[SERVER_GROUPS.ns]?.collect { serverGroupKey ->
          new TitanServerGroup(clusterDataEntry.attributes.job)
        }
      }
      cluster
    }
    clusters
  }

  private Map<String, Set<TitanCluster>> getClusters0(String applicationName, boolean includeDetails) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    if (application == null) {
      return null
    }
    Collection<TitanCluster> clusters = translateClusters(resolveRelationshipData(application, CLUSTERS.ns), includeDetails)
    mapResponse(clusters)
  }

  private Map<String, TitanServerGroup> translateServerGroups(Collection<CacheData> serverGroupData) {
    Collection<CacheData> allInstances = resolveRelationshipDataForCollection(serverGroupData, INSTANCES.ns, RelationshipCacheFilter.none())
    Map<String, TitanInstance> instances = translateInstances(allInstances)
    Map<String, TitanServerGroup> serverGroups = serverGroupData.collectEntries { serverGroupEntry ->
      def serverGroup = new TitanServerGroup(serverGroupEntry.attributes.job)
      serverGroup.instances = serverGroupEntry.relationships[INSTANCES.ns]?.findResults { instances.get(it) }
      [(serverGroupEntry.id) : serverGroup]
    }
    serverGroups
  }

  private Map<String, TitanInstance> translateInstances(Collection<CacheData> instanceData) {
    Map<String, TitanInstance> instances = instanceData.collectEntries { instanceEntry ->
      TitanInstance instance = new TitanInstance(instanceEntry.attributes.task)
      [(instanceEntry.id): instance]
    }
    addHealthToInstances(instanceData, instances)
    instances
  }

  private void addHealthToInstances(Collection<CacheData> instanceData, Map<String, TitanInstance> instances) {
    Map<String, String> healthKeysToInstance = [:]
    instanceData.each { instanceEntry ->
      Map<String, String> instanceKey = Keys.parse(instanceEntry.id)
      titanProvider.healthAgents.each {
        def key = Keys.getInstanceHealthKey(instanceKey.instanceId, instanceKey.account, instanceKey.region, it.healthId)
        healthKeysToInstance.put(key, instanceEntry.id)
      }
    }
    Collection<CacheData> healths = cacheView.getAll(HEALTH.ns, healthKeysToInstance.keySet(), RelationshipCacheFilter.none())
    healths.each { healthEntry ->
      def instanceId = healthKeysToInstance.get(healthEntry.id)
      instances[instanceId].health << healthEntry.attributes
    }
    instances.values().each { instance ->
      instance.isHealthy = instance.health.any { it.state == 'Up' } && instance.health.every { it.state == 'Up' || it.state == 'Unknown' }
    }
  }

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
