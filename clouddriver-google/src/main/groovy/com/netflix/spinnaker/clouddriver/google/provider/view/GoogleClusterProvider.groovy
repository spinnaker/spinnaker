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

package com.netflix.spinnaker.clouddriver.google.provider.view

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.api.services.compute.model.Firewall
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.GoogleApplication
import com.netflix.spinnaker.clouddriver.google.model.GoogleCluster
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

@ConditionalOnProperty(value = "google.providerImpl", havingValue = "new")
@Component
class GoogleClusterProvider implements ClusterProvider<GoogleCluster> {

  @Autowired
  GoogleCloudProvider googleCloudProvider
  @Autowired
  Cache cacheView
  @Autowired
  ObjectMapper objectMapper

  @Override
  Map<String, Set<GoogleCluster>> getClusters() {
    cacheView.getAll(CLUSTERS.ns).groupBy { it.accountName }
  }

  @Override
  Map<String, Set<GoogleCluster>> getClusterDetails(String application) {
    getClusters(application)
  }

  @Override
  Map<String, Set<GoogleCluster>> getClusterSummaries(String application) {
    getClusterDetails(application)
    // TODO(ttomsu): Provide a higher level view (load balancer, security group names only)
  }

  @Override
  Set<GoogleCluster> getClusters(String applicationName, String account) {
    CacheData cacheData = cacheView.get(APPLICATIONS.ns,
                                        Keys.getApplicationKey(googleCloudProvider, applicationName),
                                        RelationshipCacheFilter.include(CLUSTERS.ns))
    applicationFromCacheData(cacheData).clusters[account].values()
  }

  @Override
  GoogleCluster getCluster(String application, String account, String name) {
    getClusters(application, account).find { it.name == name }
  }

  @Override
  ServerGroup getServerGroup(String account, String region, String name) {
    CacheData cacheData = cacheView.get(SERVER_GROUPS.ns,
                                        Keys.getServerGroupKey(googleCloudProvider, name, account, region),
                                        RelationshipCacheFilter.include(INSTANCES.ns))
    serverGroupFromCacheData(cacheData)
  }

  GoogleApplication applicationFromCacheData(CacheData cacheData) {
    GoogleApplication application = objectMapper.convertValue(cacheData.attributes, GoogleApplication)

    def clusters = []
    def clusterKeys = cacheData.relationships[CLUSTERS.ns]
    cacheView.getAll(CLUSTERS.ns, clusterKeys).each { CacheData clusterCacheData ->
      clusters << clusterFromCacheData(clusterCacheData)
    }

    def accountClusterMap = clusters.groupBy { it.accountName }
    application.clusters = accountClusterMap.collectEntries { String accountName, List<GoogleCluster> gClusters ->
      [(accountName): gClusters.collectEntries { GoogleCluster gc -> [(gc.name): gc] }]
    }

    application
  }

  GoogleCluster clusterFromCacheData(CacheData cacheData) {
    GoogleCluster cluster = objectMapper.convertValue(cacheData.attributes, GoogleCluster)

    def serverGroupKeys = cacheData.relationships[SERVER_GROUPS.ns]
    cacheView.getAll(SERVER_GROUPS.ns, serverGroupKeys).each { CacheData serverGroupCacheData ->
      cluster.serverGroups << serverGroupFromCacheData(serverGroupCacheData)
    }
    cluster
  }

  GoogleServerGroup serverGroupFromCacheData(CacheData cacheData) {
    GoogleServerGroup serverGroup = objectMapper.convertValue(cacheData.attributes, GoogleServerGroup)

    String networkName = serverGroup.anyProperty().networkName
    List<String> instanceTemplateTags = serverGroup.anyProperty().instanceTemplateTags
    serverGroup.securityGroups = getSecurityGroups(networkName, instanceTemplateTags)

    serverGroup
  }

  List<String> getSecurityGroups(String networkName, List<String> instanceTemplateTags) {
    cacheView.getAll(SECURITY_GROUPS.ns).findResults { CacheData cacheData ->
      Firewall firewall = cacheData.attributes.firewall as Firewall
      def networkNameMatches = Utils.getLocalName(firewall.network as String) == networkName
      boolean targetTagsEmpty = !firewall.targetTags
      def targetTagsInCommon = []
      if (!targetTagsEmpty) {
        targetTagsInCommon = (firewall.targetTags).intersect(instanceTemplateTags)
      }

      networkNameMatches && (targetTagsEmpty || !targetTagsInCommon.empty) ? firewall.name : null
    } as List<String>
  }
}
