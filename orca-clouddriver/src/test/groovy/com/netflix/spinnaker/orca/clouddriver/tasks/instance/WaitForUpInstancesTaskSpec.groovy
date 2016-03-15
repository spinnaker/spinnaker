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

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForUpInstancesTaskSpec extends Specification {

  @Subject task = new WaitForUpInstancesTask() {
    @Override
    void verifyServerGroupsExist(Stage stage) {
      // do nothing
    }
  }

  def mapper = new OrcaObjectMapper()

  void "should check cluster to get server groups"() {
    given:
    def pipeline = new Pipeline()
    task.objectMapper = mapper
    def response = new Response('oort', 200, 'ok', [], new TypedString(mapper.writeValueAsString([
          name        : "front50",
          serverGroups: [
            [
              region   : "us-east-1",
              name     : "front50-v000",
              asg      : [
                desiredCapacity: 1
              ],
              capacity : [
                desired: 1
              ],
              instances: [
                [
                  health: [ [ state : "Up"] ]
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
                  health: [ [ state : "Down"] ]
                ]
              ]
            ]
          ]
        ])))
    def response2 = new Response('oort', 200, 'ok', [], new TypedString(mapper.writeValueAsString([
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
                    health: [ [ state : "Up"] ]
                  ]
                ]
              ]
            ]
          ])))
    task.oortService = Stub(OortService) {
      getCluster(*_) >>> [response, response2]
    }

    and:
    def stage = new PipelineStage(pipeline, "whatever", [
      "account.name"                  : "test",
      "targetop.asg.enableAsg.name"   : "front50-v000",
      "targetop.asg.enableAsg.regions": ["us-west-1"]
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
    def instances = [ [ health: [ [state: 'Up'] ] ] ]

    expect:
    !task.hasSucceeded(new PipelineStage(new Pipeline(), "", "", [:]), serverGroup, instances, null)

    where:
    serverGroup << [null, [:], [asg: [], capacity : [],]]

  }

  @Unroll
  void 'should return #hasSucceeded for hasSucceeded when targetHealthyDeployPercentage is #percent and #healthy/#total instances are healthy'() {
    expect:
    def instances = []
    (1..healthy).each {
      instances << [ health: [ [state: 'Up'] ] ]
    }
    def serverGroup = [
      asg: [
        desiredCapacity: total
      ],
      capacity : [
        desired: total
      ]
    ]
    hasSucceeded == task.hasSucceeded(
      new PipelineStage(new Pipeline(), "", "", [
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

    // >= checks
    89      | 9       | 10    || true
    90      | 9       | 10    || true
    90      | 8       | 10    || false
    91      | 9       | 10    || false

    // verify ceiling
    90      | 10      | 11    || true
    90      | 8       | 9     || false

  }

  void 'should succeed when ASG desired size is reached, even though snapshotCapacity is larger'() {
    when:
    def serverGroup = [
      asg: [
        desiredCapacity: 2
      ],
      capacity : [
        desired: 2
      ]
    ]
    def context = [
      snapshotCapacity: [ desiredCapacity: 5 ]
    ]

    def instances = [
      [ health: [ [state: 'Up'] ] ],
      [ health: [ [state: 'Up'] ] ]
    ]

    then:
    task.hasSucceeded(
      new PipelineStage(new Pipeline(), "", "", context),
      serverGroup, instances, null
    )
  }

  @Unroll
  void 'should return #result for #healthy instances when #description'() {
    when:
    def serverGroup = [
      asg: [
        desiredCapacity: asg
      ],
      capacity : [
        desired: asg
      ]
    ]

    def context = [
      source: [ useSourceCapacity: useSource ],
    ]
    if (configured) {
      context.capacity = [ desired: configured ]
    }
    if (snapshot) {
      context.capacitySnapshot = [ desiredCapacity: snapshot ]
    }

    def instances = []
    (1..healthy).each {
      instances << [ health: [ [state: 'Up'] ] ]
    }

    then:
    result == task.hasSucceeded(
      new PipelineStage(new Pipeline(), "", "", context),
      serverGroup, instances, null
    )

    where:
    result || useSource | healthy | configured | snapshot | asg | description
    true   || true      | 3       | 4          | 4        | 3   | 'using source capacity of 3, ignoring snapshot capacity and configured capacity'
    false  || true      | 3       | 3          | null     | 4   | 'using source capacity of 4 with no snapshot, ignoring configured capacity'
    true   || true      | 3       | 4          | 3        | 5   | 'using source capacity of 4, snapshot overrides to account for autoscaling'
    true   || true      | 3       | 4          | 4        | 3   | 'using source capacity of 4, snapshot ignored because it is larger than actual desired capacity'
    true   || false     | 2       | null       | null     | 2   | 'source not specified, falling back to ASG desired size of 2'
    false  || false     | 2       | null       | null     | 3   | 'source not specified, falling back to ASG desired size of 3'
    false  || false     | 2       | 2          | null     | 3   | 'not using source, using configured size of 3, ignoring source size of 2'
    true   || false     | 3       | 2          | null     | 3   | 'not using source, using configured size of 2, ignoring source size of 3'
  }

  @Unroll
  void 'should throw an exception if targetHealthyDeployPercentage is not between 0 and 100'() {
    when:
    task.hasSucceeded(
      new PipelineStage(new Pipeline(), "", "", [
        targetHealthyDeployPercentage: percent
      ]
      ), [asg: [desiredCapacity: 2], capacity: [desired: 2]], [], null
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
        capacitySnapshot: [
            desiredCapacity: snapshotCapacity
        ]
    ]
    def serverGroup = [asg: [desiredCapacity: 0], capacity : [desired: 0]]
    hasSucceeded == task.hasSucceeded(new PipelineStage(new Pipeline(), "", "", context), serverGroup, [], null)

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
    expect:
    hasSucceeded == task.hasSucceeded(
      new PipelineStage(new Pipeline(), "", "", [:]), [asg: [desiredCapacity: desiredCapacity], capacity : [desired: desiredCapacity]], instances, healthProviderNames
    )

    where:
    hasSucceeded || desiredCapacity | healthProviderNames | instances
    true         || 0               | null                | []
    true         || 0               | ['a']               | []
    true         || "1"             | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ] ]
    false        || 1               | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ] ]
    true         || 1               | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ] ]
    false        || 1               | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ] ]
    true         || 1               | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ] ]
    false        || 1               | ['b']               | [ [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || 1               | ['b']               | [ [ health: [ [ type: 'a', state : "Up"] ] ] ]
    true         || 1               | ['Amazon']          | [ [ health: [ [ type: 'Amazon', healthClass: 'platform', state: "Unknown"] ] ] ]
    false        || 1               | ['Amazon']          | [ [ health: [ [ type: 'Amazon', state: "Down"] ] ] ]
    true         || 1               | ['GCE']             | [ [ health: [ [ type: 'GCE', healthClass: 'platform', state: "Unknown"] ] ] ]
    false        || 1               | ['GCE']             | [ [ health: [ [ type: 'GCE', state: "Down"] ] ] ]

    // multiple health providers
    true         || 1               | ['Amazon']          | [ [ health: [ [ type: 'Amazon', healthClass: 'platform', state: "Unknown"], [ type: 'b', state : "Down"] ] ] ]
    true         || 1               | ['GCE']             | [ [ health: [ [ type: 'GCE', healthClass: 'platform', state: "Unknown"], [ type: 'b', state : "Down"] ] ] ]
    true         || 1               | null                | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || 1               | null                | [ [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 1               | null                | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    true         || 1               | ['a']               | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || 1               | ['b']               | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || 1               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    true         || 1               | ['a']               | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 1               | ['b']               | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    true         || 1               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 1               | ['a']               | [ [ health: [ [ type: 'a', state : "Unknown"], [ type: 'b', state : "Down"] ] ] ]
    false        || 1               | ['b']               | [ [ health: [ [ type: 'a', state : "Unknown"], [ type: 'b', state : "Down"] ] ] ]
    false        || 1               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Unknown"], [ type: 'b', state : "Down"] ] ] ]

    // multiple instances
    true         || 2               | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"] ] ] ]
    false        || 2               | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || 2               | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    true         || 2               | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Up"] ] ] ]
    false        || 2               | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    false        || 2               | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    true         || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"] ] ] ]
    false        || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Up"] ] ] ]
    false        || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    false        || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    true         || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"] ] ] ]
    false        || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    true         || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Up"] ] ] ]
    false        || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    false        || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]

    // multiple instances with multiple health providers
    true         || 2               | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || 2               | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    true         || 2               | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2               | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2               | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2               | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || 2               | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2               | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2               | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2               | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    true         || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    true         || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    true         || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2               | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    true         || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    true         || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2               | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]

    // health providers ignored
    true         || 2               | []                  | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2               | []                  | [ [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
  }

}
