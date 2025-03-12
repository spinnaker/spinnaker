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
import spock.lang.Specification
import spock.lang.Unroll

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

  def 'should return up if all load balancers are in service, down if any are out of service'() {
    setup:
    def lbis = [new LoadBalancerInstanceState(name: 'lb1', instances: [
        new LoadBalancerInstance(instanceId: 'i-1', state: 'InService', description: 'N/A', reasonCode: 'N/A'),
        new LoadBalancerInstance(instanceId: 'i-2', state: 'InService', description: 'N/A', reasonCode: 'N/A')
        ]),
        new LoadBalancerInstanceState(name: 'lb2', instances: [
          new LoadBalancerInstance(instanceId: 'i-1',
            state: 'OutOfService',
            description: 'Instance has failed at least the UnhealthyThreshold number of health checks consecutively.',
            reasonCode: 'FailBoat'),
          new LoadBalancerInstance(instanceId: 'i-2', state: 'InService', description: 'N/A', reasonCode: 'N/A')
        ])]

    when:
    def ilb = InstanceLoadBalancers.fromLoadBalancerInstanceState(lbis)

    then:
    ilb.size() == 2
    ilb.find {it.instanceId == 'i-1' }.state == HealthState.Down
    ilb.find {it.instanceId == 'i-2' }.state == HealthState.Up
  }

  @Unroll
  def 'should derive health state from message if not in service'() {
    setup:
    def lbis = [new LoadBalancerInstanceState(name: 'lb1', instances: [
      new LoadBalancerInstance(instanceId: 'i-1',
        state: 'OutOfService',
        description: description
      )
    ])]

    when:
    def ilb = InstanceLoadBalancers.fromLoadBalancerInstanceState(lbis)

    then:
    ilb.size() == 1
    ilb[0].state == state

    where:
    state                     | description
    HealthState.Down          | 'Instance has failed at least the UnhealthyThreshold number of health checks consecutively.'
    HealthState.Down          | 'Instance is in the EC2 Availability Zone for which LoadBalancer is not configured to route traffic to.'
    HealthState.OutOfService  | 'Instance is not currently registered with the LoadBalancer.'
    HealthState.Starting      | 'Instance registration is still in progress'
    HealthState.Down          | 'Instance has not passed the configured HealthyThreshold number of health checks consecutively.'
  }

  def 'down load balancer should override out of service and up'() {
    def lbis = [
      new LoadBalancerInstanceState(name: 'up', instances: [
        new LoadBalancerInstance(instanceId: 'i-1', state: 'InService', description: 'N/A', reasonCode: 'N/A')
      ]),
      new LoadBalancerInstanceState(name: 'outOfService', instances: [
        new LoadBalancerInstance(instanceId: 'i-1', state: 'OutOfService', description: 'Instance is not currently registered with the LoadBalancer.', reasonCode: 'N/A')
      ]),
      new LoadBalancerInstanceState(name: 'down', instances: [
        new LoadBalancerInstance(instanceId: 'i-1', state: 'OutOfService', description: 'Instance has not passed the configured HealthyThreshold number of health checks consecutively.', reasonCode: 'N/A')
      ])
    ]

    when:
    def ilb = InstanceLoadBalancers.fromLoadBalancerInstanceState(lbis)

    then:
    ilb.size() == 1
    ilb[0].state == HealthState.Down
  }

  def 'out of service load balancer should override up'() {
    def lbis = [
      new LoadBalancerInstanceState(name: 'up', instances: [
        new LoadBalancerInstance(instanceId: 'i-1', state: 'InService', description: 'N/A', reasonCode: 'N/A')
      ]),
      new LoadBalancerInstanceState(name: 'outOfService', instances: [
        new LoadBalancerInstance(instanceId: 'i-1', state: 'OutOfService', description: 'Instance is not currently registered with the LoadBalancer.', reasonCode: 'N/A')
      ])
    ]

    when:
    def ilb = InstanceLoadBalancers.fromLoadBalancerInstanceState(lbis)

    then:
    ilb.size() == 1
    ilb[0].state == HealthState.OutOfService
  }

  def 'starting load balancer should override all other states'() {
    def lbis = [
      new LoadBalancerInstanceState(name: 'up', instances: [
        new LoadBalancerInstance(instanceId: 'i-1', state: 'InService', description: 'N/A', reasonCode: 'N/A')
      ]),
      new LoadBalancerInstanceState(name: 'starting', instances: [
        new LoadBalancerInstance(instanceId: 'i-1', state: 'OutOfService', description: 'Instance registration is still in progress', reasonCode: 'N/A')
      ]),
      new LoadBalancerInstanceState(name: 'down', instances: [
        new LoadBalancerInstance(instanceId: 'i-1', state: 'OutOfService', description: 'Instance has not passed the configured HealthyThreshold number of health checks consecutively.', reasonCode: 'N/A')
      ])
    ]

    when:
    def ilb = InstanceLoadBalancers.fromLoadBalancerInstanceState(lbis)

    then:
    ilb.size() == 1
    ilb[0].state == HealthState.Starting
  }

}
