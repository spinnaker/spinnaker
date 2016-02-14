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

import com.netflix.spinnaker.clouddriver.model.Health
import com.netflix.spinnaker.clouddriver.model.HealthState
import groovy.transform.CompileStatic
import groovy.transform.EqualsAndHashCode
import groovy.transform.Immutable
import groovy.transform.TypeCheckingMode

@CompileStatic
@Immutable
@EqualsAndHashCode(cache = true)
class InstanceLoadBalancers implements Health {
  public static final String HEALTH_TYPE = 'LoadBalancer'
  public String getType() {
    HEALTH_TYPE
  }
  String instanceId
  HealthState state
  List<InstanceLoadBalancerState> loadBalancers

  static HealthState deriveHealthState(InstanceLoadBalancerState state) {
    /* Known descriptions:
        * Instance registration is still in progress
        * Instance has not passed the configured HealthyThreshold number of health checks consecutively.
        * Instance has failed at least the UnhealthyThreshold number of health checks consecutively.
        * Instance is in the EC2 Availability Zone for which LoadBalancer is not configured to route traffic to.
        * Instance is not currently registered with the LoadBalancer.
     */
    if (state.state == 'InService') {
      return HealthState.Up
    }
    if (state.description == 'Instance registration is still in progress') {
      return HealthState.Starting
    }
    if (state.description == 'Instance is not currently registered with the LoadBalancer.') {
      return HealthState.OutOfService
    }
    if (state.description == 'Instance is in the EC2 Availability Zone for which LoadBalancer is not configured to route traffic to.') {
      return HealthState.Down
    }
    return HealthState.Down
  }

  static HealthState deriveInstanceHealthState(List<InstanceLoadBalancerState> instanceLoadBalancerStates) {
    instanceLoadBalancerStates.any { deriveHealthState(it) == HealthState.Starting } ? HealthState.Starting :
      instanceLoadBalancerStates.any { deriveHealthState(it) == HealthState.Down } ? HealthState.Down :
        instanceLoadBalancerStates.any { deriveHealthState(it) == HealthState.OutOfService } ? HealthState.OutOfService :
          HealthState.Up
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  static List<InstanceLoadBalancers> fromLoadBalancerInstanceState(List<LoadBalancerInstanceState> loadBalancers) {
    List<List<InstanceLoadBalancerState>> instances = loadBalancers.collect { InstanceLoadBalancerState.fromLoadBalancerInstanceState(it) }
    instances.flatten().groupBy { InstanceLoadBalancerState ilbs ->
      ilbs.instanceId
    }.collect { String instanceId, List<InstanceLoadBalancerState> lbs ->
      new InstanceLoadBalancers(instanceId: instanceId, state: deriveInstanceHealthState(lbs), loadBalancers: lbs)
    }
  }
}
