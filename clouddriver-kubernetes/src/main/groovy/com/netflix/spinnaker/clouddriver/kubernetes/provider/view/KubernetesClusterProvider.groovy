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

package com.netflix.spinnaker.clouddriver.kubernetes.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesCluster
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesInstance
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesLoadBalancer
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesServerGroup
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import io.fabric8.kubernetes.api.model.Pod
import io.fabric8.kubernetes.api.model.ReplicationController
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class KubernetesClusterProvider implements ClusterProvider<KubernetesCluster> {
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  KubernetesClusterProvider(Cache cacheView, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<KubernetesCluster> getClusters(String applicationName, String account) {
    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.ns, Keys.getApplicationKey(applicationName), RelationshipCacheFilter.include(Keys.Namespace.CLUSTERS.ns))
    if (!application) {
      return [] as Set
    }

    Collection<String> clusterKeys = application.relationships[Keys.Namespace.CLUSTERS.ns].findAll { Keys.parse(it).account == account }
    Collection<CacheData> clusters = cacheView.getAll(Keys.Namespace.CLUSTERS.ns, clusterKeys)
    translateClusters(clusters, true) as Set<KubernetesCluster>
  }

  @Override
  Map<String, Set<KubernetesCluster>> getClusterSummaries(String applicationName) {
    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    application ? mapResponse(translateClusters(KubernetesProviderUtils.resolveRelationshipData(cacheView, application, Keys.Namespace.CLUSTERS.ns), false)) : null
  }

  @Override
  Map<String, Set<KubernetesCluster>> getClusterDetails(String applicationName) {
    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    application ? mapResponse(translateClusters(KubernetesProviderUtils.resolveRelationshipData(cacheView, application, Keys.Namespace.CLUSTERS.ns), true)) : null
  }

  @Override
  KubernetesCluster getCluster(String applicationName, String account, String name) {
    CacheData cluster = cacheView.get(Keys.Namespace.CLUSTERS.ns, Keys.getClusterKey(account, applicationName, name))
    cluster ? translateClusters([cluster], true)[0] : null
  }

  private Collection<CacheData> resolveRelationshipDataForCollection(Collection<CacheData> sources, String relationship, CacheFilter cacheFilter = null) {
    Collection<String> relationships = sources?.findResults { it.relationships[relationship]?: [] }?.flatten() ?: []
    relationships ? cacheView.getAll(relationship, relationships, cacheFilter) : []
  }

  private Collection<KubernetesCluster> translateClusters(Collection<CacheData> clusterData, boolean includeDetails) {
    Map<String, KubernetesLoadBalancer> loadBalancers
    Map<String, Set<KubernetesServerGroup>> serverGroups

    if (includeDetails) {
      // TODO(lwander): this is unnecessary, and should only query the relevant load balancers/server groups
      Collection<CacheData> allLoadBalancers = resolveRelationshipDataForCollection(clusterData, Keys.Namespace.LOAD_BALANCERS.ns)
      Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(clusterData, Keys.Namespace.SERVER_GROUPS.ns, RelationshipCacheFilter.include(Keys.Namespace.INSTANCES.ns))

      loadBalancers = translateLoadBalancers(allLoadBalancers)
      serverGroups = translateServerGroups(allServerGroups)
    }

    Collection<KubernetesCluster> clusters = clusterData.collect { CacheData clusterDataEntry ->
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.id)

      def cluster = new KubernetesCluster()
      cluster.accountName = clusterKey.account
      cluster.name = clusterKey.name
      if (includeDetails) {
        cluster.loadBalancers = clusterDataEntry.relationships[Keys.Namespace.LOAD_BALANCERS.ns]?.findResults { loadBalancers.get(it) }
        cluster.serverGroups = serverGroups.get(cluster.name)
      } else {
        cluster.loadBalancers = clusterDataEntry.relationships[Keys.Namespace.LOAD_BALANCERS.ns]?.collect { loadBalancerKey ->
          Map parts = Keys.parse(loadBalancerKey)
          new KubernetesLoadBalancer(parts.name, parts.namespace, parts.account)
        }

        cluster.serverGroups = clusterDataEntry.relationships[Keys.Namespace.SERVER_GROUPS.ns]?.collect { serverGroupKey ->
          Map parts = Keys.parse(serverGroupKey)
          new KubernetesServerGroup(parts.name, parts.namespace)
        }
      }
      cluster
    }

    clusters
  }

  private Map<String, Set<KubernetesServerGroup>> translateServerGroups(Collection<CacheData> serverGroupData) {
    Collection<CacheData> allInstances = resolveRelationshipDataForCollection(serverGroupData, Keys.Namespace.INSTANCES.ns, RelationshipCacheFilter.none())

    Map<String, Set<KubernetesInstance>> instances = KubernetesProviderUtils.serverGroupToInstanceMap(objectMapper, allInstances)

    Map<String, Set<KubernetesServerGroup>> serverGroups = [:].withDefault { _ -> [] as Set }
    serverGroupData.forEach {
      def replicationController = objectMapper.convertValue(it.attributes.replicationController, ReplicationController)
      def serverGroup = new KubernetesServerGroup(replicationController, instances[(String) it.attributes.name])
      serverGroups[Names.parseName(serverGroup.name).cluster].add(serverGroup)
    }

    serverGroups
  }

  private static Map<String, KubernetesLoadBalancer> translateLoadBalancers(Collection<CacheData> loadBalancerData) {
    loadBalancerData.collectEntries { loadBalancerEntry ->
      Map<String, String> parts = Keys.parse(loadBalancerEntry.id)
      [(loadBalancerEntry.id) : new KubernetesLoadBalancer(parts.name, parts.namespace, parts.account)]
    }
  }

  @Override
  Map<String, Set<KubernetesCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(Keys.Namespace.CLUSTERS.ns)
    Collection<KubernetesCluster> clusters = translateClusters(clusterData, true)
    mapResponse(clusters)
  }

  private static Map<String, Set<KubernetesCluster>> mapResponse(Collection<KubernetesCluster> clusters) {
    clusters.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
  }

  @Override
  ServerGroup getServerGroup(String account, String namespace, String name) {
    String serverGroupKey = Keys.getServerGroupKey(account, namespace, name)
    CacheData serverGroupData = cacheView.get(Keys.Namespace.SERVER_GROUPS.ns, serverGroupKey)
    if (!serverGroupData) {
      return null
    }

    Set<CacheData> instances = KubernetesProviderUtils.getAllMatchingKeyPattern(cacheView, Keys.Namespace.INSTANCES.ns, Keys.getInstanceKey(account, namespace, name, "*"))

    def replicationController = objectMapper.convertValue(serverGroupData.attributes.replicationController, ReplicationController)
    serverGroupData ? new KubernetesServerGroup(replicationController, KubernetesProviderUtils.serverGroupToInstanceMap(objectMapper, instances)[name]) : null
  }
}
