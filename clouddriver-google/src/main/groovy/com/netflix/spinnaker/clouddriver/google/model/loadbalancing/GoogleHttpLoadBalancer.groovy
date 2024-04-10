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
import com.netflix.spinnaker.clouddriver.google.model.health.GoogleLoadBalancerHealth
import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup
import groovy.transform.Canonical
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString

@ToString(includeSuper=true)
@EqualsAndHashCode(callSuper=true)
class GoogleHttpLoadBalancer extends GoogleLoadBalancer {
  GoogleLoadBalancerType type = GoogleLoadBalancerType.HTTP
  GoogleLoadBalancingScheme loadBalancingScheme = GoogleLoadBalancingScheme.EXTERNAL

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

  /**
   * The name of the UrlMap this load balancer uses to route traffic. In the Google
   * Cloud Console, the L7 load balancer name is the same as this name.
   */
  String urlMapName

  /**
   * Flag for filtering out load balancers that contain backend buckets in the caching agent.
   * TODO(jacobkiefer): Support backend buckets.
   */
  Boolean containsBackendBucket = false

  @JsonIgnore
  GoogleLoadBalancerView getView() {
    new View(this)
  }

  @Canonical
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

    GoogleBackendService defaultService
    List<GoogleHostRule> hostRules
    String certificate
    String urlMapName

    Set<LoadBalancerServerGroup> serverGroups

    View(GoogleHttpLoadBalancer googleHttpLoadBalancer){
      loadBalancerType = googleHttpLoadBalancer.type
      loadBalancingScheme = googleHttpLoadBalancer.loadBalancingScheme
      name = googleHttpLoadBalancer.name
      account = googleHttpLoadBalancer.account
      region = googleHttpLoadBalancer.region
      createdTime = googleHttpLoadBalancer.createdTime
      ipAddress = googleHttpLoadBalancer.ipAddress
      ipProtocol = googleHttpLoadBalancer.ipProtocol
      portRange = googleHttpLoadBalancer.portRange
      defaultService = googleHttpLoadBalancer.defaultService
      hostRules = googleHttpLoadBalancer.hostRules
      certificate = googleHttpLoadBalancer.certificate
      urlMapName = googleHttpLoadBalancer.urlMapName
      serverGroups = new HashSet<>()
    }

  }
}
