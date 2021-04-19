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

package com.netflix.spinnaker.orca.clouddriver.tasks.instance

import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.ModelUtils
import com.netflix.spinnaker.orca.clouddriver.model.ServerGroup

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import org.slf4j.MDC
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import java.util.stream.Collectors

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class WaitForUpInstancesTaskSpec extends Specification {

  @Subject WaitForUpInstancesTask task = new WaitForUpInstancesTask() {
    @Override
    void verifyServerGroupsExist(StageExecution stage) {
      // do nothing
    }
  }

  void cleanup() {
    MDC.clear()
  }

  void "should check cluster to get server groups"() {
    given:
    def pipeline = PipelineExecutionImpl.newPipeline("orca")
    def response = ModelUtils.cluster([
      name        : "front50",
      serverGroups: [
        [
          region   : "us-east-1",
          name     : "front50-v001",
          asg      : [
            desiredCapacity: 1
          ],
          capacity : [
            desired: 1
          ],
          instances: [
            [
              health: [[state: "Up"]]
            ]
          ]
        ],
        [
          region   : "us-west-1",
          name     : "front50-v001",
          asg      : [
            desiredCapacity: 1
          ],
          capacity : [
            desired: 1
          ],
          instances: [
            [
              health: [[state: "Down"]]
            ]
          ]
        ]
      ]
    ])
    def response2 = ModelUtils.cluster([
      name        : "front50",
      serverGroups: [
        [
          region   : "us-west-1",
          name     : "front50-v001",
          asg      : [
            desiredCapacity: 1
          ],
          capacity : [
            desired: 1
          ],
          instances: [
            [
              health: [[state: "Up"]]
            ]
          ]
        ]
      ]
    ])
    task.cloudDriverService = Stub(CloudDriverService) {
      getClusterTyped(*_) >>> [response, response2]
    }

    and:
    def stage = new StageExecutionImpl(pipeline, "whatever", [
      "account.name"                  : "test",
      "targetop.asg.enableAsg.name"   : "front50-v000",
      "targetop.asg.enableAsg.regions": ["us-west-1", "us-east-1"]
    ])

    expect:
    task.execute(stage).status == ExecutionStatus.RUNNING

    when:
    stage.context."targetop.asg.enableAsg.name" = "front50-v001"

    then:
    task.execute(stage).status == ExecutionStatus.SUCCEEDED

  }

  @Unroll
  void 'should return false for hasSucceeded when server group is #serverGroup'() {
    setup:
    def instances = [ModelUtils.instance([health: [[state: 'Up']]])]

    expect:
    !task.hasSucceeded(new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", "", [:]), serverGroup, instances, null)

    where:
    serverGroup << [null, [:], [asg: [:], capacity: [:]]].collect {ModelUtils.serverGroup(it) }

  }

  @Unroll
  void 'should return #hasSucceeded for hasSucceeded when targetHealthyDeployPercentage is #percent and #healthy/#total instances are healthy'() {
    expect:
    def instances = []
    healthy.times {
      instances << ModelUtils.instance([health: [[state: 'Up']]])
    }
    def serverGroup = ModelUtils.serverGroup([
      asg     : [
        desiredCapacity: total
      ],
      capacity: [
        desired: total
      ]
    ])
    hasSucceeded == task.hasSucceeded(
      new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", "", [
        targetHealthyDeployPercentage: percent
      ]
      ), serverGroup, instances, null
    )

    where:
    percent | healthy | total || hasSucceeded

    // 100 percent
    100     | 1       | 1     || true
    100     | 0       | 0     || true
    100     | 1       | 2     || false

    // zero percent (should always return true)
    0       | 1       | 2     || true
    0       | 0       | 100   || true
    100     | 0       | 0     || true

    // >= checks
    89      | 9       | 10    || true
    90      | 9       | 10    || true
    90      | 8       | 10    || false
    91      | 9       | 10    || true
    95      | 9       | 10    || false

    // verify round
    90      | 10      | 11    || true
    90      | 8       | 9     || true
    95      | 8       | 9     || false
  }

  @Unroll
  void 'should return #hasSucceeded for hasSucceeded when desiredPercentage is #percent and #healthy/#total instances are healthy'() {
    expect:
    def instances = []
    healthy.times {
      instances << ModelUtils.instance([health: [[state: 'Up']]])
    }
    def serverGroup = ModelUtils.serverGroup([
      asg     : [
        desiredCapacity: total
      ],
      capacity: [
        min    : min,
        desired: total,
        max    : max
      ]
    ])
    hasSucceeded == task.hasSucceeded(
      new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", "", [
        desiredPercentage: percent
      ]
      ), serverGroup, instances, null
    )

    where:
    percent | healthy | total | min | max || hasSucceeded

    // 100 percent
    100     | 1       | 1     | 1   | 1   || true
    100     | 0       | 0     | 0   | 0   || true
    100     | 2       | 2     | 0   | 2   || true

    // zero percent (should always return true)
    0       | 1       | 2     | 1   | 2   || true
    0       | 0       | 100   | 1   | 100 || true

    // >= checks
    89      | 9       | 10    | 10  | 10  || true
    90      | 9       | 10    | 10  | 10  || true
    90      | 8       | 10    | 10  | 10  || false
    91      | 9       | 10    | 10  | 10  || false

    // verify round
    90      | 10      | 11    | 10  | 11  || true
    90      | 8       | 9     | 9   | 9   || false

  }

  void 'should succeed when ASG desired size is reached, even though snapshotCapacity is larger'() {
    when:
    def serverGroup = ModelUtils.serverGroup([
      asg     : [
        desiredCapacity: 2
      ],
      capacity: [
        desired: 2
      ]
    ])
    def context = [
      snapshotCapacity: [desiredCapacity: 5]
    ]

    def instances = [
      ModelUtils.instance([health: [[state: 'Up']]]),
      ModelUtils.instance([health: [[state: 'Up']]])
    ]

    then:
    task.hasSucceeded(
      new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", "", context),
      serverGroup, instances, null
    )
  }

  void 'should succeed when spotPrice is set and the deployment strategy is None, even if no instances are up'() {
    when:
    def serverGroup = ModelUtils.serverGroup([
      asg         : [
        desiredCapacity: 2
      ],
      capacity    : [
        desired: 2
      ],
      launchConfig: [
        spotPrice: 0.87
      ]
    ])
    def context = [
      capacity: [desired: 5],
      strategy: ''
    ]

    def instances = [
    ]

    then:
    task.hasSucceeded(
      new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", "", context),
      serverGroup, instances, null
    )
  }

  @Unroll
  void 'should return #result for #healthy instances when #description'() {
    when:
    def serverGroup = ModelUtils.serverGroup([
      asg     : [
        desiredCapacity: asg
      ],
      capacity: [
        desired: asg
      ]
    ])

    def context = [:]
    if (configured) {
      context.capacity = [desired: configured]
    }
    if (snapshot) {
      context.capacitySnapshot = [desiredCapacity: snapshot]
    }

    def instances = []
    healthy.times {
      instances << ModelUtils.instance([health: [[state: 'Up']]])
    }

    then:
    result == task.hasSucceeded(
      new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", "", context),
      serverGroup, instances, null
    )

    where:
    result || healthy | configured | snapshot | asg | description
    true   || 3       | 4          | 4        | 3   | 'using source capacity of 3, ignoring snapshot capacity and configured capacity'
    false  || 3       | 3          | null     | 4   | 'using source capacity of 4 with no snapshot, ignoring configured capacity'
    true   || 3       | 4          | 4        | 3   | 'using source capacity of 4, snapshot ignored because it is larger than actual desired capacity'
    true   || 2       | null       | null     | 2   | 'source not specified, falling back to ASG desired size of 2'
    false  || 2       | null       | null     | 3   | 'source not specified, falling back to ASG desired size of 3'
    false  || 2       | 2          | null     | 3   | 'not using source, using configured size of 3, ignoring source size of 2'
    true   || 3       | 2          | null     | 3   | 'not using source, using configured size of 2, ignoring source size of 3'
  }

  @Unroll
  void 'calculates targetDesired based on configured capacity or servergroup depending on value'() {
    when:
    def serverGroup = ModelUtils.serverGroup([
      asg     : [
        desiredCapacity: asg.desired
      ],
      capacity: [
        min    : asg.min,
        max    : asg.max,
        desired: asg.desired
      ]
    ])

    def context = [:]

    if (snapshot) {
      context.capacitySnapshot = [desiredCapacity: snapshot]
    }

    if (configured) {
      context.capacity = [
        min    : configured.min,
        max    : configured.max,
        desired: configured.desired,
        pinned : configured.pinned
      ]
    }

    def instances = []
    healthy.times {
      instances << ModelUtils.instance([health: [[state: 'Up']]])
    }

    then:
    result == task.hasSucceeded(
      new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", "", context),
      serverGroup, instances, null
    )

    where:
    result || snapshot | healthy | asg                          | configured
    false  || null     | 2       | [min: 3, max: 3, desired: 3] | null

    // configured is used if present and min == max == desired
    true   || null     | 2       | [min: 3, max: 3, desired: 3] | [min: 2, max: 2, desired: 2, pinned: true]
    true   || null     | 2       | [min: 3, max: 3, desired: 3] | [min: "2", max: "2", desired: "2", pinned: "true"]

    // configured is used if current allows autoscaling but configured doesn't
    true   || null     | 2       | [min: 3, max: 3, desired: 3] | [min: 2, max: 500, desired: 2]
    true   || null     | 2       | [min: 3, max: 3, desired: 3] | [min: "2", max: "500", desired: "2"]
    true   || null     | 2       | [min: 5, max: 5, desired: 5] | [min: 1, max: 5, desired: 2]
    true   || null     | 2       | [min: 5, max: 5, desired: 5] | [min: "1", max: "5", desired: "2"]

    // configured and current are used over snapshot
    false  || 3        | 3       | [min: 5, max: 5, desired: 5] | [min: 5, max: 5, desired: 5]
    false  || 3        | 3       | [min: 5, max: 5, desired: 5] | [min: "5", max: "5", desired: "5"]
    false  || 3        | 3       | [min: 5, max: 5, desired: 5] | null
    true   || 3        | 5       | [min: 5, max: 5, desired: 5] | null

    // sourceCapacity is ignored if > than the calculated target due to a scale down corner case
    true   || 4        | 3       | [min: 3, max: 3, desired: 3] | null
    false  || 4        | 3       | [min: 3, max: 3, desired: 3] | [min: 4, max: 4, desired: 4]
    false  || 4        | 3       | [min: 3, max: 3, desired: 3] | [min: "4", max: "4", desired: "4"]
    true   || 5        | 4       | [min: 3, max: 3, desired: 3] | [min: 4, max: 4, desired: 4]
    true   || 5        | 4       | [min: 3, max: 3, desired: 3] | [min: "4", max: "4", desired: "4"]
    true   || 5        | 4       | [min: 3, max: 3, desired: 3] | [min: 4, max: 4, desired: "4"]
  }

  @Unroll
  void 'should throw an exception if targetHealthyDeployPercentage is not between 0 and 100'() {
    when:
    task.hasSucceeded(
        new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", "", [
            targetHealthyDeployPercentage: percent
        ]
        ), ModelUtils.serverGroup([asg: [desiredCapacity: 2], capacity: [desired: 2]]), [], null
    )

    then:
    thrown(NumberFormatException)

    where:
    percent << [-1, 101]
  }

  @Unroll
  void 'should not trust an ASG desired capacity of zero unless zero has been seen 12 times'() {
    expect:
    def context = [
      zeroDesiredCapacityCount: counter,
      capacitySnapshot        : [
        desiredCapacity: snapshotCapacity
      ]
    ]
    def serverGroup = ModelUtils.serverGroup([asg: [desiredCapacity: 0], capacity: [desired: 0]])
    hasSucceeded == task.hasSucceeded(new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", "", context), serverGroup, [], null)

    where:
    hasSucceeded || counter | snapshotCapacity
    true         || 12      | 1
    true         || 13      | 1
    false        || 11      | 1
    true         || 0       | 0
    true         || 1       | 0
  }

  @Unroll
  void 'should succeed as #hasSucceeded based on instance providers #healthProviderNames for instances #instances'() {
    given:
    def serverGroup = ModelUtils.serverGroup([asg: [desiredCapacity: desiredCapacity], capacity: [desired: desiredCapacity]])
    def instanceList = instances.collect { ModelUtils.instance(it) }

    expect:
    hasSucceeded == task.hasSucceeded(
      new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", "", [:]), serverGroup, instanceList, healthProviderNames)

    where:
    hasSucceeded || desiredCapacity | healthProviderNames | instances
    true         || 0               | null                | []
    true         || 0               | ['a']               | []
    true         || "1"             | ['a']               | [[health: [[type: 'a', state: "Up"]]]]
    false        || 1               | null                | [[health: [[type: 'a', state: "Down"]]]]
    true         || 1               | null                | [[health: [[type: 'a', state: "Up"]]]]
    false        || 1               | ['a']               | [[health: [[type: 'a', state: "Down"]]]]
    true         || 1               | ['a']               | [[health: [[type: 'a', state: "Up"]]]]
    false        || 1               | ['b']               | [[health: [[type: 'a', state: "Down"]]]]
    false        || 1               | ['b']               | [[health: [[type: 'a', state: "Up"]]]]
    true         || 1               | ['Amazon']          | [[health: [[type: 'Amazon', healthClass: 'platform', state: "Unknown"]]]]
    false        || 1               | ['Amazon']          | [[health: [[type: 'Amazon', state: "Down"]]]]
    false        || 1               | null                | [[health: [[type: 'Amazon', state: "Up"], [type: 'targetGroup', state: "Starting"]]]]
    false        || 1               | null                | [[health: [[type: 'Amazon', state: "Unknown"], [type: 'targetGroup', state: "Starting"], [type: 'd', state: "Up"]]]]
    true         || 1               | ['Amazon']          | [[health: [[type: 'Amazon', state: "Up"], [type: 'targetGroup', state: "Starting"]]]]
    true         || 1               | ['GCE']             | [[health: [[type: 'GCE', healthClass: 'platform', state: "Unknown"]]]]
    false        || 1               | ['GCE']             | [[health: [[type: 'GCE', state: "Down"]]]]

    // multiple health providers
    true         || 1               | ['Amazon']          | [[health: [[type: 'Amazon', healthClass: 'platform', state: "Unknown"], [type: 'b', state: "Down"]]]]
    true         || 1               | ['GCE']             | [[health: [[type: 'GCE', healthClass: 'platform', state: "Unknown"], [type: 'b', state: "Down"]]]]
    true         || 1               | null                | [[health: [[type: 'a', state: "Up"], [type: 'b', state: "Up"]]]]
    false        || 1               | null                | [[health: [[type: 'a', state: "Down"], [type: 'b', state: "Down"]]]]
    false        || 1               | null                | [[health: [[type: 'a', state: "Up"], [type: 'b', state: "Down"]]]]
    true         || 1               | ['a']               | [[health: [[type: 'a', state: "Up"], [type: 'b', state: "Down"]]]]
    false        || 1               | ['b']               | [[health: [[type: 'a', state: "Up"], [type: 'b', state: "Down"]]]]
    false        || 1               | ['a', 'b']          | [[health: [[type: 'a', state: "Up"], [type: 'b', state: "Down"]]]]
    true         || 1               | ['a']               | [[health: [[type: 'a', state: "Up"], [type: 'b', state: "Unknown"]]]]
    false        || 1               | ['b']               | [[health: [[type: 'a', state: "Up"], [type: 'b', state: "Unknown"]]]]
    true         || 1               | ['a', 'b']          | [[health: [[type: 'a', state: "Up"], [type: 'b', state: "Unknown"]]]]
    false        || 1               | ['a']               | [[health: [[type: 'a', state: "Unknown"], [type: 'b', state: "Down"]]]]
    false        || 1               | ['b']               | [[health: [[type: 'a', state: "Unknown"], [type: 'b', state: "Down"]]]]
    false        || 1               | ['a', 'b']          | [[health: [[type: 'a', state: "Unknown"], [type: 'b', state: "Down"]]]]

    // multiple instances
    true         || 2               | null                | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Up"]]]]
    false        || 2               | null                | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Down"]]]]
    false        || 2               | null                | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Down"]]]]
    true         || 2               | null                | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'b', state: "Up"]]]]
    false        || 2               | null                | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'b', state: "Down"]]]]
    false        || 2               | null                | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'b', state: "Down"]]]]
    true         || 2               | ['a']               | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Up"]]]]
    false        || 2               | ['a']               | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Down"]]]]
    false        || 2               | ['a']               | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Down"]]]]
    false        || 2               | ['a']               | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'b', state: "Up"]]]]
    false        || 2               | ['a']               | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'b', state: "Down"]]]]
    false        || 2               | ['a']               | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'b', state: "Down"]]]]
    true         || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Up"]]]]
    false        || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Down"]]]]
    false        || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Down"]]]]
    true         || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'b', state: "Up"]]]]
    false        || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'b', state: "Down"]]]]
    false        || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'b', state: "Down"]]]]

    // multiple instances with multiple health providers
    true         || 2               | null                | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Up"]]]]
    false        || 2               | null                | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Down"]]]]
    true         || 2               | null                | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Unknown"]]]]
    false        || 2               | null                | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Down"], [type: 'b', state: "Down"]]]]
    false        || 2               | null                | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Down"], [type: 'b', state: "Unknown"]]]]
    false        || 2               | null                | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Up"]]]]
    false        || 2               | null                | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Down"]]]]
    false        || 2               | null                | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Unknown"]]]]
    false        || 2               | null                | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Down"], [type: 'b', state: "Down"]]]]
    false        || 2               | null                | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Down"], [type: 'b', state: "Unknown"]]]]
    true         || 2               | ['a']               | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Up"]]]]
    true         || 2               | ['a']               | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Down"]]]]
    true         || 2               | ['a']               | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Unknown"]]]]
    false        || 2               | ['a']               | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Down"], [type: 'b', state: "Down"]]]]
    false        || 2               | ['a']               | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Down"], [type: 'b', state: "Unknown"]]]]
    false        || 2               | ['a']               | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Up"]]]]
    false        || 2               | ['a']               | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Down"]]]]
    false        || 2               | ['a']               | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Unknown"]]]]
    false        || 2               | ['a']               | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Down"], [type: 'b', state: "Down"]]]]
    false        || 2               | ['a']               | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Down"], [type: 'b', state: "Unknown"]]]]
    true         || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Up"]]]]
    false        || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Down"]]]]
    true         || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Unknown"]]]]
    false        || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Down"], [type: 'b', state: "Down"]]]]
    false        || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Up"]]], [health: [[type: 'a', state: "Down"], [type: 'b', state: "Unknown"]]]]
    false        || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Up"]]]]
    false        || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Down"]]]]
    false        || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Up"], [type: 'b', state: "Unknown"]]]]
    false        || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Down"], [type: 'b', state: "Down"]]]]
    false        || 2               | ['a', 'b']          | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Down"], [type: 'b', state: "Unknown"]]]]

    // health providers ignored
    true         || 2               | []                  | [[health: [[type: 'a', state: "Down"]]], [health: [[type: 'a', state: "Down"], [type: 'b', state: "Unknown"]]]]
    false        || 2               | []                  | [[health: [[type: 'a', state: "Down"], [type: 'b', state: "Unknown"]]]]
  }

  @Unroll
  void 'should extract target server group capacity from kato.tasks'() {
    given:
    def stage = stage {
      context = [
        "kato.tasks": katoTasks
      ]
    }

    def serverGroup = ModelUtils.serverGroup([name: "app-v001", region: "us-west-2"])

    expect:
    WaitForUpInstancesTask.getInitialTargetCapacity(stage, serverGroup) == expectedInitialTargetCapacity

    where:
    katoTasks                                               || expectedInitialTargetCapacity
    null                                                    || null
    []                                                      || null
    [[:]]                                                   || null
    [[resultObjects: [[deployments: [
        deployment("app-v001", "us-west-2", 0, 1, 1),
        deployment("app-v002", "us-west-2", 0, 2, 2),
        deployment("app-v001", "us-east-1", 0, 3, 3),]]]]]  || ServerGroup.Capacity.builder().min(0).max(1).desired(1).build()     // should match on serverGroupName and location
    [[resultObjects: [[deployments: [
        deployment("app-v001", "us-west-2", 0, 1, 1)]]]],
     [resultObjects: [[deployments: [
         deployment("app-v001", "us-west-2", 0, 2, 2)]]]],] || ServerGroup.Capacity.builder().min(0).max(2).desired(2).build()     // should look for most recent katoTask result object
  }

  def 'reverse stream'() {
    expect:
    WaitForUpInstancesTask.reverseStream(list).collect(Collectors.toList()) == list.reverse()

    where:
    scenario | list
    'empty'  | []
    '1'      | [1]
    'many'   | [1, 2, 4, 3]
  }

  @Unroll
  void 'should favor initial target capacity if current capacity is 0/0/0'() {
    given:
    def stage = stage {
      context = [
        "kato.tasks": katoTasks
      ]
    }

    def serverGroup = ModelUtils.serverGroup([name: "app-v001", region: "us-west-2", capacity: serverGroupCapacity])

    def expectedCapacity = ServerGroup.Capacity.builder().min(cap.min).max(cap.max).desired(cap.desired).build()

    and:
    MDC.put("taskStartTime", taskStartTime.toString())

    expect:
    WaitForUpInstancesTask.getServerGroupCapacity(stage, serverGroup) == expectedCapacity

    where:
    katoTasks                                                                          | taskStartTime | serverGroupCapacity            || cap
    null                                                                               | startTime(0)  | [min: 0, max: 0, desired: 0]   || [min: 0, max: 0, desired: 0]
    [[resultObjects: [[deployments: [deployment("app-v001", "us-west-2", 0, 1, 1)]]]]] | startTime(9)  | [min: 0, max: 0, desired: 0]   || [min: 0, max: 1, desired: 1]   // should take initial capacity b/c max = 0
    [[resultObjects: [[deployments: [deployment("app-v001", "us-west-2", 0, 1, 1)]]]]] | startTime(9)  | [min: 0, max: 400, desired: 0] || [min: 0, max: 1, desired: 1]   // should take initial capacity b/c desired = 0
    [[resultObjects: [[deployments: [deployment("app-v001", "us-west-2", 0, 1, 1)]]]]] | startTime(9)  | [min: 0, max: 2, desired: 2]   || [min: 0, max: 2, desired: 2]   // should take current capacity b/c max > 0
    [[resultObjects: [[deployments: [deployment("app-v001", "us-west-2", 0, 1, 1)]]]]] | startTime(11) | [min: 0, max: 0, desired: 0]   || [min: 0, max: 0, desired: 0]   // should take current capacity b/c timeout
  }

  static Map deployment(String serverGroupName, String location, int min, int max, int desired) {
    return [
      serverGroupName: serverGroupName, location: location, capacity: [min: min, max: max, desired: desired]
    ]
  }

  static Long startTime(int minutesOld) {
    return System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(minutesOld)
  }
}
