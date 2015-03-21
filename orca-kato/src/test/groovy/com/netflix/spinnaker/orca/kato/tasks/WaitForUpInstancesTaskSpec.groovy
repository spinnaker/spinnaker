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

package com.netflix.spinnaker.orca.kato.tasks
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForUpInstancesTaskSpec extends Specification {

  @Subject task = new WaitForUpInstancesTask()

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
                minSize: 1
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
                minSize: 1
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
                  minSize: 1
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
    task.execute(stage.asImmutable()).status == ExecutionStatus.RUNNING

    when:
    stage.context."targetop.asg.enableAsg.name" = "front50-v001"

    then:
    task.execute(stage.asImmutable()).status == ExecutionStatus.SUCCEEDED

  }

  @Unroll
  void 'should succeed as #hasSucceeded based on instance providers #healthProviderNames for instances #instances'() {
    expect:
    hasSucceeded == task.hasSucceeded(
      new PipelineStage(new Pipeline(), "", "", [capacity: [min: minSize]]), null, instances, healthProviderNames
    )
    hasSucceeded == task.hasSucceeded(
      new PipelineStage(new Pipeline(), "", "", [:]), [minSize: minSize], instances, healthProviderNames
    )

    where:
    hasSucceeded || minSize | healthProviderNames | instances
    true         || 0       | null                | []
    true         || 0       | ['a']               | []
    false        || 1       | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ] ]
    true         || 1       | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ] ]
    false        || 1       | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ] ]
    true         || 1       | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ] ]
    false        || 1       | ['b']               | [ [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || 1       | ['b']               | [ [ health: [ [ type: 'a', state : "Up"] ] ] ]

    // multiple health providers
    true         || 1       | null                | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || 1       | null                | [ [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 1       | null                | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    true         || 1       | ['a']               | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || 1       | ['b']               | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || 1       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    true         || 1       | ['a']               | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 1       | ['b']               | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    true         || 1       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 1       | ['a']               | [ [ health: [ [ type: 'a', state : "Unknown"], [ type: 'b', state : "Down"] ] ] ]
    false        || 1       | ['b']               | [ [ health: [ [ type: 'a', state : "Unknown"], [ type: 'b', state : "Down"] ] ] ]
    false        || 1       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Unknown"], [ type: 'b', state : "Down"] ] ] ]

    // multiple instances
    true         || 2       | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"] ] ] ]
    false        || 2       | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || 2       | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    true         || 2       | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Up"] ] ] ]
    false        || 2       | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    false        || 2       | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    true         || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"] ] ] ]
    false        || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Up"] ] ] ]
    false        || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    false        || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    true         || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"] ] ] ]
    false        || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    true         || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Up"] ] ] ]
    false        || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    false        || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]

    // multiple instances with multiple health providers
    true         || 2       | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || 2       | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    true         || 2       | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2       | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2       | null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2       | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || 2       | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2       | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2       | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2       | null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    true         || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    true         || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    true         || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2       | ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    true         || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    true         || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || 2       | ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]

    // health providers ignored
    true         || 2       | []                  | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || 2       | []                  | [ [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
  }

}
