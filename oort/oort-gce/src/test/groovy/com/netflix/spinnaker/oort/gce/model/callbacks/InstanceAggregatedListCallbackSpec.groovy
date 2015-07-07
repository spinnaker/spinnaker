/*
 * Copyright 2015 Google, Inc.
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

package com.netflix.spinnaker.oort.gce.model.callbacks

import com.google.api.services.compute.model.HealthStatus
import com.netflix.spinnaker.oort.gce.model.GoogleInstance
import com.netflix.spinnaker.oort.model.HealthState
import spock.lang.Specification
import spock.lang.Unroll

class InstanceAggregatedListCallbackSpec extends Specification {
  @Unroll
  def "should build spinnaker GCE health state with properly-mapped instance status"() {
    when:
      def gceHealthState = InstanceAggregatedListCallback.buildGCEHealthState(instanceStatus)

    then:
      gceHealthState.type == "GCE"
      gceHealthState.state == spinnakerHealthState

    where:
      instanceStatus | spinnakerHealthState
      "PROVISIONING" | HealthState.Starting
      "STAGING"      | HealthState.Starting
      "RUNNING"      | HealthState.Unknown
      "STOPPING"     | HealthState.Down
      "TERMINATING"  | HealthState.Down
  }

  def "should not add any load balancer state if no load balancers report instance status"() {
    setup:
      def instanceNameToLoadBalancerHealthStatusMap = [
        instance1: new HashMap<String, List<HealthStatus>>(),
        instance2: [
          testlb1: []
        ]
      ]
      def healthStates = []

    when:
      InstanceAggregatedListCallback.buildAndAddLoadBalancerStateIfNecessary(
        "instance1", healthStates, instanceNameToLoadBalancerHealthStatusMap)

    then:
      healthStates == []

    when:
      InstanceAggregatedListCallback.buildAndAddLoadBalancerStateIfNecessary(
        "instance2", healthStates, instanceNameToLoadBalancerHealthStatusMap)

    then:
      healthStates == []
  }

  def "should report up and in service if one load balancer reports healthy"() {
    setup:
      def instanceNameToLoadBalancerHealthStatusMap = [
        instance1: [
          testlb1: [
            [
              healthState: "HEALTHY"
            ]
          ]
        ]
      ]

    when:
      def healthStates = []
      InstanceAggregatedListCallback.buildAndAddLoadBalancerStateIfNecessary(
        "instance1", healthStates, instanceNameToLoadBalancerHealthStatusMap)

    then:
      healthStates == [
        [
          type: "LoadBalancer",
          state: HealthState.Up,
          loadBalancers: [
            [
              loadBalancerName: "testlb1",
              instanceId: "instance1",
              state: "InService"
            ]
          ],
          instanceId: "instance1"
        ]
      ]
  }

  def "should report up and in service if two load balancers report healthy"() {
    setup:
      def instanceNameToLoadBalancerHealthStatusMap = [
        instance1: [
          testlb1: [
            [
              healthState: "HEALTHY"
            ]
          ],
          testlb2: [
            [
              healthState: "HEALTHY"
            ]
          ]
        ]
      ]

    when:
      def healthStates = []
      InstanceAggregatedListCallback.buildAndAddLoadBalancerStateIfNecessary(
        "instance1", healthStates, instanceNameToLoadBalancerHealthStatusMap)

    then:
      healthStates == [
        [
          type: "LoadBalancer",
          state: HealthState.Up,
          loadBalancers: [
            [
              loadBalancerName: "testlb1",
              instanceId: "instance1",
              state: "InService"
            ],
            [
              loadBalancerName: "testlb2",
              instanceId: "instance1",
              state: "InService"
            ]
          ],
          instanceId: "instance1"
        ]
      ]
  }

  def "should report down and out of service if any load balancer reports unhealthy"() {
    setup:
      def instanceNameToLoadBalancerHealthStatusMap = [
        instance1: [
          testlb1: [
            [
              healthState: "HEALTHY"
            ]
          ],
          testlb2: [
            [
              healthState: "UNHEALTHY"
            ]
          ]
        ]
      ]

    when:
      def healthStates = []
      InstanceAggregatedListCallback.buildAndAddLoadBalancerStateIfNecessary(
        "instance1", healthStates, instanceNameToLoadBalancerHealthStatusMap)

    then:
      healthStates == [
        [
          type: "LoadBalancer",
          state: HealthState.Down,
          loadBalancers: [
            [
              loadBalancerName: "testlb1",
              instanceId: "instance1",
              state: "InService"
            ],
            [
              loadBalancerName: "testlb2",
              instanceId: "instance1",
              state: "OutOfService",
              description: "No http health check defined. Traffic will still be sent to this instance."
            ]
          ],
          instanceId: "instance1"
        ]
      ]
  }

  def "should produce appropriate description depending on presence/absence of http health check"() {
    setup:
      def instanceNameToLoadBalancerHealthStatusMap = [
        instance1: [
          testlb1: [
            [
              healthState: "UNHEALTHY",
              hasHttpHealthCheck: true
            ]
          ],
          testlb2: [
            [
              healthState: "UNHEALTHY",
              hasHttpHealthCheck: false
            ]
          ]
        ]
      ]

    when:
      def healthStates = []
      InstanceAggregatedListCallback.buildAndAddLoadBalancerStateIfNecessary(
        "instance1", healthStates, instanceNameToLoadBalancerHealthStatusMap)

    then:
      healthStates == [
        [
          type: "LoadBalancer",
          state: HealthState.Down,
          loadBalancers: [
            [
              loadBalancerName: "testlb1",
              instanceId: "instance1",
              state: "OutOfService",
              description: "Instance has failed at least the Unhealthy Threshold number of health checks consecutively."
            ],
            [
              loadBalancerName: "testlb2",
              instanceId: "instance1",
              state: "OutOfService",
              description: "No http health check defined. Traffic will still be sent to this instance."
            ]
          ],
          instanceId: "instance1"
        ]
      ]
  }

  def "should report overall health state of up if one up vote"() {
    setup:
      def googleInstance = new GoogleInstance()

    when:
      googleInstance.setProperty("health", [
        [
          state: HealthState.Up
        ]
      ])

    then:
      googleInstance.getHealthState() == HealthState.Up
  }

  def "should report overall health state of up if one up vote and multiple unknown votes"() {
    setup:
      def googleInstance = new GoogleInstance()

    when:
      googleInstance.setProperty("health", [
        [
          state: HealthState.Up
        ],
        [
          state: HealthState.Unknown
        ],
        [
          state: HealthState.Unknown
        ]
      ])

    then:
      googleInstance.getHealthState() == HealthState.Up
  }

  def "should report overall health state of starting if any starting votes"() {
    setup:
      def googleInstance = new GoogleInstance()

    when:
      googleInstance.setProperty("health", [
        [
          state: HealthState.Up
        ],
        [
          state: HealthState.Unknown
        ],
        [
          state: HealthState.Starting
        ],
        [
          state: HealthState.OutOfService
        ],
        [
          state: HealthState.Down
        ]
      ])

    then:
      googleInstance.getHealthState() == HealthState.Starting
  }

  def "should report overall health state of down if no starting votes and at least one down vote"() {
    setup:
      def googleInstance = new GoogleInstance()

    when:
      googleInstance.setProperty("health", [
        [
          state: HealthState.Up
        ],
        [
          state: HealthState.Unknown
        ],
        [
          state: HealthState.OutOfService
        ],
        [
          state: HealthState.Down
        ]
      ])

    then:
      googleInstance.getHealthState() == HealthState.Down
  }

  def "should report overall health state of out of service if no starting votes, no down votes, and at least one out of service vote"() {
    setup:
      def googleInstance = new GoogleInstance()

    when:
      googleInstance.setProperty("health", [
        [
          state: HealthState.Up
        ],
        [
          state: HealthState.Unknown
        ],
        [
          state: HealthState.OutOfService
        ]
      ])

    then:
      googleInstance.getHealthState() == HealthState.OutOfService
  }

  def "should report overall health state of unknown if only unknown votes"() {
    setup:
      def googleInstance = new GoogleInstance()

    when:
      googleInstance.setProperty("health", [
        [
          state: HealthState.Unknown
        ],
        [
          state: HealthState.Unknown
        ]
      ])

    then:
      googleInstance.getHealthState() == HealthState.Unknown
  }

  def "should report overall health state of unknown if no votes of any kind"() {
    setup:
      def googleInstance = new GoogleInstance()

    when:
      googleInstance.setProperty("health", [])

    then:
      googleInstance.getHealthState() == HealthState.Unknown
  }
}
