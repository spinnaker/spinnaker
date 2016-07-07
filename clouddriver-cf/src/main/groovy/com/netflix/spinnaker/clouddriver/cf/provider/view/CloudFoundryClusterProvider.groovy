/*
 * Copyright 2016 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.cf.provider.view
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.cf.cache.CacheUtils
import com.netflix.spinnaker.clouddriver.cf.cache.Keys
import com.netflix.spinnaker.clouddriver.cf.model.CloudFoundryCluster
import com.netflix.spinnaker.clouddriver.cf.provider.CloudFoundryProvider
import com.netflix.spinnaker.clouddriver.cf.provider.ProviderUtils
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.cf.cache.Keys.Namespace.*

@Component
class CloudFoundryClusterProvider implements ClusterProvider<CloudFoundryCluster> {

  private final Cache cacheView
  private final CloudFoundryProvider cloudFoundryProvider
  private final ObjectMapper objectMapper


  @Autowired
  CloudFoundryClusterProvider(Cache cacheView, CloudFoundryProvider cloudFoundryProvider, ObjectMapper objectMapper) {
    this.cacheView = cacheView
    this.cloudFoundryProvider = cloudFoundryProvider
    this.objectMapper = objectMapper
  }

  @Override
  Map<String, Set<CloudFoundryCluster>> getClusters() {
    Collection<CacheData> clusterData = cacheView.getAll(CLUSTERS.ns)
    Collection<CloudFoundryCluster> clusters = CacheUtils.translateClusters(cacheView, clusterData, false)
    mapResponse(clusters)
  }

  @Override
  Map<String, Set<CloudFoundryCluster>> getClusterSummaries(String applicationName) {
    getClusters0(applicationName, false)
  }

  @Override
  Map<String, Set<CloudFoundryCluster>> getClusterDetails(String applicationName) {
    getClusters0(applicationName, true)
  }

  @Override
  Set<CloudFoundryCluster> getClusters(String applicationName, String account) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName), RelationshipCacheFilter.include(CLUSTERS.ns))
    if (application == null) {
      return [] as Set
    }
    Collection<String> clusterKeys = application.relationships[CLUSTERS.ns].findAll { Keys.parse(it).account == account }
    Collection<CacheData> clusters = cacheView.getAll(CLUSTERS.ns, clusterKeys)
    CacheUtils.translateClusters(cacheView, clusters, true) as Set<CloudFoundryCluster>
  }

  @Override
  CloudFoundryCluster getCluster(String applicationName, String account, String name) {
    CacheData cluster = cacheView.get(CLUSTERS.ns, Keys.getClusterKey(name, applicationName, account))
    if (cluster == null) {
      null
    } else {
      CacheUtils.translateClusters(cacheView, [cluster], true)[0]
    }
  }

  @Override
  ServerGroup getServerGroup(String account, String region, String name) {
    String serverGroupKey = Keys.getServerGroupKey(name, account, region)
    CacheData serverGroupData = cacheView.get(SERVER_GROUPS.ns, serverGroupKey)
    if (serverGroupData == null) {
      return null
    }

    CacheUtils.translateServerGroup(serverGroupData,
        CacheUtils.translateInstances(cacheView, ProviderUtils.resolveRelationshipData(cacheView, serverGroupData, INSTANCES.ns)).values(),
        CacheUtils.translateLoadBalancers(ProviderUtils.resolveRelationshipData(cacheView, serverGroupData, LOAD_BALANCERS.ns)).values())
  }

  private Map<String, Set<CloudFoundryCluster>> getClusters0(String applicationName, boolean includeDetails) {
    CacheData application = cacheView.get(APPLICATIONS.ns, Keys.getApplicationKey(applicationName))
    if (application == null) {
      return null
    }

    Collection<CacheData> clusterData = ProviderUtils.resolveRelationshipData(cacheView, application, CLUSTERS.ns)
    Collection<CloudFoundryCluster> clusters = CacheUtils.translateClusters(cacheView, clusterData, includeDetails)
    mapResponse(clusters)
  }


  private static Map<String, Set<CloudFoundryCluster>> mapResponse(Collection<CloudFoundryCluster> clusters) {
    clusters.groupBy { it.accountName }.collectEntries { k, v -> [k, new HashSet(v)] }
  }

}
