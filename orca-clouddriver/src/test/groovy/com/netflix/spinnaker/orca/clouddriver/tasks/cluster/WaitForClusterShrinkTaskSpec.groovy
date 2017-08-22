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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.clouddriver.OortService
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.client.Response
import retrofit.mime.TypedByteArray
import spock.lang.Specification
import spock.lang.Subject
import static com.netflix.spinnaker.orca.ExecutionStatus.RUNNING
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED
import static retrofit.RetrofitError.httpError

class WaitForClusterShrinkTaskSpec extends Specification {

  def oortService = Mock(OortService)
  def objectMapper = new ObjectMapper()
  @Subject def task = new WaitForClusterShrinkTask(
    oortHelper: new OortHelper(oortService: oortService, objectMapper: objectMapper)
  )

  def "does not complete if previous ASG is still there"() {
    given:
    def stage = new Stage<>(new Pipeline(), "test", [
      cluster               : clusterName,
      credentials           : account,
      "deploy.server.groups": [
        (region): ["$clusterName-$oldServerGroup".toString()]
      ]
    ])

    and:
    oortService.getCluster(*_) >> clusterResponse(
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
    )

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
    def stage = new Stage<>(new Pipeline(), "test", [
      cluster               : clusterName,
      credentials           : account,
      "deploy.server.groups": [
        (region): ["$clusterName-$oldServerGroup".toString()]
      ]
    ])

    and:
    oortService.getCluster(*_) >> clusterResponse(
      name: clusterName,
      serverGroups: [
        [
          name  : "$clusterName-$newServerGroup".toString(),
          region: region,
          health: null
        ]
      ]
    )

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
    def stage = new Stage<>(context: [
      cluster               : clusterName,
      credentials           : account,
      "deploy.server.groups": [
        (region): ["$clusterName-$oldServerGroup".toString()]
      ]
    ])

    and:
    oortService.getCluster(*_) >> { throw httpError("http://cloudriver", emptyClusterResponse(), null, null) }

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

  Response clusterResponse(body) {
    new Response("http://cloudriver", 200, "OK", [], new TypedByteArray("application/json", objectMapper.writeValueAsString(body).bytes))
  }

  Response emptyClusterResponse() {
    new Response("http://cloudriver", 404, "Not Found", [], null)
  }

}
