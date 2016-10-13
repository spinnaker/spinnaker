/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForDestroyedServerGroupTask
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED

class WaitForDestroyedAsgTaskSpec extends Specification {
  @Subject
  def task = new WaitForDestroyedServerGroupTask()

  @Shared
  def objectMapper = new ObjectMapper()

  @Unroll
  void "should return RUNNING status if ASG does not exist, adding remaining instances to context"() {
    given:
    task.oortService = Mock(OortService) {
      1 * getCluster(*_) >> {
        if (status >= 400) {
          throw RetrofitError.httpError(
            null,
            new Response("http://...", status, "...", [], null),
            null,
            null
          )
        }
        new Response('..', status, 'ok', [], new TypedString(
          objectMapper.writeValueAsString(body)
        ))
      }
    }
    task.objectMapper = objectMapper

    and:
    def stage = new PipelineStage(new Pipeline(), "", [
      "regions": [region],
      "asgName": asgName,
    ])

    def result = task.execute(stage)

    expect:
    result.status == taskStatus

    where:
    status | body                                                                                        || taskStatus
    200    | [:]                                                                                         || SUCCEEDED
    200    | [serverGroups: []]                                                                          || SUCCEEDED
    200    | [serverGroups: [[region: "us-east-1", name: "app-test-v000"]]]                              || SUCCEEDED
    200    | [serverGroups: [[region: "us-west-1", name: "app-test-v001"]]]                              || SUCCEEDED
    404    | [:]                                                                                         || SUCCEEDED
    202    | [serverGroups: [[region: "us-west-1", name: "app-test-v001"]]]                              || RUNNING
    200    | [serverGroups: [[region: "us-west-1", name: "app-test-v000"]]]                              || RUNNING
    200    | [serverGroups: [[region: "us-west-1", name: "app-test-v000", instances: [[name:"i-123"]]]]] || RUNNING
    200    | [serverGroups: [[region: "us-west-1", name: "app-test-v000", instances: []]]]               || RUNNING
    500    | [:]                                                                                         || RUNNING

    region = "us-west-1"
    asgName = "app-test-v000"
  }

  void "should remove remainingInstances from stage outputs when task completes"() {
    given:
    task.oortService = Mock(OortService) {
      1 * getCluster(*_) >> {
        new Response('..', 200, 'ok', [], new TypedString(
            objectMapper.writeValueAsString(
                [:]
            )
        ))
      }
    }
    task.objectMapper = objectMapper

    and:
    def stage = new PipelineStage(new Pipeline(), "", [
        "regions": ["us-east-1"],
        "asgName": "app-test-v000",
        "remainingInstances": ["i-123"]
    ])

    def result = task.execute(stage)

    expect:
    result.stageOutputs.remainingInstances == []
  }

  @Unroll
  void "should include remainingInstances in stage outputs"() {
    given:
    task.oortService = Mock(OortService) {
      1 * getCluster(*_) >> {
        new Response('..', 200, 'ok', [], new TypedString(
            objectMapper.writeValueAsString(
                [serverGroups: [[region: "us-east-1", name: "app-test-v000", instances: instances]]]
            )
        ))
      }
    }
    task.objectMapper = objectMapper

    and:
    def stage = new PipelineStage(new Pipeline(), "", [
        "regions": ["us-east-1"],
        "asgName": "app-test-v000",
        "remainingInstances": ['i-123', 'i-234', 'i-345']
    ])

    def result = task.execute(stage)

    expect:
    result.stageOutputs.remainingInstances == remainingInstances

    where:
    instances                         || remainingInstances
    []                                || []
    [[name: 'i-123']]                 || ['i-123']
    [[name: 'i-123'],[name: 'i-234']] || ['i-123', 'i-234']
  }
}
