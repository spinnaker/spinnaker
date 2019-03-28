/*
 * Copyright 2016 Target, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.clouddriver.openstack.provider.view

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Sets
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackFloatingIP
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackNetwork
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSubnet
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import groovy.util.logging.Slf4j
import static com.netflix.spinnaker.clouddriver.core.provider.agent.Namespace.TARGET_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.FLOATING_IPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.NETWORKS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SECURITY_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SUBNETS

@Slf4j
@Component
class OpenstackLoadBalancerProvider implements LoadBalancerProvider<OpenstackLoadBalancer.View> {

  final String cloudProvider = OpenstackCloudProvider.ID

  final Cache cacheView
  final ObjectMapper objectMapper
  final OpenstackClusterProvider clusterProvider

  @Autowired
  OpenstackLoadBalancerProvider(
    final Cache cacheView, final ObjectMapper objectMapper, final OpenstackClusterProvider clusterProvider) {
    this.cacheView = cacheView
    this.objectMapper = objectMapper
    this.clusterProvider = clusterProvider
  }

  /**
   * Find all load balancers associated with all clusters that are a part of the application.
   * @param application
   * @return
   */
  @Override
  Set<OpenstackLoadBalancer.View> getApplicationLoadBalancers(String application) {
    //get all load balancers tied to this app (via their name)
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, Keys.getLoadBalancerKey(application, '*', '*', '*'))
    identifiers.addAll(cacheView.filterIdentifiers(LOAD_BALANCERS.ns, Keys.getLoadBalancerKey("$application-*", '*', '*', '*')))
    Collection<CacheData> data = cacheView.getAll(LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.include(SERVER_GROUPS.ns, FLOATING_IPS.ns, NETWORKS.ns, SUBNETS.ns, SECURITY_GROUPS.ns))
    !data ? Sets.newHashSet() : data.collect(this.&fromCacheData)
  }

  /**
   * Get load balancer(s) by account, region, and id.
   * @param account
   * @param region
   * @param id
   * @return
   */
  Set<OpenstackLoadBalancer.View> getLoadBalancers(String account, String region, String id) {
    String pattern = Keys.getLoadBalancerKey('*', id, account, region)
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, pattern)
    Collection<CacheData> data = cacheView.getAll(LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.include(SERVER_GROUPS.ns, FLOATING_IPS.ns, NETWORKS.ns, SUBNETS.ns, SECURITY_GROUPS.ns))
    !data ? Sets.newHashSet() : data.collect(this.&fromCacheData)
  }

  /**
   * Convert load balancer cache data to a load balancer domain item.
   * @param cacheData
   * @return
   */
  OpenstackLoadBalancer.View fromCacheData(CacheData cacheData) {
    //get relationship data
    OpenstackFloatingIP ip = getRelationshipData(cacheData, FLOATING_IPS.ns, OpenstackFloatingIP)
    OpenstackNetwork network = getRelationshipData(cacheData, NETWORKS.ns, OpenstackNetwork)
    OpenstackSubnet subnet = getRelationshipData(cacheData, SUBNETS.ns, OpenstackSubnet)
    Set<String> securityGroups = cacheData.relationships[SECURITY_GROUPS.ns]?.collect { Keys.parse(it)?.id }?.toSet()

    //build load balancer
    OpenstackLoadBalancer loadBalancer = objectMapper.convertValue(cacheData.attributes, OpenstackLoadBalancer)
    loadBalancer.with {
      it.floatingIP = ip
      it.network = network
      it.subnet = subnet
      it.securityGroups = securityGroups ?: [].toSet()
    }

    //build load balancer server groups
    Set<LoadBalancerServerGroup> serverGroups = cacheData.relationships[SERVER_GROUPS.ns]?.findResults { key ->
      LoadBalancerServerGroup loadBalancerServerGroup = null
      ServerGroup serverGroup = clusterProvider.getServerGroup(loadBalancer.account, loadBalancer.region, Keys.parse(key)['serverGroup'])
      if (serverGroup) {
        loadBalancerServerGroup = new LoadBalancerServerGroup(name: serverGroup.name, isDisabled: serverGroup.isDisabled(), cloudProvider: OpenstackCloudProvider.ID)
        loadBalancerServerGroup.instances = serverGroup.instances?.collect { instance ->
          new LoadBalancerInstance(id: instance.name, health: [state: instance.healthState?.toString()])
        }?.toSet()
      }
      loadBalancerServerGroup
    }?.toSet()
    loadBalancer.serverGroups = serverGroups ?: [].toSet()

    //construct view
    loadBalancer.view
  }

  private <T> T getRelationshipData(CacheData parent, String type, Class<T> clazz) {
    CacheData cacheData = cacheView.getAll(type, parent.relationships[type] ?: [])?.find()
    objectMapper.convertValue(cacheData?.attributes, clazz)
  }

  List<OpenstackLoadBalancerSummary> list() {
    def searchKey = Keys.getLoadBalancerKey('*', '*', '*', '*');
    Collection<String> identifiers = cacheView.filterIdentifiers(LOAD_BALANCERS.ns, searchKey)
    def result = getSummaryForLoadBalancers(identifiers).values() as List
    result
  }

  LoadBalancerProvider.Item get(String name) {
    throw new UnsupportedOperationException("TODO: Support a single getter")
  }

  List<OpenstackLoadBalancer.View> byAccountAndRegionAndName(String account,
                                                             String region,
                                                             String name) {
    getLoadBalancers(account, region, name) as List
  }

  private Map<String, OpenstackLoadBalancerSummary> getSummaryForLoadBalancers(Collection<String> loadBalancerKeys) {
    Map<String, OpenstackLoadBalancerSummary> map = [:]
    Map<String, CacheData> loadBalancers = cacheView.getAll(LOAD_BALANCERS.ns, loadBalancerKeys, RelationshipCacheFilter.include(SERVER_GROUPS.ns, FLOATING_IPS.ns, NETWORKS.ns, SUBNETS.ns, SECURITY_GROUPS.ns)).collectEntries { [(it.id): it] }


    for (lb in loadBalancerKeys) {
      CacheData loadBalancerFromCache = loadBalancers[lb]
      if (loadBalancerFromCache) {
        def parts = Keys.parse(lb)
        String name = parts.name
        String region = parts.region
        String account = parts.account
        def summary = map.get(name)
        if (!summary) {
          summary = new OpenstackLoadBalancerSummary(name: name)
          map.put name, summary
        }
        def loadBalancer = new OpenstackLoadBalancerDetail()
        loadBalancer.account = parts.account
        loadBalancer.region = parts.region
        loadBalancer.name = parts.name
        loadBalancer.id = parts.id
        loadBalancer.securityGroups = loadBalancerFromCache.attributes.securityGroups
        loadBalancer.loadBalancerType = parts.type
        if (loadBalancer.loadBalancerType == null) {
          loadBalancer.loadBalancerType = "classic"
        }

        // Add target group list to the load balancer. At time of implementation, this is only used
        // to get the list of available target groups to deploy a server group into. Since target
        // groups only exist within load balancers (in clouddriver, Openstack allows them to exist
        // independently), this was an easy way to get them into deck without creating a whole new
        // provider type.
        if (loadBalancerFromCache.relationships[TARGET_GROUPS.ns]) {
          loadBalancer.targetGroups = loadBalancerFromCache.relationships[TARGET_GROUPS.ns].collect {
            Keys.parse(it).targetGroup
          }
        }

        summary.getOrCreateAccount(account).getOrCreateRegion(region).loadBalancers << loadBalancer
      }
    }
    map
  }


  // view models...

  static class OpenstackLoadBalancerSummary implements LoadBalancerProvider.Item {
    private Map<String, OpenstackLoadBalancerAccount> mappedAccounts = [:]
    String name

    OpenstackLoadBalancerAccount getOrCreateAccount(String name) {
      if (!mappedAccounts.containsKey(name)) {
        mappedAccounts.put(name, new OpenstackLoadBalancerAccount(name: name))
      }
      mappedAccounts[name]
    }

    @JsonProperty("accounts")
    List<OpenstackLoadBalancerAccount> getByAccounts() {
      mappedAccounts.values() as List
    }
  }

  static class OpenstackLoadBalancerAccount implements LoadBalancerProvider.ByAccount {
    private Map<String, OpenstackLoadBalancerByRegion> mappedRegions = [:]
    String name

    OpenstackLoadBalancerByRegion getOrCreateRegion(String name) {
      if (!mappedRegions.containsKey(name)) {
        mappedRegions.put(name, new OpenstackLoadBalancerByRegion(name: name, loadBalancers: []))
      }
      mappedRegions[name]
    }

    @JsonProperty("regions")
    List<OpenstackLoadBalancerByRegion> getByRegions() {
      mappedRegions.values() as List
    }
  }

  static class OpenstackLoadBalancerByRegion implements LoadBalancerProvider.ByRegion {
    String name
    List<OpenstackLoadBalancerDetail> loadBalancers
  }

  static class OpenstackLoadBalancerDetail implements LoadBalancerProvider.Details {
    String account
    String region
    String name
    String id
    String type = 'openstack'
    String loadBalancerType
    List<String> securityGroups = []
    List<String> targetGroups = []
  }

}
