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

package com.netflix.spinnaker.clouddriver.kubernetes.v1.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.kubernetes.KubernetesCloudProvider
import com.netflix.spinnaker.clouddriver.kubernetes.v1.caching.Keys
import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesV1Cluster
import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesV1Instance
import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesV1LoadBalancer
import com.netflix.spinnaker.clouddriver.kubernetes.v1.model.KubernetesV1ServerGroup
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import io.fabric8.kubernetes.api.model.apps.Deployment
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class KubernetesV1ClusterProvider implements ClusterProvider<KubernetesV1Cluster> {
  private final KubernetesCloudProvider kubernetesCloudProvider
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  KubernetesV1SecurityGroupProvider securityGroupProvider

  @Autowired
  KubernetesV1ClusterProvider(KubernetesCloudProvider kubernetesCloudProvider,
                              Cache cacheView,
                              ObjectMapper objectMapper) {
    this.kubernetesCloudProvider = kubernetesCloudProvider
    this.cacheView = cacheView
    this.objectMapper = objectMapper
  }

  @Override
  Set<KubernetesV1Cluster> getClusters(String applicationName, String account) {
    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.ns, Keys.getApplicationKey(applicationName), RelationshipCacheFilter.include(Keys.Namespace.CLUSTERS.ns))
    if (!application) {
      return [] as Set
    }

    Collection<String> clusterKeys = application.relationships[Keys.Namespace.CLUSTERS.ns].findAll { Keys.parse(it).account == account }
    Collection<CacheData> clusters = cacheView.getAll(Keys.Namespace.CLUSTERS.ns, clusterKeys)
    translateClusters(clusters, true) as Set<KubernetesV1Cluster>
  }

  @Override
  Map<String, Set<KubernetesV1Cluster>> getClusterSummaries(String applicationName) {
    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    application ? mapResponse(translateClusters(KubernetesProviderUtils.resolveRelationshipData(cacheView, application, Keys.Namespace.CLUSTERS.ns), false)) : null
  }

  @Override
  Map<String, Set<KubernetesV1Cluster>> getClusterDetails(String applicationName) {
    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    application ? mapResponse(translateClusters(KubernetesProviderUtils.resolveRelationshipData(cacheView, application, Keys.Namespace.CLUSTERS.ns), true)) : null
  }

  @Override
  KubernetesV1Cluster getCluster(String application, String account, String name, boolean includeDetails) {
    CacheData serverGroupCluster = cacheView.get(Keys.Namespace.CLUSTERS.ns, Keys.getClusterKey(account, application, "serverGroup", name))
    List<CacheData> clusters = [serverGroupCluster] - null
    return clusters ? translateClusters(clusters, includeDetails).inject(new KubernetesV1Cluster()) { KubernetesV1Cluster acc, KubernetesV1Cluster val ->
      acc.name = acc.name ?: val.name
      acc.accountName = acc.accountName ?: val.accountName
      acc.loadBalancers.addAll(val.loadBalancers)
      acc.serverGroups.addAll(val.serverGroups)
      return acc
    } : null
  }

  @Override
  KubernetesV1Cluster getCluster(String applicationName, String account, String name) {
    return getCluster(applicationName, account, name, true)
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
      [(source.id): source.relationships[relationship].collect { String key -> allData[key] } - null]
    }
  }

  private Collection<KubernetesV1Cluster> translateClusters(Collection<CacheData> clusterData, boolean includeDetails) {
    Map<String, KubernetesV1LoadBalancer> loadBalancers
    Map<String, Set<KubernetesV1ServerGroup>> serverGroups

    if (includeDetails) {
      Collection<CacheData> allLoadBalancers = resolveRelationshipDataForCollection(cacheView, clusterData, Keys.Namespace.LOAD_BALANCERS.ns)
      Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(cacheView, clusterData, Keys.Namespace.SERVER_GROUPS.ns,
          RelationshipCacheFilter.include(Keys.Namespace.INSTANCES.ns, Keys.Namespace.LOAD_BALANCERS.ns, Keys.Namespace.DEPLOYMENTS.ns))
      loadBalancers = translateLoadBalancers(allLoadBalancers)
      serverGroups = translateServerGroups(allServerGroups)
    }

    Collection<KubernetesV1Cluster> clusters = clusterData.collect { CacheData clusterDataEntry ->
      Map<String, String> clusterKey = Keys.parse(clusterDataEntry.id)

      def cluster = new KubernetesV1Cluster()
      cluster.accountName = clusterKey.account
      cluster.name = clusterKey.name
      if (includeDetails) {
        cluster.loadBalancers = clusterDataEntry.relationships[Keys.Namespace.LOAD_BALANCERS.ns]?.findResults { loadBalancers.get(it) } ?: []
        cluster.serverGroups = serverGroups[cluster.name]?.findAll { it.account == cluster.accountName } ?: []
      } else {
        cluster.loadBalancers = clusterDataEntry.relationships[Keys.Namespace.LOAD_BALANCERS.ns]?.collect { loadBalancerKey ->
          Map parts = Keys.parse(loadBalancerKey)
          new KubernetesV1LoadBalancer(parts.name, parts.namespace, parts.account)
        }

        cluster.serverGroups = clusterDataEntry.relationships[Keys.Namespace.SERVER_GROUPS.ns]?.collect { serverGroupKey ->
          Map parts = Keys.parse(serverGroupKey)
          new KubernetesV1ServerGroup(parts.name, parts.namespace)
        }
      }
      cluster
    }

    clusters
  }

  private Map<String, Set<KubernetesV1ServerGroup>> translateServerGroups(Collection<CacheData> serverGroupData) {
    Collection<CacheData> allLoadBalancers = resolveRelationshipDataForCollection(cacheView, serverGroupData, Keys.Namespace.LOAD_BALANCERS.ns, RelationshipCacheFilter.include(Keys.Namespace.SECURITY_GROUPS.ns))
    def securityGroups = loadBalancerToSecurityGroupMap(securityGroupProvider, cacheView, allLoadBalancers)
    Map<String, Set<KubernetesV1Instance>> instances = [:]
    preserveRelationshipDataForCollection(cacheView, serverGroupData, Keys.Namespace.INSTANCES.ns, RelationshipCacheFilter.none()).each { key, cacheData ->
      instances[key] = cacheData.collect { it -> KubernetesProviderUtils.convertInstance(objectMapper, it) } as Set
    }
    Map<String, Deployment> deployments = [:]
    preserveRelationshipDataForCollection(cacheView, serverGroupData, Keys.Namespace.DEPLOYMENTS.ns, RelationshipCacheFilter.none()).each { key, cacheData ->
      deployments[key] = cacheData.collect { it -> objectMapper.convertValue(it.attributes.deployment, Deployment.class) }[0]
    }

    Map<String, Set<KubernetesV1ServerGroup>> serverGroups = [:].withDefault { _ -> [] as Set }
    serverGroupData.forEach { cacheData ->
      def serverGroup = KubernetesProviderUtils.serverGroupFromCacheData(objectMapper, cacheData, instances[cacheData.id], deployments[cacheData.id])

      serverGroup.loadBalancers?.each {
        serverGroup.securityGroups.addAll(securityGroups[it])
      }

      serverGroups[Names.parseName(serverGroup.name).cluster].add(serverGroup)
    }

    serverGroups
  }

  private static Map<String, KubernetesV1LoadBalancer> translateLoadBalancers(Collection<CacheData> loadBalancerData) {
    loadBalancerData.collectEntries { loadBalancerEntry ->
      Map<String, String> parts = Keys.parse(loadBalancerEntry.id)
      [(loadBalancerEntry.id) : new KubernetesV1LoadBalancer(parts.name, parts.namespace, parts.account)]
    }
  }

  @Override
  Map<String, Set<KubernetesV1Cluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(Keys.Namespace.CLUSTERS.ns)
    Collection<KubernetesV1Cluster> clusters = translateClusters(clusterData, true)
    mapResponse(clusters)
  }

  private static Map<String, Set<KubernetesV1Cluster>> mapResponse(Collection<KubernetesV1Cluster> clusters) {
    clusters.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
  }

  static loadBalancerToSecurityGroupMap(KubernetesV1SecurityGroupProvider securityGroupProvider, Cache cacheView, Collection<CacheData> loadBalancers) {
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
  ServerGroup getServerGroup(String account, String namespace, String name, boolean includeDetails) {
    String serverGroupKey = Keys.getServerGroupKey(account, namespace, name)
    CacheData serverGroupData = cacheView.get(Keys.Namespace.SERVER_GROUPS.ns, serverGroupKey)
    if (!serverGroupData) {
      return null
    }

    Collection<CacheData> allLoadBalancers = resolveRelationshipDataForCollection(cacheView, [serverGroupData], Keys.Namespace.LOAD_BALANCERS.ns, RelationshipCacheFilter.include(Keys.Namespace.SECURITY_GROUPS.ns))
    Deployment deployment = resolveRelationshipDataForCollection(cacheView, [serverGroupData], Keys.Namespace.DEPLOYMENTS.ns, RelationshipCacheFilter.none()).collect { cacheData ->
      objectMapper.convertValue(cacheData.attributes.deployment, Deployment.class)
    }[0]
    Set<KubernetesV1Instance> instances = resolveRelationshipDataForCollection(cacheView, [serverGroupData], Keys.Namespace.INSTANCES.ns, RelationshipCacheFilter.none()).collect {
      KubernetesProviderUtils.convertInstance(objectMapper, it)
    } as Set

    def securityGroups = loadBalancerToSecurityGroupMap(securityGroupProvider, cacheView, allLoadBalancers)

    def serverGroup = KubernetesProviderUtils.serverGroupFromCacheData(objectMapper, serverGroupData, instances, deployment)

    serverGroup.loadBalancers?.each {
      serverGroup.securityGroups.addAll(securityGroups[it])
    }

    return serverGroup
  }

  @Override
  ServerGroup getServerGroup(String account, String namespace, String name) {
    return getServerGroup(account, namespace, name, true)
  }

  @Override
  String getCloudProviderId() {
    return kubernetesCloudProvider.id
  }

  @Override
  boolean supportsMinimalClusters() {
    return false
  }
}
