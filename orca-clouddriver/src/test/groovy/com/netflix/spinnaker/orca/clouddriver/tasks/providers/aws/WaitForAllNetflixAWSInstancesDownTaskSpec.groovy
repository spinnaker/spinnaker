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

package com.netflix.spinnaker.orca.clouddriver.tasks.providers.aws

import java.util.concurrent.TimeUnit
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForAllNetflixAWSInstancesDownTaskSpec extends Specification {
  @Subject task = new WaitForAllNetflixAWSInstancesDownTask() {
    @Override
    void verifyServerGroupsExist(Stage stage) {
      // do nothing
    }
  }

  @Shared
  def oneHourAgo = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(1)

  @Shared
  def oneMinuteAgo = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(1)

  def mapper = new OrcaObjectMapper()

  void "should check cluster to get server groups"() {
    given:
    def pipeline = new Pipeline()
    task.objectMapper = mapper
    def response = mapper.writeValueAsString([
      name        : "front50",
      serverGroups: [
        [
          region   : "us-west-1",
          name     : "front50-v000",
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
    ])


    task.oortService = Stub(OortService) {
      getCluster(*_) >> new Response('oort', 200, 'ok', [], new TypedString(response))
    }

    and:
    def stage = new PipelineStage(pipeline, "asgActionWaitForDownInstances", [
      "targetop.asg.enableAsg.name"   : "front50-v000",
      "targetop.asg.enableAsg.regions": ['us-west-1'],
      "account.name"                  : "test"
    ])

    expect:
    task.execute(stage).status == ExecutionStatus.SUCCEEDED

  }

  @Unroll
  void 'should succeed as #hasSucceeded based on instance providers #healthProviderNames for instances #instances'() {
    given:
    def stage = new PipelineStage(new Pipeline(), "")

    expect:
    hasSucceeded == task.hasSucceeded(stage, [minSize: 0], instances, healthProviderNames)

    where:
    hasSucceeded || healthProviderNames | instances
    true         || null                | []
    true         || null                | [ [ health: [ ] ] ]
    true         || ['a']               | []
    true         || null                | [ [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || null                | [ [ health: [ [ type: 'a', state : "Up"] ] ] ]
    true         || ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ] ]
    false        || ['b']               | [ [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || ['b']               | [ [ health: [ [ type: 'a', state : "Up"] ] ] ]
    true         || ['a']               | [ [ health: [ [ type: 'a', state: "OutOfService"] ] ] ]
    true         || null                | [ [ launchTime: oneHourAgo, health: [ [ type: 'Amazon', state: "Unknown"] ] ], [ health: [ [ type: 'Amazon', state: 'Unknown'], [type: 'Discovery', state: 'Down'] ] ] ]
    false        || null                | [ [ launchTime: oneMinuteAgo, health: [ [ type: 'Amazon', state: "Unknown"] ] ], [ health: [ [ type: 'Amazon', state: 'Unknown'], [type: 'Discovery', state: 'Down'] ] ] ]

    // multiple health providers
    false        || null                | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    true         || null                | [ [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || null                | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    true         || ['b']               | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || ['b']               | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Unknown"], [ type: 'b', state : "Down"] ] ] ]
    true         || ['b']               | [ [ health: [ [ type: 'a', state : "Unknown"], [ type: 'b', state : "Down"] ] ] ]
    true         || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Unknown"], [ type: 'b', state : "Down"] ] ] ]
    true         || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Unknown"], [ type: 'b', state : "OutOfService"] ] ] ]

    // multiple instances
    false        || null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"] ] ] ]
    true         || null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Up"] ] ] ]
    true         || null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    false        || null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"] ] ] ]
    true         || ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Up"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"] ] ] ]
    true         || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Up"] ] ] ]
    true         || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'b', state : "Down"] ] ] ]

    // multiple instances with multiple health providers
    false        || null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || null                | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    true         || null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    true         || null                | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    true         || ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    true         || ['a']               | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Up"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Down"] ] ] ]
    false        || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
    true         || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Down"] ] ] ]
    true         || ['a', 'b']          | [ [ health: [ [ type: 'a', state : "Down"] ] ], [ health: [ [ type: 'a', state : "Down"], [ type: 'b', state : "Unknown"] ] ] ]

    // ignoring health
    true         || []                  | [ [ health: [ [ type: 'a', state : "Up"] ] ], [ health: [ [ type: 'a', state : "Up"], [ type: 'b', state : "Unknown"] ] ] ]
  }

}
