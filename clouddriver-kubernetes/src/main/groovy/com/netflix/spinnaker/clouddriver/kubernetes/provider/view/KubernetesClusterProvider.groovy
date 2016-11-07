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
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.cache.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.deploy.KubernetesUtil
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesCluster
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesInstance
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesLoadBalancer
import com.netflix.spinnaker.clouddriver.kubernetes.model.KubernetesServerGroup
import com.netflix.spinnaker.clouddriver.kubernetes.provider.KubernetesProvider
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import io.fabric8.kubernetes.api.model.ReplicationController
import io.fabric8.kubernetes.api.model.extensions.ReplicaSet
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import java.util.ArrayList

@Component
class KubernetesClusterProvider implements ClusterProvider<KubernetesCluster> {
  private final KubernetesCloudProvider kubernetesCloudProvider
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  KubernetesSecurityGroupProvider securityGroupProvider

  @Autowired
  KubernetesClusterProvider(KubernetesCloudProvider kubernetesCloudProvider,
                            Cache cacheView,
                            ObjectMapper objectMapper) {
    this.kubernetesCloudProvider = kubernetesCloudProvider
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
    CacheData serverGroupCluster = cacheView.get(Keys.Namespace.CLUSTERS.ns, Keys.getClusterKey(account, applicationName, "serverGroup", name))
    List<CacheData> clusters = [serverGroupCluster] - null
    return clusters ? translateClusters(clusters, true).inject(new KubernetesCluster()) { KubernetesCluster acc, KubernetesCluster val ->
      acc.name = acc.name ?: val.name
      acc.accountName = acc.accountName ?: val.accountName
      acc.loadBalancers.addAll(val.loadBalancers)
      acc.serverGroups.addAll(val.serverGroups)
      return acc
    } : null
  }

  static Collection<CacheData> resolveRelationshipDataForCollection(Cache cacheView, Collection<CacheData> sources, String relationship, CacheFilter cacheFilter = null) {
    Collection<String> relationships = sources?.findResults { it.relationships[relationship] ?: [] }?.flatten() ?: []
    relationships ? cacheView.getAll(relationship, relationships, cacheFilter) : []
  }

  static Map<String, Collection<CacheData>> preserveRelationshipDataForCollection(Cache cacheView, Collection<CacheData> sources, String relationship, CacheFilter cacheFilter = null) {
    Map<String, CacheData> allData = resolveRelationshipDataForCollection(cacheView, sources, relationship, cacheFilter).collectEntries { cacheData ->
      [(cacheData.id): cacheData]
    }

    return sources.collectEntries { CacheData source ->
      [(source.id): source.relationships[relationship].collect { String key -> allData[key] }]
    }
  }

  private Collection<KubernetesCluster> translateClusters(Collection<CacheData> clusterData, boolean includeDetails) {
    Map<String, KubernetesLoadBalancer> loadBalancers
    Map<String, Set<KubernetesServerGroup>> serverGroups

    if (includeDetails) {
      Collection<CacheData> allLoadBalancers = resolveRelationshipDataForCollection(cacheView, clusterData, Keys.Namespace.LOAD_BALANCERS.ns)
      Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(cacheView, clusterData, Keys.Namespace.SERVER_GROUPS.ns,
          RelationshipCacheFilter.include(Keys.Namespace.INSTANCES.ns, Keys.Namespace.LOAD_BALANCERS.ns))
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
        cluster.serverGroups = serverGroups[cluster.name]?.findAll { it.account == cluster.accountName } ?: []
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
    Collection<CacheData> allLoadBalancers = resolveRelationshipDataForCollection(cacheView, serverGroupData, Keys.Namespace.LOAD_BALANCERS.ns, RelationshipCacheFilter.include(Keys.Namespace.SECURITY_GROUPS.ns))
    def securityGroups = loadBalancerToSecurityGroupMap(securityGroupProvider, cacheView, allLoadBalancers)
    Map<String, Set<KubernetesInstance>> instances = [:]
    preserveRelationshipDataForCollection(cacheView, serverGroupData, Keys.Namespace.INSTANCES.ns, RelationshipCacheFilter.none()).each { key, cacheData ->
      instances[key] = cacheData.collect { it -> KubernetesProviderUtils.convertInstance(objectMapper, it) } as Set
    }

    Map<String, Set<KubernetesServerGroup>> serverGroups = [:].withDefault { _ -> [] as Set }
    serverGroupData.forEach { cacheData ->
      def serverGroup = KubernetesProviderUtils.serverGroupFromCacheData(objectMapper, cacheData, instances[cacheData.id])

      serverGroup.loadBalancers?.each {
        serverGroup.securityGroups.addAll(securityGroups[it])
      }

      def imageList = []
      for (def container : serverGroup.deployDescription.containers) {
        imageList.add(KubernetesUtil.getImageIdWithoutRegistry(container.imageDescription))
      }

      Map buildInfo = [images: imageList]
      serverGroup.buildInfo = buildInfo
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

  static loadBalancerToSecurityGroupMap(KubernetesSecurityGroupProvider securityGroupProvider, Cache cacheView, Collection<CacheData> loadBalancers) {
    Collection<CacheData> allSecurityGroups = resolveRelationshipDataForCollection(cacheView, loadBalancers, Keys.Namespace.SECURITY_GROUPS.ns, RelationshipCacheFilter.none())

    Map<String, Set<String>> securityGroups = [:].withDefault { _ -> [] as Set }
    allSecurityGroups.each { securityGroup ->
      def translated = securityGroupProvider.translateSecurityGroup(securityGroup, true)

      translated.loadBalancers.each {
        securityGroups[it].add(translated.id)
      }
    }

    return securityGroups
  }

  @Override
  ServerGroup getServerGroup(String account, String namespace, String name) {
    String serverGroupKey = Keys.getServerGroupKey(account, namespace, name)
    CacheData serverGroupData = cacheView.get(Keys.Namespace.SERVER_GROUPS.ns, serverGroupKey)
    if (!serverGroupData) {
      return null
    }

    Collection<CacheData> allLoadBalancers = resolveRelationshipDataForCollection(cacheView, [serverGroupData], Keys.Namespace.LOAD_BALANCERS.ns, RelationshipCacheFilter.include(Keys.Namespace.SECURITY_GROUPS.ns))
    Set<KubernetesInstance> instances = resolveRelationshipDataForCollection(cacheView, [serverGroupData], Keys.Namespace.INSTANCES.ns, RelationshipCacheFilter.none()).collect {
      KubernetesProviderUtils.convertInstance(objectMapper, it)
    } as Set

    def securityGroups = loadBalancerToSecurityGroupMap(securityGroupProvider, cacheView, allLoadBalancers)

    def serverGroup = KubernetesProviderUtils.serverGroupFromCacheData(objectMapper, serverGroupData, instances)

    serverGroup.loadBalancers?.each {
      serverGroup.securityGroups.addAll(securityGroups[it])
    }

    return serverGroup
  }

  @Override
  String getCloudProviderId() {
    return kubernetesCloudProvider.id
  }
}
