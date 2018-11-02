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
  GoogleHealthCheck healthCheck

  @JsonIgnore
  GoogleLoadBalancerView getView() {
    new View()
  }

  class View extends GoogleLoadBalancerView {
    final String type = GoogleCloudProvider.ID
    GoogleLoadBalancerType loadBalancerType = GoogleNetworkLoadBalancer.this.type
    GoogleLoadBalancingScheme loadBalancingScheme = GoogleNetworkLoadBalancer.this.loadBalancingScheme

    String name = GoogleNetworkLoadBalancer.this.name
    String account = GoogleNetworkLoadBalancer.this.account
    String region = GoogleNetworkLoadBalancer.this.region
    Long createdTime = GoogleNetworkLoadBalancer.this.createdTime
    String ipAddress = GoogleNetworkLoadBalancer.this.ipAddress
    String ipProtocol = GoogleNetworkLoadBalancer.this.ipProtocol
    String portRange = GoogleNetworkLoadBalancer.this.portRange

    String targetPool =  GoogleNetworkLoadBalancer.this.targetPool
    GoogleHealthCheck.View healthCheck = GoogleNetworkLoadBalancer.this.healthCheck?.view

    Set<LoadBalancerServerGroup> serverGroups = new HashSet<>()
  }
}
