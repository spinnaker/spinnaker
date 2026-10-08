/*
 * Copyright 2026 Harness, Inc.
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
class GoogleRegionalExternalNetworkLoadBalancer extends GoogleLoadBalancer {
  GoogleLoadBalancerType type = GoogleLoadBalancerType.REGIONAL_EXTERNAL_NETWORK
  GoogleLoadBalancingScheme loadBalancingScheme = GoogleLoadBalancingScheme.EXTERNAL

  List<String> ports
  String network
  String networkTier
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
    String networkTier
    GoogleBackendService backendService

    Set<LoadBalancerServerGroup> serverGroups

    View(GoogleRegionalExternalNetworkLoadBalancer googleLoadBalancer) {
      loadBalancerType = googleLoadBalancer.type
      loadBalancingScheme = googleLoadBalancer.loadBalancingScheme
      name = googleLoadBalancer.name
      account = googleLoadBalancer.account
      region = googleLoadBalancer.region
      createdTime = googleLoadBalancer.createdTime
      ipAddress = googleLoadBalancer.ipAddress
      ipProtocol = googleLoadBalancer.ipProtocol
      portRange = googleLoadBalancer.portRange
      ports = googleLoadBalancer.ports
      network = googleLoadBalancer.network
      networkTier = googleLoadBalancer.networkTier
      backendService = googleLoadBalancer.backendService
      serverGroups = new HashSet<>()
    }
  }
}
