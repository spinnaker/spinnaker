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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.RetrofitError
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import static java.net.HttpURLConnection.HTTP_NOT_FOUND

class WaitForDestroyedAsgTaskSpec extends Specification {
  @Subject
  def task = new WaitForDestroyedAsgTask()

  @Shared
  def objectMapper = new ObjectMapper()

  @Unroll
  void "should return RUNNING status if ASG does not exist"() {
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
      "asgName": asgName
    ])

    expect:
    task.execute(stage.asImmutable()).status == taskStatus

    where:
    status | body                                                           || taskStatus
    200    | [:]                                                            || ExecutionStatus.SUCCEEDED
    200    | [serverGroups: []]                                             || ExecutionStatus.SUCCEEDED
    200    | [serverGroups: [[region: "us-east-1", name: "app-test-v000"]]] || ExecutionStatus.SUCCEEDED
    200    | [serverGroups: [[region: "us-west-1", name: "app-test-v001"]]] || ExecutionStatus.SUCCEEDED
    404    | [:]                                                            || ExecutionStatus.SUCCEEDED
    202    | [serverGroups: [[region: "us-west-1", name: "app-test-v001"]]] || ExecutionStatus.RUNNING
    200    | [serverGroups: [[region: "us-west-1", name: "app-test-v000"]]] || ExecutionStatus.RUNNING
    500    | [:]                                                            || ExecutionStatus.RUNNING

    region = "us-west-1"
    asgName = "app-test-v000"
  }
}
