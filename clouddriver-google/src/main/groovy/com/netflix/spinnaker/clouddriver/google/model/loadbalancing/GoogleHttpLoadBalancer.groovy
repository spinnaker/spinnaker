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
import com.netflix.spinnaker.clouddriver.google.model.GoogleHealthCheck
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import groovy.transform.Canonical

@Canonical
class GoogleHttpLoadBalancer {
  GoogleLoadBalancerType type = GoogleLoadBalancerType.HTTP

  String name
  String account
  String region
  Long createdTime
  String ipAddress
  String ipProtocol
  String portRange
  List<GoogleLoadBalancerHealth> healths

  /**
   * Default backend service a request is sent to if no host rules are matched.
   */
  GoogleBackendService defaultService

  /**
   * List of host rules that map incoming requests to GooglePathMatchers based on host header.
   */
  List<GoogleHostRule> hostRules

  /**
   * SSL certificate. This is populated only if this load balancer is a HTTPS load balancer.
   */
  String certificate

  @JsonIgnore
  GoogleLoadBalancerView getView() {
    new View()
  }

  @Canonical
  class View extends GoogleLoadBalancerView {
    String loadBalancerType = GoogleHttpLoadBalancer.this.type

    String name = GoogleHttpLoadBalancer.this.name
    String account = GoogleHttpLoadBalancer.this.account
    String region = GoogleHttpLoadBalancer.this.region
    Long createdTime = GoogleHttpLoadBalancer.this.createdTime
    String ipAddress = GoogleHttpLoadBalancer.this.ipAddress
    String ipProtocol = GoogleHttpLoadBalancer.this.ipProtocol
    String portRange = GoogleHttpLoadBalancer.this.portRange
    String certificate = GoogleHttpLoadBalancer.this.certificate
    GoogleBackendService defaultService = GoogleHttpLoadBalancer.this.defaultService

    Set<LoadBalancerServerGroup> serverGroups = new HashSet<>()
    List<GoogleHostRule> hostRules = GoogleHttpLoadBalancer.this.hostRules
  }
}
