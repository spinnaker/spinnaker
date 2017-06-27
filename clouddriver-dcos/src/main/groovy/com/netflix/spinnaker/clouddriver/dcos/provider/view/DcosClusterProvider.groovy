/*
 * Copyright 2017 Cerner Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.clouddriver.dcos.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.frigga.Names
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.dcos.DcosCloudProvider
import com.netflix.spinnaker.clouddriver.dcos.cache.Keys
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.MarathonVolumeDeserializer
import com.netflix.spinnaker.clouddriver.dcos.deploy.util.id.DcosSpinnakerAppId
import com.netflix.spinnaker.clouddriver.dcos.model.DcosCluster
import com.netflix.spinnaker.clouddriver.dcos.model.DcosLoadBalancer
import com.netflix.spinnaker.clouddriver.dcos.model.DcosServerGroup
import com.netflix.spinnaker.clouddriver.dcos.provider.DcosProviderUtils
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import mesosphere.marathon.client.model.v2.Volume
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.dcos.provider.DcosProviderUtils.resolveRelationshipData
import static com.netflix.spinnaker.clouddriver.dcos.provider.DcosProviderUtils.resolveRelationshipDataForCollection

@Component
class DcosClusterProvider implements ClusterProvider<DcosCluster> {
  private final DcosCloudProvider dcosCloudProvider
  private final Cache cacheView
  private final ObjectMapper objectMapper

  @Autowired
  DcosClusterProvider(DcosCloudProvider dcosCloudProvider,
                      Cache cacheView,
                      ObjectMapper objectMapper) {
    this.dcosCloudProvider = dcosCloudProvider
    this.cacheView = cacheView

    // TODO should we be registering this at a higher level? Figured we wanted to isolate as much as possible.
    this.objectMapper = objectMapper.copy()
    DcosProviderUtils.registerDeserializer(this.objectMapper, Volume.class, new MarathonVolumeDeserializer())
  }

  @Override
  Map<String, Set<DcosCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(Keys.Namespace.CLUSTERS.ns)
    Collection<DcosCluster> clusters = translateClusters(clusterData, true)
    mapResponse(clusters)
  }

  @Override
  Map<String, Set<DcosCluster>> getClusterSummaries(String applicationName) {
    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    application ? mapResponse(translateClusters(resolveRelationshipData(cacheView, application, Keys.Namespace.CLUSTERS.ns), false)) : null
  }

  @Override
  Map<String, Set<DcosCluster>> getClusterDetails(String applicationName) {
    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    application ? mapResponse(translateClusters(resolveRelationshipData(cacheView, application, Keys.Namespace.CLUSTERS.ns), true)) : null
  }

  @Override
  Set<DcosCluster> getClusters(final String applicationName, final String account) {
    CacheData application = cacheView.get(Keys.Namespace.APPLICATIONS.ns, Keys.getApplicationKey(applicationName), RelationshipCacheFilter.include(Keys.Namespace.CLUSTERS.ns))
    if (!application) {
      return [] as Set
    }

    Collection<String> clusterKeys = application.relationships[Keys.Namespace.CLUSTERS.ns].findAll {
      Keys.parse(it).account == account
    }
    Collection<CacheData> clusters = cacheView.getAll(Keys.Namespace.CLUSTERS.ns, clusterKeys)
    translateClusters(clusters, true) as Set<DcosCluster>
  }

  @Override
  DcosCluster getCluster(final String application, final String account, final String name) {
    CacheData serverGroupCluster = cacheView.get(Keys.Namespace.CLUSTERS.ns, Keys.getClusterKey(account, application, name))
    List<CacheData> clusters = [serverGroupCluster] - null
    return clusters ? translateClusters(clusters, true).inject(new DcosCluster()) { DcosCluster acc, DcosCluster val ->
      acc.name = acc.name ?: val.name
      acc.accountName = acc.accountName ?: val.accountName
      acc.loadBalancers.addAll(val.loadBalancers)
      acc.serverGroups.addAll(val.serverGroups)
      return acc
    } : null
  }

  @Override
  ServerGroup getServerGroup(final String account, final String combinedRegion, final String name) {
    String region = combinedRegion.split(DcosSpinnakerAppId.SAFE_REGION_SEPARATOR).first()
    String group = combinedRegion.split(DcosSpinnakerAppId.SAFE_REGION_SEPARATOR).tail().join("/")
    String serverGroupKey = Keys.getServerGroupKey(DcosSpinnakerAppId.from(account, group, name).get(), region)
    CacheData serverGroupData = cacheView.get(Keys.Namespace.SERVER_GROUPS.ns, serverGroupKey)
    if (!serverGroupData) {
      return null
    }

    objectMapper.convertValue(serverGroupData.attributes.serverGroup, DcosServerGroup)
  }

  @Override
  String getCloudProviderId() {
    return dcosCloudProvider.id
  }

  private static Map<String, Set<DcosCluster>> mapResponse(Collection<DcosCluster> clusters) {
    clusters.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
  }


  def translateClusters(Collection<CacheData> clusterData, boolean includeDetails) {
    Map<String, DcosLoadBalancer> loadBalancers
    Map<String, Set<DcosServerGroup>> serverGroups

    if (includeDetails) {
      Collection<CacheData> allLoadBalancers = resolveRelationshipDataForCollection(cacheView, clusterData, Keys.Namespace.LOAD_BALANCERS.ns)
      Collection<CacheData> allServerGroups = resolveRelationshipDataForCollection(cacheView, clusterData, Keys.Namespace.SERVER_GROUPS.ns,
                                                                                   RelationshipCacheFilter.include(Keys.Namespace.INSTANCES.ns, Keys.Namespace.LOAD_BALANCERS.ns))
      loadBalancers = translateLoadBalancers(allLoadBalancers)
      serverGroups = translateServerGroups(allServerGroups)
    }

    Collection<DcosCluster> clusters = clusterData.collect {
      createDcosCluster(it, loadBalancers, serverGroups, includeDetails)
    }

    clusters
  }

  static def createDcosCluster(CacheData clusterDataEntry, Map<String, DcosLoadBalancer> loadBalancers,
                               Map<String, Set<DcosServerGroup>> serverGroups, boolean includeDetails) {
    Map<String, String> clusterKey = Keys.parse(clusterDataEntry.id)

    def cluster = new DcosCluster()
    cluster.accountName = clusterKey.account
    cluster.name = clusterKey.name
    if (includeDetails) {
      cluster.loadBalancers = clusterDataEntry.relationships[Keys.Namespace.LOAD_BALANCERS.ns]?.findResults {
        loadBalancers.get(it)
      }
      cluster.serverGroups = serverGroups[cluster.name]?.findAll { it.account == cluster.accountName } ?: []
    } else {
      cluster.loadBalancers = clusterDataEntry.relationships[Keys.Namespace.LOAD_BALANCERS.ns]?.collect { loadBalancerKey ->
        Map parts = Keys.parse(loadBalancerKey)
        new DcosLoadBalancer(parts.name, 'global', parts.account)
      }

      cluster.serverGroups = clusterDataEntry.relationships[Keys.Namespace.SERVER_GROUPS.ns]?.collect { serverGroupKey ->
        Map parts = Keys.parse(serverGroupKey)
        new DcosServerGroup(parts.name, parts.region, parts.group, parts.account)
      }
    }
    cluster
  }

  private static Map<String, DcosLoadBalancer> translateLoadBalancers(Collection<CacheData> loadBalancerData) {
    loadBalancerData.collectEntries { loadBalancerEntry ->
      Map<String, String> parts = Keys.parse(loadBalancerEntry.id)
      [(loadBalancerEntry.id): new DcosLoadBalancer(parts.name, 'global', parts.account)]
    }
  }

  private Map<String, Set<DcosServerGroup>> translateServerGroups(Collection<CacheData> serverGroupData) {
    Map<String, Set<DcosServerGroup>> serverGroups = [:].withDefault { _ -> [] as Set }
    serverGroupData.forEach { cacheData ->
      def serverGroup = objectMapper.convertValue(cacheData.attributes.serverGroup, DcosServerGroup)
      serverGroups[Names.parseName(serverGroup.name).cluster].add(serverGroup)
    }

    serverGroups
  }
}
