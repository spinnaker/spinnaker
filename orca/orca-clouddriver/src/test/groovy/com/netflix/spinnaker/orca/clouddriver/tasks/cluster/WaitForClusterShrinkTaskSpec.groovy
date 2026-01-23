/*
 * Copyright 2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.clouddriver.tasks.cluster

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerHttpException
import com.netflix.spinnaker.orca.clouddriver.CloudDriverService
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import okhttp3.MediaType
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus.SUCCEEDED

class WaitForClusterShrinkTaskSpec extends Specification {

  def oortService = Mock(OortService)
  def objectMapper = OrcaObjectMapper.getInstance()
  @Subject def task = new WaitForClusterShrinkTask(
    cloudDriverService: new CloudDriverService(oortService, objectMapper)
  )

  def "does not complete if previous ASG is still there"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "test", [
      cluster               : clusterName,
      credentials           : account,
      "deploy.server.groups": [
        (region): ["$clusterName-$oldServerGroup".toString()]
      ]
    ])

    and:
    oortService.getCluster(*_) >> Calls.response(clusterResponse(
      name: clusterName,
      serverGroups: [
        [
          name  : "$clusterName-v050".toString(),
          region: "us-west-1",
          health: null
        ],
        [
          name  : "$clusterName-v051".toString(),
          region: "us-west-1",
          health: null
        ],
        [
          name  : "$clusterName-$newServerGroup".toString(),
          region: region,
          health: null
        ],
        [
          name  : "$clusterName-$oldServerGroup".toString(),
          region: region,
          health: null
        ]
      ]
    ))

    when:
    def result = task.execute(stage)

    then:
    result.status == RUNNING

    where:
    clusterName = "spindemo-test-prestaging"
    account = "test"
    region = "us-east-1"
    oldServerGroup = "v167"
    newServerGroup = "v168"
  }

  def "completes if previous ASG is gone"() {
    given:
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "test", [
      cluster               : clusterName,
      credentials           : account,
      "deploy.server.groups": [
        (region): ["$clusterName-$oldServerGroup".toString()]
      ]
    ])

    and:
    oortService.getCluster(*_) >> Calls.response(clusterResponse(
      name: clusterName,
      serverGroups: [
        [
          name  : "$clusterName-$newServerGroup".toString(),
          region: region,
          health: null
        ]
      ]
    ))

    when:
    def result = task.execute(stage)

    then:
    result.status == SUCCEEDED

    where:
    clusterName = "spindemo-test"
    account = "test"
    region = "us-east-1"
    oldServerGroup = "v391"
    newServerGroup = "v392"
  }

  def "completes if the cluster is now totally gone"() {
    given:
    def stage = new StageExecutionImpl(context: [
      cluster               : clusterName,
      credentials           : account,
      "deploy.server.groups": [
        (region): ["$clusterName-$oldServerGroup".toString()]
      ]
    ])

    and:
    oortService.getCluster(*_) >> { throw makeSpinnakerHttpException(404) }

    when:
    def result = task.execute(stage)

    then:
    result.status == SUCCEEDED

    where:
    clusterName = "spindemo-test"
    account = "test"
    region = "us-east-1"
    oldServerGroup = "v391"
  }

  ResponseBody clusterResponse(body) {
    ResponseBody.create(MediaType.parse("application/json"), objectMapper.writeValueAsString(body).bytes)
  }

  static SpinnakerHttpException makeSpinnakerHttpException(int status, String message = "{ \"message\": \"arbitrary message\" }") {
    String url = "https://oort";
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
