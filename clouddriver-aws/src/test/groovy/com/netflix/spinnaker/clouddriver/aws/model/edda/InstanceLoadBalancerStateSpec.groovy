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

import spock.lang.Specification

class InstanceLoadBalancerStateSpec extends Specification {

  def 'should flatten loadBalancerName onto instance'() {
    setup:
    LoadBalancerInstanceState lbis = new LoadBalancerInstanceState(name: 'loadBalancer', instances: [
      new LoadBalancerInstance(instanceId: 'i-1', state: 'InService', description: 'N/A', reasonCode: 'N/A'),
      new LoadBalancerInstance(instanceId: 'i-2', state: 'InService', description: 'N/A', reasonCode: 'N/A')
    ])

    when:
    def instanceStates = InstanceLoadBalancerState.fromLoadBalancerInstanceState(lbis)

    then:
    instanceStates.size() == 2
    for (instanceState in instanceStates) {
      with (instanceState) {
        loadBalancerName == 'loadBalancer'
        state == 'InService'
        description == 'N/A'
        reasonCode == 'N/A'
      }
    }
    instanceStates[0].instanceId == 'i-1'
    instanceStates[1].instanceId == 'i-2'
  }
}
