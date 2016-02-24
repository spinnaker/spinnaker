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
import com.netflix.spinnaker.clouddriver.google.model.GoogleApplication2
import com.netflix.spinnaker.clouddriver.google.model.GoogleCluster2
import com.netflix.spinnaker.clouddriver.google.model.GoogleInstance2
import com.netflix.spinnaker.clouddriver.google.model.GoogleLoadBalancer2
import com.netflix.spinnaker.clouddriver.google.model.GoogleServerGroup2
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

@ConditionalOnProperty(value = "google.providerImpl", havingValue = "new")
@Component
class GoogleClusterProvider implements ClusterProvider<GoogleCluster2.View> {

  @Autowired
  GoogleCloudProvider googleCloudProvider
  @Autowired
  Cache cacheView
  @Autowired
  ObjectMapper objectMapper
  @Autowired
  GoogleApplicationProvider applicationProvider

  @Override
  Map<String, Set<GoogleCluster2.View>> getClusters() {
    cacheView.getAll(CLUSTERS.ns).groupBy { it.accountName }
  }

  @Override
  Map<String, Set<GoogleCluster2.View>> getClusterDetails(String applicationName) {
    getClusters(applicationName, true /* detailed */)
  }

  @Override
  Map<String, Set<GoogleCluster2.View>> getClusterSummaries(String applicationName) {
    getClusters(applicationName, false /* detailed */)
  }

  Map<String, Set<GoogleCluster2.View>> getClusters(String applicationName, boolean includeInstanceDetails) {
    GoogleApplication2.View application = applicationProvider.getApplication(applicationName)

    def clusterKeys = []
    application.clusterNames.each { String accountName, Set<String> clusterNames ->
      clusterNames.each { String clusterName ->
        clusterKeys << Keys.getClusterKey(googleCloudProvider, accountName, applicationName, clusterName)
      }
    }

    List<GoogleCluster2.View> clusters = cacheView.getAll(CLUSTERS.ns,
                                                          clusterKeys,
                                                          RelationshipCacheFilter.include(SERVER_GROUPS.ns)).collect { CacheData cacheData ->
      clusterFromCacheData(cacheData, includeInstanceDetails)
    }

    clusters.groupBy { it.accountName } as Map<String, Set<GoogleCluster2.View>>
  }

  @Override
  Set<GoogleCluster2.View> getClusters(String applicationName, String account) {
    getClusterDetails(applicationName)?.get(account)
  }

  @Override
  GoogleCluster2.View getCluster(String application, String account, String name) {
    getClusters(application, account).find { it.name == name }
  }

  @Override
  ServerGroup getServerGroup(String account, String region, String name) {
    CacheData cacheData = cacheView.get(SERVER_GROUPS.ns,
                                        Keys.getServerGroupKey(googleCloudProvider, name, account, region),
                                        RelationshipCacheFilter.include(INSTANCES.ns))
    //TODO(ttomsu): Fix this
    serverGroupFromCacheData(cacheData)
  }

  GoogleCluster2.View clusterFromCacheData(CacheData cacheData, boolean includeInstanceDetails) {
    GoogleCluster2.View clusterView = objectMapper.convertValue(cacheData.attributes, GoogleCluster2)?.view

    def serverGroupKeys = cacheData.relationships[SERVER_GROUPS.ns]
    if (serverGroupKeys) {
      def filter = includeInstanceDetails ?
          RelationshipCacheFilter.include(LOAD_BALANCERS.ns, INSTANCES.ns) :
          RelationshipCacheFilter.include(LOAD_BALANCERS.ns)
      cacheView.getAll(SERVER_GROUPS.ns,
                       serverGroupKeys,
                       filter).each { CacheData serverGroupCacheData ->
        clusterView.serverGroups << serverGroupFromCacheData(serverGroupCacheData, clusterView)
      }
    }

    clusterView
  }

  GoogleServerGroup2.View serverGroupFromCacheData(CacheData cacheData, GoogleCluster2.View clusterView) {
    GoogleServerGroup2 serverGroup = objectMapper.convertValue(cacheData.attributes, GoogleServerGroup2)

    def loadBalancerKeys = cacheData.relationships[LOAD_BALANCERS.ns]
    List<GoogleLoadBalancer2> loadBalancers = cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys).collect {
      GoogleLoadBalancer2 loadBalancer = objectMapper.convertValue(it.attributes, GoogleLoadBalancer2)
      clusterView.loadBalancers << loadBalancer.view
      loadBalancer
    }

    def instanceKeys = cacheData.relationships[INSTANCES.ns]
    if (instanceKeys) {
      cacheView.getAll(INSTANCES.ns, instanceKeys).each { CacheData instanceCacheData ->
        GoogleInstance2 instance = instanceFromCacheData(instanceCacheData)
        instance.loadBalancerHealths = getLoadBalancerHealths(instance.name, loadBalancers)
        serverGroup.instances << instance
      }

      // The presence of instance keys indicate details are requested.
      String networkName = serverGroup.anyProperty().networkName
      List<String> instanceTemplateTags = serverGroup.anyProperty().instanceTemplateTags as List
      serverGroup.securityGroups = getSecurityGroups(networkName, instanceTemplateTags)
    }

    serverGroup.view
  }

  List<GoogleLoadBalancerHealth> getLoadBalancerHealths(String instanceName, List<GoogleLoadBalancer2> loadBalancers) {
    loadBalancers*.healths.findResults { List<GoogleLoadBalancerHealth> glbhs ->
      glbhs.findAll { GoogleLoadBalancerHealth glbh ->
        glbh.instanceName == instanceName
      }
    }.flatten()
  }

  GoogleInstance2 instanceFromCacheData(CacheData cacheData) {
    objectMapper.convertValue(cacheData.attributes, GoogleInstance2)
  }

  List<String> getSecurityGroups(String networkName, List<String> instanceTemplateTags) {
    cacheView.getAll(SECURITY_GROUPS.ns).findResults { CacheData cacheData ->
      Firewall firewall = cacheData.attributes.firewall as Firewall
      if (!firewall) {
        return
      }
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
