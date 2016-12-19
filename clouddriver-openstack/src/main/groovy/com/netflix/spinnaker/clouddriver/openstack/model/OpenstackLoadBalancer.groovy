/*
 * Copyright 2016 Target Inc.
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

package com.netflix.spinnaker.clouddriver.openstack.model

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.netflix.spinnaker.clouddriver.model.LoadBalancer
import com.netflix.spinnaker.clouddriver.model.LoadBalancerProvider
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import com.netflix.spinnaker.clouddriver.openstack.OpenstackCloudProvider
import com.netflix.spinnaker.clouddriver.openstack.domain.LoadBalancerResolver
import groovy.transform.Canonical
import org.openstack4j.model.network.ext.HealthMonitorV2
import org.openstack4j.model.network.ext.LbPoolV2
import org.openstack4j.model.network.ext.ListenerV2
import org.openstack4j.model.network.ext.LoadBalancerV2

@Canonical
@JsonIgnoreProperties(['createdRegex', 'createdPattern'])
class OpenstackLoadBalancer implements LoadBalancerResolver, LoadBalancer {

  final String type = OpenstackCloudProvider.ID
  final String cloudProvider = OpenstackCloudProvider.ID
  String account
  String region
  String id
  String name
  String description
  String status
  String algorithm
  Set<OpenstackLoadBalancerListener> listeners
  OpenstackHealthMonitor healthMonitor
  Set<OpenstackLoadBalancerHealth> healths
  Set<LoadBalancerServerGroup> serverGroups
  OpenstackFloatingIP floatingIP
  OpenstackNetwork network
  OpenstackSubnet subnet
  Set<String> securityGroups

  static OpenstackLoadBalancer from(LoadBalancerV2 loadBalancer, Set<ListenerV2> listeners, LbPoolV2 pool,
                                    HealthMonitorV2 healthMonitor, String account, String region) {
    if (!loadBalancer) {
      throw new IllegalArgumentException("Load balancer must not be null.")
    }
    Set<OpenstackLoadBalancerListener> openstackListeners = listeners?.collect { listener ->
      new OpenstackLoadBalancerListener(externalProtocol: listener.protocol.toString(),
        externalPort: listener.protocolPort,
        description: listener.description)
    }?.toSet() ?: [].toSet()
    OpenstackHealthMonitor openstackHealthMonitor = healthMonitor ? new OpenstackHealthMonitor(id: healthMonitor.id,
      adminStateUp: healthMonitor.adminStateUp, delay: healthMonitor.delay, maxRetries: healthMonitor.maxRetries,
      expectedCodes: healthMonitor.expectedCodes?.split(',')?.collect {
        it?.toInteger()
      }, httpMethod: healthMonitor.httpMethod,
      timeout: healthMonitor.timeout, type: healthMonitor.type.toString(), url: healthMonitor.urlPath) : null
    new OpenstackLoadBalancer(account: account, region: region, id: loadBalancer.id, name: loadBalancer.name,
      description: loadBalancer.description, status: loadBalancer.operatingStatus,
      algorithm: pool?.lbMethod?.toString(), listeners: openstackListeners, healthMonitor: openstackHealthMonitor)
  }

  Long getCreatedTime() {
    parseCreatedTime(description)
  }

  @JsonIgnore
  View getView() {
    new View(account: account, region: region, id: id, name: name,
      description: description, status: status, algorithm: algorithm,
      listeners: listeners, healthMonitor: healthMonitor, ip: floatingIP?.floatingIpAddress,
      subnetId: subnet?.id, subnetName: subnet?.name, healths: healths,
      networkId: network?.id, networkName: network?.name, serverGroups: serverGroups ?: [].toSet(), securityGroups: securityGroups ?: [].toSet())
  }

  @Canonical
  @JsonIgnoreProperties(['createdRegex', 'createdPattern'])
  static class OpenstackLoadBalancerListener implements LoadBalancerResolver {
    String description
    String externalProtocol
    Integer externalPort

    Integer getInternalPort() {
      parseListenerKey(description)?.get('internalPort')?.toInteger()
    }
  }

  @Canonical
  static class OpenstackHealthMonitor {
    String id
    boolean adminStateUp
    Integer delay
    Integer maxRetries
    Integer timeout
    List<Integer> expectedCodes
    String httpMethod
    String type
    String url
  }

  @Canonical
  @JsonIgnoreProperties(['createdRegex', 'createdPattern'])
  static class View extends OpenstackLoadBalancer implements LoadBalancer, LoadBalancerProvider.Details {
    String ip = ""
    String subnetId = ""
    String subnetName = ""
    String networkId = ""
    String networkName = ""

    //oh groovy asts are fun - they bring insanity for everyone
    //we need this for creating sets
    @Override
    boolean equals(Object other) {
      View view = (View) other
      ip == view.ip && subnetId == view.subnetId && subnetName == view.subnetName &&
        networkId == view.networkId && networkName == view.networkName && super.equals((OpenstackLoadBalancer) view)
    }

  }

}
