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
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.consul.provider.ConsulProviderUtils
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.cache.Keys
import com.netflix.spinnaker.clouddriver.google.model.*
import com.netflix.spinnaker.clouddriver.google.model.callbacks.Utils
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleHttpLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleInternalLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleLoadBalancerType
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleNetworkLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleSslLoadBalancer
import com.netflix.spinnaker.clouddriver.google.model.loadbalancing.GoogleTcpLoadBalancer
import com.netflix.spinnaker.clouddriver.model.ClusterProvider
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.google.cache.Keys.Namespace.*

@Component
@Slf4j
class GoogleClusterProvider implements ClusterProvider<GoogleCluster.View> {
  @Autowired
  GoogleCloudProvider googleCloudProvider

  @Autowired
  Cache cacheView

  @Autowired
  ObjectMapper objectMapper

  @Autowired
  GoogleApplicationProvider applicationProvider

  @Autowired
  GoogleInstanceProvider instanceProvider

  @Autowired
  GoogleSecurityGroupProvider securityGroupProvider

  @Override
  Map<String, Set<GoogleCluster.View>> getClusters() {
    cacheView.getAll(CLUSTERS.ns).groupBy { it.accountName }
  }

  @Override
  Map<String, Set<GoogleCluster.View>> getClusterDetails(String applicationName) {
    getClusters(applicationName, true /* detailed */).collectEntries { k, v -> [k, new HashSet<>(v)] }
  }

  @Override
  Map<String, Set<GoogleCluster.View>> getClusterSummaries(String applicationName) {
    getClusters(applicationName, false /* detailed */).collectEntries { k, v -> [k, new HashSet<>(v)] }
  }

  Map<String, Set<GoogleCluster.View>> getClusters(String applicationName, boolean includeInstanceDetails) {
    GoogleApplication.View application = applicationProvider.getApplication(applicationName)

    def clusterKeys = []
    application?.clusterNames?.each { String accountName, Set<String> clusterNames ->
      clusterNames.each { String clusterName ->
        clusterKeys << Keys.getClusterKey(accountName, applicationName, clusterName)
      }
    }

    // TODO(jacobkiefer): Avoid parsing instance keys into map just to re-serialize?
    Set<String> allApplicationInstanceKeys = includeInstanceDetails ? application?.instances?.collect { Keys.getInstanceKey(it.account, it.region, it.name) } : [] as Set

    List<GoogleCluster.View> clusters = cacheView.getAll(
        CLUSTERS.ns,
        clusterKeys,
        RelationshipCacheFilter.include(SERVER_GROUPS.ns)).collect { CacheData cacheData ->
      clusterFromCacheData(cacheData, allApplicationInstanceKeys)
    }

    clusters?.groupBy { it.accountName } as Map<String, Set<GoogleCluster.View>>
  }

  @Override
  Set<GoogleCluster.View> getClusters(String applicationName, String account) {
    getClusterDetails(applicationName)?.get(account)
  }

  @Override
  GoogleCluster.View getCluster(String application, String account, String name, boolean includeDetails) {
    CacheData clusterData = cacheView.get(
      CLUSTERS.ns,
      Keys.getClusterKey(account, application, name),
      RelationshipCacheFilter.include(SERVER_GROUPS.ns, INSTANCES.ns))

    Set<String> allClusterInstanceKeys = includeDetails ? (clusterData?.relationships?.get(INSTANCES.ns) ?: []) : [] as Set

    return clusterData ? clusterFromCacheData(clusterData, allClusterInstanceKeys) : null
  }

  @Override
  GoogleCluster.View getCluster(String applicationName, String accountName, String clusterName) {
    return getCluster(applicationName, accountName, clusterName, true)
  }

  @Override
  GoogleServerGroup.View getServerGroup(String account, String region, String name, boolean includeDetails) {
    def cacheData = cacheView.get(SERVER_GROUPS.ns,
                                  Keys.getServerGroupKey(name, account, region),
                                  RelationshipCacheFilter.include(LOAD_BALANCERS.ns, INSTANCES.ns))

    if (!cacheData) {
      // No regional server group was found, so attempt to query for all zonal server groups in the region.
      def pattern = Keys.getServerGroupKey(name, account, region, "*")
      def identifiers = cacheView.filterIdentifiers(SERVER_GROUPS.ns, pattern)
      def cacheDataResults = cacheView.getAll(SERVER_GROUPS.ns,
                                              identifiers,
                                              RelationshipCacheFilter.include(LOAD_BALANCERS.ns, INSTANCES.ns))

      if (cacheDataResults) {
        cacheData = cacheDataResults.first()
      }
    }

    if (cacheData) {
      def securityGroups = securityGroupProvider.getAllByAccount(false, account)

      def instanceKeys = cacheData.relationships?.get(INSTANCES.ns) ?: []
      def instances = instanceProvider.getInstances(account, instanceKeys as List, securityGroups)
      def loadBalancers = loadBalancersFromKeys(cacheData.relationships[LOAD_BALANCERS.ns] as List)
      return serverGroupFromCacheData(cacheData, account, instances, securityGroups, loadBalancers)?.view
    }
  }

  @Override
  GoogleServerGroup.View getServerGroup(String account, String region, String name) {
    return getServerGroup(account, region, name, true)
  }

  @Override
  String getCloudProviderId() {
    return googleCloudProvider.id
  }

  @Override
  boolean supportsMinimalClusters() {
    return false
  }

  GoogleCluster.View clusterFromCacheData(CacheData cacheData, Set<String> instanceKeySuperSet) {
    GoogleCluster.View clusterView = objectMapper.convertValue(cacheData.attributes, GoogleCluster)?.view

    def serverGroupKeys = cacheData.relationships[SERVER_GROUPS.ns]
    if (serverGroupKeys) {
      log.debug("Server group keys from cluster relationships: ${serverGroupKeys}")
      def filter = RelationshipCacheFilter.include(LOAD_BALANCERS.ns)

      def serverGroupData = cacheView.getAll(SERVER_GROUPS.ns, serverGroupKeys, filter)
      log.debug("Retrieved cache data for server groups: ${serverGroupData?.collect { it?.attributes?.name }}")

      def securityGroups = securityGroupProvider.getAllByAccount(false, clusterView.accountName)

      // TODO(duftler): De-frigga this.
      def clusterInstancePattern = Keys.getInstanceKey(clusterView.accountName, ".*", "$clusterView.name-.*")
      def instanceKeys = instanceKeySuperSet.findAll { it ==~ clusterInstancePattern }
      def instances = instanceProvider.getInstances(clusterView.accountName, instanceKeys as List, securityGroups)

      def loadBalancerKeys = serverGroupData.collect { serverGroup ->
        serverGroup.relationships[LOAD_BALANCERS.ns] as List<String>
      }.flatten()
      def loadBalancers = loadBalancersFromKeys(loadBalancerKeys as List)

      serverGroupData.each { CacheData serverGroupCacheData ->
        GoogleServerGroup serverGroup = serverGroupFromCacheData(serverGroupCacheData, clusterView.accountName, instances, securityGroups, loadBalancers)
        clusterView.serverGroups << serverGroup.view
        clusterView.loadBalancers.addAll(serverGroup.loadBalancers*.view)
      }
      log.debug("Server groups added to cluster: ${clusterView?.serverGroups?.collect { it?.name }}")
    }

    clusterView
  }

  Set<GoogleLoadBalancer> loadBalancersFromKeys(List<String> loadBalancerKeys) {
    return cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys).collect {
      def loadBalancer = null
      switch (GoogleLoadBalancerType.valueOf(it.attributes?.type as String)) {
        case GoogleLoadBalancerType.INTERNAL:
          loadBalancer = objectMapper.convertValue(it.attributes, GoogleInternalLoadBalancer)
          break
        case GoogleLoadBalancerType.HTTP:
          loadBalancer = objectMapper.convertValue(it.attributes, GoogleHttpLoadBalancer)
          break
        case GoogleLoadBalancerType.NETWORK:
          loadBalancer = objectMapper.convertValue(it.attributes, GoogleNetworkLoadBalancer)
          break
        case GoogleLoadBalancerType.SSL:
          loadBalancer = objectMapper.convertValue(it.attributes, GoogleSslLoadBalancer)
          break
        case GoogleLoadBalancerType.TCP:
          loadBalancer = objectMapper.convertValue(it.attributes, GoogleTcpLoadBalancer)
          break
        default:
          loadBalancer = null
          break
      }
      return loadBalancer
    } as Set
  }

  GoogleServerGroup serverGroupFromCacheData(CacheData cacheData,
                                             String account,
                                             List<GoogleInstance> instances,
                                             Set<GoogleSecurityGroup> securityGroups,
                                             Set<GoogleLoadBalancer> loadBalancers) {
    GoogleServerGroup serverGroup = objectMapper.convertValue(cacheData.attributes, GoogleServerGroup)

    def loadBalancerKeys = cacheData.relationships[LOAD_BALANCERS.ns]
    loadBalancers = loadBalancers.findAll { loadBalancer ->
      def loadBalancerKey = Keys.getLoadBalancerKey(loadBalancer.region, loadBalancer.account, loadBalancer.name)
      return loadBalancerKeys?.contains(loadBalancerKey) ? loadBalancer : null
    }
    serverGroup.loadBalancers = loadBalancers*.view

    serverGroup.securityGroups = GoogleSecurityGroupProvider.getMatchingServerGroupNames(
        account,
        securityGroups,
        serverGroup.instanceTemplateTags,
        serverGroup.networkName)

    if (instances) {
      serverGroup.instances = instances.findAll { GoogleInstance instance ->
        instance.serverGroup == serverGroup.name && instance.region == serverGroup.region
      }

      serverGroup.instances.each { GoogleInstance instance ->
        def foundHealths = getLoadBalancerHealths(instance.name, loadBalancers as List)
        if (foundHealths) {
          instance.loadBalancerHealths = foundHealths
        }
      }
    }

    // Time to aggregate health states that can't be computed during the server group fetch operation.
    def internalLoadBalancers = loadBalancers.findAll { it.type == GoogleLoadBalancerType.INTERNAL }
    def internalDisabledStates = internalLoadBalancers.collect { loadBalancer ->
      Utils.determineInternalLoadBalancerDisabledState(loadBalancer, serverGroup)
    }

    def httpLoadBalancers = loadBalancers.findAll { it.type == GoogleLoadBalancerType.HTTP }
    def httpDisabledStates = httpLoadBalancers.collect { loadBalancer ->
        Utils.determineHttpLoadBalancerDisabledState(loadBalancer, serverGroup)
    }

    def sslLoadBalancers = loadBalancers.findAll { it.type == GoogleLoadBalancerType.SSL }
    def sslDisabledStates = sslLoadBalancers.collect { loadBalancer ->
      Utils.determineSslLoadBalancerDisabledState(loadBalancer, serverGroup)
    }

    def tcpLoadBalancers = loadBalancers.findAll { it.type == GoogleLoadBalancerType.TCP }
    def tcpDisabledStates = tcpLoadBalancers.collect { loadBalancer ->
      Utils.determineTcpLoadBalancerDisabledState(loadBalancer, serverGroup)
    }

    // Health states for Consul.
    def consulNodes = serverGroup.instances?.collect { it.consulNode } ?: []
    def consulDiscoverable = ConsulProviderUtils.consulServerGroupDiscoverable(consulNodes)
    def consulDisabled = false
    if (consulDiscoverable) {
      consulDisabled = ConsulProviderUtils.serverGroupDisabled(consulNodes)
    }

    def isDisabled = true
    // TODO: Extend this for future load balancers that calculate disabled state after caching.
    def anyDisabledStates = internalDisabledStates || httpDisabledStates || sslDisabledStates || tcpDisabledStates
    def disabledStatesSizeMatch = internalDisabledStates.size() + httpDisabledStates.size() + sslDisabledStates.size() + tcpDisabledStates.size() == loadBalancers.size()
    def excludesNetwork = anyDisabledStates && disabledStatesSizeMatch

    if (httpDisabledStates) {
      isDisabled &= httpDisabledStates.every { it }
    }
    if (internalDisabledStates) {
      isDisabled &= internalDisabledStates.every { it }
    }
    if (sslDisabledStates) {
      isDisabled &= sslDisabledStates.every { it }
    }
    if (tcpDisabledStates) {
      isDisabled &= tcpDisabledStates.every { it }
    }
    serverGroup.disabled = excludesNetwork ? isDisabled : isDisabled && serverGroup.disabled

    // Now that disabled is set based on L7 & L4 state, we need to take Consul into account.
    if (consulDiscoverable) {
      // If there are no load balancers to determine enable/disabled status we rely on Consul exclusively.
      if (loadBalancers.size() == 0) {
          serverGroup.disabled = true
      }
      // If the server group is disabled, but Consul isn't, we say the server group is discoverable.
      // If the server group isn't disabled, but Consul is, we say the server group can be reached via load balancer.
      // If the server group and Consul are both disabled, the server group remains disabled.
      // If the server group and Consul are both not disabled, the server group is not disabled.
      serverGroup.disabled &= consulDisabled
      serverGroup.discovery = true
    }

    serverGroup
  }

  static List<GoogleLoadBalancerHealth> getLoadBalancerHealths(String instanceName, List<GoogleLoadBalancer> loadBalancers) {
    loadBalancers*.healths.findResults { List<GoogleLoadBalancerHealth> glbhs ->
      glbhs.findAll { GoogleLoadBalancerHealth glbh ->
        glbh.instanceName == instanceName
      }
    }.flatten()
  }
}
