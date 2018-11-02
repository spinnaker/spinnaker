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
class GoogleTcpLoadBalancer extends GoogleLoadBalancer {
  GoogleLoadBalancerType type = GoogleLoadBalancerType.TCP
  GoogleLoadBalancingScheme loadBalancingScheme = GoogleLoadBalancingScheme.EXTERNAL

  GoogleBackendService backendService

  @JsonIgnore
  GoogleLoadBalancerView getView() {
    new View()
  }

  class View extends GoogleLoadBalancerView {
    GoogleLoadBalancerType loadBalancerType = GoogleTcpLoadBalancer.this.type
    GoogleLoadBalancingScheme loadBalancingScheme = GoogleTcpLoadBalancer.this.loadBalancingScheme

    String name = GoogleTcpLoadBalancer.this.name
    String account = GoogleTcpLoadBalancer.this.account
    String region = GoogleTcpLoadBalancer.this.region
    Long createdTime = GoogleTcpLoadBalancer.this.createdTime
    String ipAddress = GoogleTcpLoadBalancer.this.ipAddress
    String ipProtocol = GoogleTcpLoadBalancer.this.ipProtocol
    String portRange = GoogleTcpLoadBalancer.this.portRange

    GoogleBackendService backendService = GoogleTcpLoadBalancer.this.backendService

    Set<LoadBalancerServerGroup> serverGroups = new HashSet<>()
  }
}
