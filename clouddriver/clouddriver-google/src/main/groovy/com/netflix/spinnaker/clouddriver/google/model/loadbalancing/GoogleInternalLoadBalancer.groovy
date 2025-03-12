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
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeSuper=true)
@EqualsAndHashCode(callSuper=true)
class GoogleInternalLoadBalancer extends GoogleLoadBalancer {
  GoogleLoadBalancerType type = GoogleLoadBalancerType.INTERNAL
  GoogleLoadBalancingScheme loadBalancingScheme = GoogleLoadBalancingScheme.INTERNAL

  List<String> ports
  String network
  String subnet
  GoogleBackendService backendService

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

    List<String> ports
    String network
    String subnet
    GoogleBackendService backendService

    Set<LoadBalancerServerGroup> serverGroups

    View(GoogleInternalLoadBalancer googleInternalLoadBalancer){
      loadBalancerType = googleInternalLoadBalancer.type
      loadBalancingScheme = googleInternalLoadBalancer.loadBalancingScheme
      name = googleInternalLoadBalancer.name
      account = googleInternalLoadBalancer.account
      region = googleInternalLoadBalancer.region
      createdTime = googleInternalLoadBalancer.createdTime
      ipAddress = googleInternalLoadBalancer.ipAddress
      ipProtocol = googleInternalLoadBalancer.ipProtocol
      portRange = googleInternalLoadBalancer.portRange
      ports = googleInternalLoadBalancer.ports
      network = googleInternalLoadBalancer.network
      subnet = googleInternalLoadBalancer.subnet
      backendService = googleInternalLoadBalancer.backendService
      serverGroups = new HashSet<>()
    }

  }
}
