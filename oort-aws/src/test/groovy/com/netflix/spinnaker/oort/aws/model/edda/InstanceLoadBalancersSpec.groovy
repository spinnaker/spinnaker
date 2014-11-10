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

import spock.lang.Specification

class InstanceLoadBalancersSpec extends Specification {

  def 'should handle duplicated instances'() {
    setup:
    def lbis = [new LoadBalancerInstanceState(name: 'lb1', instances: [
          new LoadBalancerInstance(instanceId: 'i-1', state: 'InService', description: 'N/A', reasonCode: 'N/A')
        ]),
        new LoadBalancerInstanceState(name: 'lb2', instances: [
          new LoadBalancerInstance(instanceId: 'i-1', state: 'OutOfService', description: 'failboat sailed', reasonCode: 'FailBoat')
        ])]

    when:
    def ilb = InstanceLoadBalancers.fromLoadBalancerInstanceState(lbis)

    then:
    ilb.size() == 1
    with(ilb.first()) {
      instanceId == 'i-1'
      loadBalancers.size() == 2
      loadBalancers.loadBalancerName.sort() == ['lb1', 'lb2']
    }
  }
}
