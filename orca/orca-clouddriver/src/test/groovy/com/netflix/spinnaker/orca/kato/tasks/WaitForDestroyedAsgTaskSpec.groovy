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
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.tasks.servergroup.WaitForDestroyedServerGroupTask
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED

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
          throw makeSpinnakerHttpException(status)
        }
        Calls.response(Response.success(status, ResponseBody.create(MediaType.parse("application/json"),
            objectMapper.writeValueAsString(body))))
      }
    }
    task.objectMapper = objectMapper

    and:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
      "region": region,
      "asgName": asgName,
      "account": "test"
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
        Calls.response(ResponseBody.create(MediaType.parse("application/json"),
            objectMapper.writeValueAsString([:])))
      }
    }
    task.objectMapper = objectMapper

    and:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
        "region": ["us-east-1"],
        "asgName": "app-test-v000",
        "remainingInstances": ["i-123"],
        "account": "test"
    ])

    def result = task.execute(stage)

    expect:
    result.context.remainingInstances == []
  }

  @Unroll
  void "should include remainingInstances in stage outputs"() {
    given:
    task.oortService = Mock(OortService) {
      1 * getCluster(*_) >> {
        Calls.response(ResponseBody.create(MediaType.parse("application/json"),
            objectMapper.writeValueAsString(
                [serverGroups: [[region: "us-east-1", name: "app-test-v000", instances: instances]]]
            )
        ))
      }
    }
    task.objectMapper = objectMapper

    and:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "", [
        "region": ["us-east-1"],
        "asgName": "app-test-v000",
        "remainingInstances": ['i-123', 'i-234', 'i-345'],
        "account": "test"
    ])

    def result = task.execute(stage)

    expect:
    result.context.remainingInstances == remainingInstances

    where:
    instances                         || remainingInstances
    []                                || []
    [[name: 'i-123']]                 || ['i-123']
    [[name: 'i-123'],[name: 'i-234']] || ['i-123', 'i-234']
  }

  static SpinnakerHttpException makeSpinnakerHttpException(int status, String message = "{ \"message\": \"arbitrary message\" }") {
    String url = "https://mort";
    Response retrofit2Response =
        Response.error(
            status,
            ResponseBody.create(
                MediaType.parse("application/json"), message))

    Retrofit retrofit =
        new Retrofit.Builder()
            .baseUrl(url)
            .addConverterFactory(JacksonConverterFactory.create())
            .build()

    return new SpinnakerHttpException(retrofit2Response, retrofit)
  }
}
