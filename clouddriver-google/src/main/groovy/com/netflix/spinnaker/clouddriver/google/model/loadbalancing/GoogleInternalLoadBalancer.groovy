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
    new View()
  }

  class View extends GoogleLoadBalancerView {
    GoogleLoadBalancerType loadBalancerType = GoogleInternalLoadBalancer.this.type
    GoogleLoadBalancingScheme loadBalancingScheme = GoogleInternalLoadBalancer.this.loadBalancingScheme

    String name = GoogleInternalLoadBalancer.this.name
    String account = GoogleInternalLoadBalancer.this.account
    String region = GoogleInternalLoadBalancer.this.region
    Long createdTime = GoogleInternalLoadBalancer.this.createdTime
    String ipAddress = GoogleInternalLoadBalancer.this.ipAddress
    String ipProtocol = GoogleInternalLoadBalancer.this.ipProtocol
    String portRange = GoogleInternalLoadBalancer.this.portRange

    List<String> ports = GoogleInternalLoadBalancer.this.ports
    String network = GoogleInternalLoadBalancer.this.network
    String subnet = GoogleInternalLoadBalancer.this.subnet
    GoogleBackendService backendService = GoogleInternalLoadBalancer.this.backendService

    Set<LoadBalancerServerGroup> serverGroups = new HashSet<>()
  }
}
