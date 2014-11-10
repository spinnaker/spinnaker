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

package com.netflix.spinnaker.oort.aws.model.edda

import com.netflix.spinnaker.oort.model.Health
import com.netflix.spinnaker.oort.model.HealthState
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

  @CompileStatic(TypeCheckingMode.SKIP)
  static List<InstanceLoadBalancers> fromLoadBalancerInstanceState(List<LoadBalancerInstanceState> loadBalancers) {
    List<List<InstanceLoadBalancerState>> instances = loadBalancers.collect { InstanceLoadBalancerState.fromLoadBalancerInstanceState(it) }
    instances.flatten().groupBy { InstanceLoadBalancerState ilbs ->
      ilbs.instanceId
    }.collect { String instanceId, List<InstanceLoadBalancerState> lbs ->
      new InstanceLoadBalancers(instanceId: instanceId, state: lbs.every { it.state == 'InService' } ? HealthState.Up : HealthState.Down, loadBalancers: lbs)
    }
  }
}
