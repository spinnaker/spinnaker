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

import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.Sets
import com.netflix.spinnaker.cats.cache.Cache
import com.netflix.spinnaker.cats.cache.CacheData
import com.netflix.spinnaker.cats.cache.RelationshipCacheFilter
import com.netflix.spinnaker.clouddriver.model.LoadBalancerInstance
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.model.ServerGroup
import com.netflix.spinnaker.clouddriver.openstack.cache.Keys
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackFloatingIP
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackInstance
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackLoadBalancer
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackNetwork
import com.netflix.spinnaker.clouddriver.openstack.model.OpenstackSubnet
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.FLOATING_IPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.LOAD_BALANCERS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.NETWORKS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SERVER_GROUPS
import static com.netflix.spinnaker.clouddriver.openstack.cache.Keys.Namespace.SUBNETS

@Component
class OpenstackLoadBalancerProvider implements LoadBalancerProvider<OpenstackLoadBalancer.View> {

  final Cache cacheView
  final ObjectMapper objectMapper
  final OpenstackClusterProvider clusterProvider

  @Autowired
  OpenstackLoadBalancerProvider(final Cache cacheView, final ObjectMapper objectMapper, final OpenstackClusterProvider clusterProvider) {
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
    Collection<CacheData> data = cacheView.getAll(LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.include(SERVER_GROUPS.ns, FLOATING_IPS.ns, NETWORKS.ns, SUBNETS.ns))
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
    Collection<CacheData> data = cacheView.getAll(LOAD_BALANCERS.ns, identifiers, RelationshipCacheFilter.include(SERVER_GROUPS.ns, FLOATING_IPS.ns, NETWORKS.ns, SUBNETS.ns))
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

    //build load balancer
    OpenstackLoadBalancer loadBalancer = objectMapper.convertValue(cacheData.attributes, OpenstackLoadBalancer)

    //build load balancer server groups
    Set<LoadBalancerServerGroup> serverGroups = cacheData.relationships[SERVER_GROUPS.ns]?.findResults { key ->
      LoadBalancerServerGroup loadBalancerServerGroup = null
      ServerGroup serverGroup = clusterProvider.getServerGroup(loadBalancer.account, loadBalancer.region, Keys.parse(key)['serverGroup'])
      if (serverGroup) {
        loadBalancerServerGroup = new LoadBalancerServerGroup(name: serverGroup.name, isDisabled: serverGroup.isDisabled())
        loadBalancerServerGroup.instances = serverGroup.instances?.collect { instance ->
          new LoadBalancerInstance(id: ((OpenstackInstance) instance).instanceId, health: instance.health && instance.health.size() > 0 ? instance.health[0] : null)
        }?.toSet()
      }
      loadBalancerServerGroup
    }?.toSet()

    //construct view
    new OpenstackLoadBalancer.View(account: loadBalancer.account, region: loadBalancer.region, id: loadBalancer.id, name: loadBalancer.name,
      description: loadBalancer.description, status: loadBalancer.status, method: loadBalancer.status,
      listeners: loadBalancer.listeners, healthMonitor: loadBalancer.healthMonitor, ip: ip?.floatingIpAddress,
      subnetId: subnet?.id, subnetName: subnet?.name,
      networkId: network?.id, networkName: network?.name, serverGroups: serverGroups ?: [].toSet())
  }

  private <T> T getRelationshipData(CacheData parent, String type, Class<T> clazz) {
    CacheData cacheData = cacheView.getAll(type, parent.relationships[type] ?: [])?.find()
    objectMapper.convertValue(cacheData?.attributes, clazz)
  }

}
