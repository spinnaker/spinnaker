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

package com.netflix.spinnaker.clouddriver.google.model.loadbalancing

import com.fasterxml.jackson.annotation.JsonIgnore
import com.netflix.spinnaker.clouddriver.google.GoogleCloudProvider
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeSuper=true)
@EqualsAndHashCode(callSuper=true)
class GoogleNetworkLoadBalancer extends GoogleLoadBalancer {
  GoogleLoadBalancerType type = GoogleLoadBalancerType.NETWORK
  GoogleLoadBalancingScheme loadBalancingScheme = GoogleLoadBalancingScheme.EXTERNAL

  String targetPool
  String sessionAffinity
  GoogleHealthCheck healthCheck

  @JsonIgnore
  GoogleLoadBalancerView getView() {
    new View(this)
  }

  class View extends GoogleLoadBalancerView {
    GoogleLoadBalancerType loadBalancerType
    GoogleLoadBalancingScheme loadBalancingScheme

    String name
    String account
    String region
    Long createdTime
    String ipAddress
    String ipProtocol
    String portRange

    String targetPool
    String sessionAffinity

    GoogleHealthCheck.View healthCheck

    Set<LoadBalancerServerGroup> serverGroups

    View(GoogleNetworkLoadBalancer googleNetworkLoadBalancer){
      loadBalancerType = googleNetworkLoadBalancer.type
      loadBalancingScheme = googleNetworkLoadBalancer.loadBalancingScheme
      name = googleNetworkLoadBalancer.name
      account = googleNetworkLoadBalancer.account
      region = googleNetworkLoadBalancer.region
      createdTime = googleNetworkLoadBalancer.createdTime
      ipAddress = googleNetworkLoadBalancer.ipAddress
      ipProtocol = googleNetworkLoadBalancer.ipProtocol
      portRange = googleNetworkLoadBalancer.portRange
      targetPool =  googleNetworkLoadBalancer.targetPool
      sessionAffinity = googleNetworkLoadBalancer.sessionAffinity
      healthCheck = googleNetworkLoadBalancer.healthCheck?.view
      serverGroups = new HashSet<>()
    }

  }
}
