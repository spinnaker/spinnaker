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

package com.netflix.spinnaker.orca.kato.tasks.quip

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.InstanceService
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.RestAdapter
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class VerifyQuipTaskSpec extends Specification {

  @Subject task = new VerifyQuipTask()
  OortService oortService = Mock(OortService)
  InstanceService instanceService = Mock(InstanceService)

  String oort = '''\
    {
      "serverGroups":[{
        "name": "foo-test-v000",
        "region":"us-east-1",
        "asg": { "createdTime": 12344, "suspendedProcesses": [] },
        "image": { "imageId": "ami-012", "name": "ami-012" },
        "buildInfo": { "job": "foo-build", "buildNumber": 1 },
        "instances": [ { "id": 1, "publicDnsName": "ec2.1.com" }, { "id": 2, "publicDnsName": "ec2.2.com" } ]
      },{
        "name": "foo-test-v001",
        "region":"us-west-2",
        "asg": { "createdTime": 12345, "suspendedProcesses": [] },
        "image": { "imageId": "ami-123", "name": "ami-123" },
        "buildInfo": { "job": "foo-build", "buildNumber": 1 },
        "instances": [ { "id": 3 }, { "id": 4 } ]
      },{
        "name": "foo-test-v002",
        "region":"us-west-2",
        "asg": { "createdTime": 12345, "suspendedProcesses": [] },
        "image": { "imageId": "ami-123", "name": "ami-123" },
        "buildInfo": { "job": "foo-build", "buildNumber": 1 },
        "instances": [ { "id": 5 }, { "id": 6 } ]
      },{
        "name": "foo-test-v002",
        "region":"eu-west-1",
        "asg": { "createdTime": 23456,  "suspendedProcesses": [] },
        "image": { "imageId": "ami-234", "name": "ami-234" },
        "buildInfo": { "job": "foo-build", "buildNumber": 1 },
        "instances": []
      }]
    }
    '''.stripIndent()

  def setup() {
    task.oortService = oortService
    task.instanceService = instanceService
    task.testing = true
    task.objectMapper = new ObjectMapper()
  }

  def "missing configuration"() {
    def pipe = new Pipeline.Builder()
      .withApplication(app)
      .build()
    def stage = new PipelineStage(pipe, 'verifyQuip', [
      "clusterName" : null,
      "account" : account,
      "region" : region,
      "application" : app
    ])

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.FAILED

    where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "us-west-2"

  }

  def "more than one asg"() {
    def pipe = new Pipeline.Builder()
      .withApplication(app)
      .build()
    def stage = new PipelineStage(pipe, 'verifyQuip', [
      "clusterName" : cluster,
      "account" : account,
      "region" : region,
      "application" : app
    ])
    Response oortResponse = new Response('http://oort', 500, 'WTF', [], new TypedString(oort))

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getCluster(app, account, cluster, 'aws') >> oortResponse
    result.status == ExecutionStatus.FAILED

    where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "us-west-2"
  }

  def "bad oort response"() {
    def pipe = new Pipeline.Builder()
      .withApplication(app)
      .build()
    def stage = new PipelineStage(pipe, 'verifyQuip', [
      "clusterName" : cluster,
      "account" : account,
      "region" : region,
      "application" : app
    ])
    Response oortResponse = new Response('http://oort', 500, 'WTF', [], null)

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getCluster(app, account, cluster, 'aws') >> oortResponse
    result.status == ExecutionStatus.FAILED

    where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "eu-west-1"

  }

    def "no instances in cluster"() {
    given:
    def pipe = new Pipeline.Builder()
      .withApplication(app)
      .build()
    def stage = new PipelineStage(pipe, 'verifyQuip', [
      "clusterName" : cluster,
      "account" : account,
      "region" : region,
      "application" : app
    ])

    Response oortResponse = new Response('http://oort', 200, 'OK', [], new TypedString(oort))

    when:
    def result = task.execute(stage)

    then:
    1 * oortService.getCluster(app, account, cluster, 'aws') >> oortResponse
    !stage.context?.instances
    result.status == ExecutionStatus.FAILED

     where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "eu-west-1"
  }

  def "verifies at least one instance without quip"() {
    given:
    def pipe = new Pipeline.Builder()
      .withApplication(app)
      .build()
    def stage = new PipelineStage(pipe, 'verifyQuip', [
      "clusterName" : cluster,
      "account" : account,
      "region" : region,
      "application" : app
    ])

    Response oortResponse = new Response('http://oort', 200, 'OK', [], new TypedString(oort))
    Response instanceResponse = new Response('http://oort', 200, 'OK', [], new TypedString(instance))
    Response badInstanceResponse = new Response('http://oort', 500, 'WTF', [], null)

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * oortService.getCluster(app, account, cluster, 'aws') >> oortResponse
    2 * instanceService.listTasks() >>> [ instanceResponse, badInstanceResponse ]
    //stage.context?.instances?.size() == 2
    result.stageOutputs.instances.size() == 2
    result.status == ExecutionStatus.FAILED

    where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"

    instance = '[]'
  }

  def "verifies quip is running"() {
    given:
    def pipe = new Pipeline.Builder()
      .withApplication(app)
      .build()
    def stage = new PipelineStage(pipe, 'verifyQuip', [
      "clusterName" : cluster,
      "account" : account,
      "region" : region,
      "application" : app
    ])

    Response oortResponse = new Response('http://oort', 200, 'OK', [], new TypedString(oort))
    Response instanceResponse = new Response('http://instance.com', 200, 'OK', [], new TypedString(instance))

    when:
    TaskResult result = task.execute(stage)

    then:
    1 * oortService.getCluster(app, account, cluster, 'aws') >> oortResponse
    2 * instanceService.listTasks() >> instanceResponse
    result.stageOutputs?.instances?.size() == 2
    result.status == ExecutionStatus.SUCCEEDED

    where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"

    instance = '[]'
  }
}
