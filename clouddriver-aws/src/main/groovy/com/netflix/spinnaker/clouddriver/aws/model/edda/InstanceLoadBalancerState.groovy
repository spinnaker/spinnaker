/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.clouddriver.aws.model.edda

import com.netflix.spinnaker.clouddriver.model.HealthState
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode

@CompileStatic
@EqualsAndHashCode(cache = true)
class InstanceLoadBalancerState {
  final String instanceId
  final String loadBalancerType
  final String loadBalancerName
  final String state
  final String reasonCode
  final String description

  final HealthState healthState

  InstanceLoadBalancerState(String instanceId, String loadBalancerType, String loadBalancerName, String state, String reasonCode, String description) {
    this.instanceId = instanceId
    this.loadBalancerType = loadBalancerType
    this.loadBalancerName = loadBalancerName
    this.state = state
    this.reasonCode = reasonCode
    this.description = description
    this.healthState = deriveHealthState()
  }

  // Used for testing only
  static List<InstanceLoadBalancerState> fromLoadBalancerInstanceState(LoadBalancerInstanceState lbis) {
    lbis.instances.collect { new InstanceLoadBalancerState(it.instanceId, lbis.loadBalancerType, lbis.name, it.state, it.reasonCode, it.description)}
  }

  private HealthState deriveHealthState() {
    /* for ELBv1 we derive state from descriptions:
        * Instance registration is still in progress
        * Instance has not passed the configured HealthyThreshold number of health checks consecutively.
        * Instance has failed at least the UnhealthyThreshold number of health checks consecutively.
        * Instance is in the EC2 Availability Zone for which LoadBalancer is not configured to route traffic to.
        * Instance is not currently registered with the LoadBalancer.
     */
    if (state == 'InService') {
      return HealthState.Up
    }

    if (description == 'Instance registration is still in progress') {
      return HealthState.Starting
    }
    if (description == 'Instance is not currently registered with the LoadBalancer.') {
      return HealthState.OutOfService
    }
    if (description == 'Instance is in the EC2 Availability Zone for which LoadBalancer is not configured to route traffic to.') {
      return HealthState.Down
    }
    return HealthState.Down
  }
}
