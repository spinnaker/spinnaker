/*
 * Copyright 2015 Netflix, Inc.
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

package com.netflix.spinnaker.orca.oort.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class FindAmiFromClusterTaskSpec extends Specification {

  @Subject task = new FindAmiFromClusterTask()
  OortService oortService = Mock(OortService)

  def setup() {
    task.oortService = oortService
    task.objectMapper = new ObjectMapper()
  }

  @Unroll
  def "selects correct SG by strategy #strategy"() {
    given:
    def pipe = new Pipeline.Builder()
      .withApplication(app)
      .build()
    def stage = new PipelineStage(pipe, 'findAmi', [
      cluster: cluster,
      account: account,
      selectionStrategy: strategy
    ])

    Response response = new Response('http://oort', 200, 'OK', [], new TypedString(oortResponse))

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getCluster(app, account, cluster, 'aws') >> response
    result.globalOutputs?.deploymentDetails?.find { it.region == 'us-east-1' }?.ami == expectedAmi



    where:
    strategy  | expectedAmi
    'NEWEST'  | 'ami-234'
    'LARGEST' | 'ami-123'

    app = 'foo'
    cluster = 'foo-test'
    account = 'test'

    oortResponse = '''\
    {
      "serverGroups":[{
        "name": "foo-test-v000",
        "region":"us-east-1",
        "asg": { "createdTime": 12344, "suspendedProcesses": [] },
        "image": { "imageId": "ami-012", "name": "ami-012" },
        "buildInfo": { "job": "foo-build", "buildNumber": 1 },
        "instances": [ { "id": 1 }, { "id": 2 } ]
      },{
        "name": "foo-test-v001",
        "region":"us-east-1",
        "asg": { "createdTime": 12345, "suspendedProcesses": [] },
        "image": { "imageId": "ami-123", "name": "ami-123" },
        "buildInfo": { "job": "foo-build", "buildNumber": 1 },
        "instances": [ { "id": 1 }, { "id": 2 } ]
      },{
        "name": "foo-test-v002",
        "region":"us-east-1",
        "asg": { "createdTime": 23456,  "suspendedProcesses": [] },
        "image": { "imageId": "ami-234", "name": "ami-234" },
        "buildInfo": { "job": "foo-build", "buildNumber": 1 },
        "instances": [ { "id": 1 } ]
      }]
    }
    '''.stripIndent()
  }
}
