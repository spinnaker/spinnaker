/*
 * Copyright 2025 Harness, Inc.
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

import com.netflix.spinnaker.clouddriver.model.LoadBalancerServerGroup

/**
 * Base view class for simple Google load balancers (Network, TCP, SSL, Internal).
 * Eliminates duplication of common view fields across simple load balancer types.
 */
abstract class GoogleSimpleLoadBalancerView extends GoogleLoadBalancerView {
  GoogleLoadBalancerType loadBalancerType
  GoogleLoadBalancingScheme loadBalancingScheme

  String name
  String account
  String region
  Long createdTime
  String ipAddress
  String ipProtocol
  String portRange

  Set<LoadBalancerServerGroup> serverGroups = new HashSet<>()

  /**
   * Populate common fields from a GoogleLoadBalancer.
   */
  protected void populateCommonFields(GoogleLoadBalancer loadBalancer) {
    this.name = loadBalancer.name
    this.account = loadBalancer.account
    this.region = loadBalancer.region
    this.createdTime = loadBalancer.createdTime
    this.ipAddress = loadBalancer.ipAddress
    this.ipProtocol = loadBalancer.ipProtocol
    this.portRange = loadBalancer.portRange
  }
}
