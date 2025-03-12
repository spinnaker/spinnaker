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

  static HealthState deriveInstanceHealthState(List<InstanceLoadBalancerState> instanceLoadBalancerStates) {
    instanceLoadBalancerStates.any { it.healthState == HealthState.Starting } ? HealthState.Starting :
      instanceLoadBalancerStates.any { it.healthState == HealthState.Down } ? HealthState.Down :
        instanceLoadBalancerStates.any { it.healthState == HealthState.OutOfService } ? HealthState.OutOfService :
          HealthState.Up
  }

  @CompileStatic(TypeCheckingMode.SKIP)
  static List<InstanceLoadBalancers> fromLoadBalancerInstanceState(List<LoadBalancerInstanceState> loadBalancers) {
    List<List<InstanceLoadBalancerState>> instances = loadBalancers.collect { InstanceLoadBalancerState.fromLoadBalancerInstanceState(it) }
    return fromInstanceLoadBalancerStates(instances.flatten())
  }

  static List<InstanceLoadBalancers> fromInstanceLoadBalancerStates(List<InstanceLoadBalancerState> instanceLoadBalancerStates) {
    instanceLoadBalancerStates.groupBy { InstanceLoadBalancerState ilbs ->
      ilbs.instanceId
    }.collect { String instanceId, List<InstanceLoadBalancerState> lbs ->
      new InstanceLoadBalancers(instanceId: instanceId, state: deriveInstanceHealthState(lbs), loadBalancers: lbs)
    }

  }
}
