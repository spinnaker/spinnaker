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

package com.netflix.spinnaker.clouddriver.azure.resources.cluster.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.CacheFilter
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.azure.AzureCloudProvider
import com.netflix.spinnaker.clouddriver.azure.resources.application.model.AzureApplication
import com.netflix.spinnaker.clouddriver.azure.resources.application.view.AzureApplicationProvider
import com.netflix.spinnaker.clouddriver.azure.resources.cluster.model.AzureCluster
import com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys
import static com.netflix.spinnaker.clouddriver.azure.resources.common.cache.Keys.Namespace.*
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureServerGroupDescription
import com.netflix.spinnaker.clouddriver.azure.resources.loadbalancer.model.AzureLoadBalancer
import com.netflix.spinnaker.clouddriver.azure.resources.servergroup.model.AzureInstance
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component



@Component
class AzureClusterProvider implements ClusterProvider<AzureCluster> {

  @Autowired
  AzureCloudProvider azureCloudProvider

  @Autowired
  AzureApplicationProvider applicationProvider

  @Autowired
  Cache cacheView

  @Autowired
  ObjectMapper objectMapper

  @Override
  Set<AzureCluster> getClusters(String applicationName, String account) {
    AzureApplication azureApplication = applicationProvider.getApplication(applicationName)

    if (!azureApplication) {
      return [] as Set
    }

    def clusterKeys = []
    azureApplication.clusterNames.each { String accountName, Set<String> clusterNames ->
      clusterNames.each { String clusterName ->
        clusterKeys << Keys.getClusterKey(azureCloudProvider, azureApplication.name, clusterName, accountName)
      }
    }

    def clusters = cacheView.getAll(AZURE_CLUSTERS.ns, clusterKeys)

    translateClusters(clusters, true) as Set<AzureCluster>
  }

  @Override
  Map<String, Set<AzureCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(AZURE_CLUSTERS.ns)
    Collection<AzureCluster> clusters = translateClusters(clusterData, true)
    mapResponse(clusters)

  }

  @Override
  Map<String, Set<AzureCluster>> getClusterSummaries(String applicationName) {
    getClusters(applicationName, false)
  }

  @Override
  Map<String, Set<AzureCluster>> getClusterDetails(String applicationName) {
    getClusters(applicationName, true)
  }

  Map<String, Set<AzureCluster>> getClusters(String applicationName, Boolean includeInstanceDetails) {
    CacheData appData = cacheView.get(AZURE_APPLICATIONS.ns, Keys.getApplicationKey(azureCloudProvider, applicationName))
    if (!appData) {
      return [:] as Map
    }

    Collection<AzureCluster> clusters = translateClusters(resolveRelationshipData(appData, AZURE_CLUSTERS.ns), includeInstanceDetails)
    mapResponse(clusters)
  }

  @Override
  AzureCluster getCluster(String applicationName, String account, String name) {
    CacheData cluster = cacheView.get(AZURE_CLUSTERS.ns, Keys.getClusterKey(azureCloudProvider, applicationName, name, account, ))
    cluster ? translateClusters([cluster], true)[0] : null
  }

  @Override
  AzureServerGroupDescription getServerGroup(String account, String region, String name) {
    String serverGroupKey = Keys.getServerGroupKey(AzureCloudProvider.AZURE, name, region, account)
    CacheData serverGroupData = cacheView.get(AZURE_SERVER_GROUPS.ns, serverGroupKey)

    if (!serverGroupData) {
      return null
    }

    translateServerGroup(serverGroupData)
  }

  private Collection<AzureCluster> translateClusters(Collection<CacheData> clusterData, boolean includeDetails) {
    Map<String, AzureLoadBalancer> loadBalancers
    Map<String, AzureServerGroupDescription> serverGroups

    if (includeDetails) {
      Collection<CacheData> allLoadBalancers = resolveRelationshipDataForCollection(clusterData, AZURE_APP_GATEWAYS.ns)
      Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(clusterData, AZURE_SERVER_GROUPS.ns, RelationshipCacheFilter.include(AZURE_INSTANCES.ns))

      loadBalancers = translateLoadBalancers(allLoadBalancers)
      serverGroups = translateServerGroups(allServerGroups)
    }

    Collection<AzureCluster> clusters = clusterData.collect { CacheData clusterDataEntry ->
      Map<String, String> clusterKey = Keys.parse(azureCloudProvider, clusterDataEntry.id)

      def cluster = new AzureCluster()
      cluster.accountName = clusterKey.account
      cluster.name = clusterKey.name
      if (includeDetails) {
        cluster.loadBalancers = clusterDataEntry.relationships[AZURE_APP_GATEWAYS.ns]?.findResults {
          loadBalancers.get(it)
        }
        cluster.serverGroups = clusterDataEntry.relationships[AZURE_SERVER_GROUPS.ns]?.findResults {
          serverGroups.get(it)
        }
      } else {
        cluster.loadBalancers = clusterDataEntry.relationships[AZURE_APP_GATEWAYS.ns]?.collect { loadBalancerKey ->
          Map parts = Keys.parse(azureCloudProvider, loadBalancerKey)
          new AzureLoadBalancer(name: parts.name, region: parts.region, account: parts.account)
        }

        cluster.serverGroups = clusterDataEntry.relationships[AZURE_SERVER_GROUPS.ns]?.collect { serverGroupKey ->
          Map parts = Keys.parse(azureCloudProvider, serverGroupKey)
          new AzureServerGroupDescription(name: parts.serverGroup, region: parts.region,
            application: parts.application, appName: parts.application)
        }
      }
      cluster
    }

    clusters
  }

  private Map<String, AzureLoadBalancer> translateLoadBalancers(Collection<CacheData> loadBalancerData) {
    loadBalancerData.collectEntries { loadBalancerEntry ->
      Map<String, String> lbKey = Keys.parse(azureCloudProvider, loadBalancerEntry.id)
      [(loadBalancerEntry.id) : new AzureLoadBalancer(name: lbKey.name, account: lbKey.account, region: lbKey.region)]
    }
  }

  private Map<String, AzureServerGroupDescription> translateServerGroups(Collection<CacheData> serverGroupData) {
    serverGroupData.collectEntries { serverGroupEntry ->
      def serverGroup = translateServerGroup(serverGroupEntry)
      [(serverGroupEntry.id): serverGroup]
    }
  }

  private AzureServerGroupDescription translateServerGroup(CacheData serverGroupData) {
    def serverGroup = objectMapper.convertValue(serverGroupData.attributes.serverGroup, AzureServerGroupDescription)
    def instances = resolveRelationshipData(serverGroupData, AZURE_INSTANCES.ns)
    serverGroup.instances = translateInstances(instances)
    serverGroup
  }

  private static Set<AzureInstance> translateInstances(Collection<CacheData> instanceData) {
    def instances = instanceData?.collect { instanceEntry ->
        new AzureInstance(instanceEntry.attributes.instance)
    } ?: []
    // TODO (scotm) add health info to instances
    instances as Set
  }

  private static Map<String, Set<AzureCluster>> mapResponse(Collection<AzureCluster> clusters) {
    clusters.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
  }

  private Collection<CacheData> resolveRelationshipDataForCollection(Collection<CacheData> sources, String relationship, CacheFilter cacheFilter = null) {
    Collection<String> relationships = sources?.findResults { it.relationships[relationship]?: [] }?.flatten() ?: []
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
