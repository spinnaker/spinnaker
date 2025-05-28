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
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.TaskResult
import com.netflix.spinnaker.orca.clouddriver.InstanceService
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.mock.Calls
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

class VerifyQuipTaskSpec extends Specification {

  @Subject task = Spy(VerifyQuipTask)
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
    task.objectMapper = new ObjectMapper()
  }

  @Unroll
  def "missing configuration #app, #cluster, #account, #region,#healthProviders"() {
    def pipe = pipeline {
      application = app
    }
    def stage = new StageExecutionImpl(pipe, 'verifyQuip', [
      "clusterName"    : cluster,
      "account"        : account,
      "region"         : region,
      "application"    : app,
      "healthProviders": healthProviders
    ])

    when:
    task.execute(stage)

    then:
    thrown(RuntimeException)

    where:
    app   | cluster    | account | region      | healthProviders
    ""    | "foo-test" | "test"  | "us-west-2" | ['Discovery']
    "foo" | null       | "test"  | "us-west-2" | ['Discovery']
    "foo" | "foo-test" | null    | "us-west-2" | ['Discovery']
    "foo" | "foo-test" | "test"  | null        | ['Discovery']
    "foo" | "foo-test" | "test"  | "us-west-2" | null
  }

  def "more than one asg"() {
    def pipe = pipeline {
      application = app
    }
    def stage = new StageExecutionImpl(pipe, 'verifyQuip', [
      "clusterName"    : cluster,
      "account"        : account,
      "region"         : region,
      "application"    : app,
      "healthProviders": ['Discovery']
    ])

    when:
    def result = task.execute(stage)

    then:
    0 * task.createInstanceService(_) >> instanceService
    thrown(RuntimeException)

    where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "us-west-2"
  }

  def "bad oort response"() {
    def pipe = pipeline {
      application = app
    }
    def stage = new StageExecutionImpl(pipe, 'verifyQuip', [
      "clusterName"    : cluster,
      "account"        : account,
      "region"         : region,
      "application"    : app,
      "healthProviders": ['Discovery']
    ])

    when:
    def result = task.execute(stage)

    then:
    0 * task.createInstanceService(_) >> instanceService
    thrown(RuntimeException)

    where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "eu-west-1"
  }

  def "no server groups in cluster"() {
    given:
    def pipe = pipeline {
      application = app
    }
    def stage = new StageExecutionImpl(pipe, 'verifyQuip', [
      "clusterName"    : cluster,
      "account"        : account,
      "region"         : region,
      "application"    : app,
      "healthProviders": ['Discovery']
    ])

    when:
    task.execute(stage)

    then:
    0 * task.createInstanceService(_) >> instanceService
    !stage.context?.instances
    thrown(RuntimeException)

    where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "eu-west-1"
  }

  def "no instances in cluster"() {
    given:
    def pipe = pipeline {
      application = app
    }
    def stage = new StageExecutionImpl(pipe, 'verifyQuip', [
      "clusterName"    : cluster,
      "account"        : account,
      "region"         : region,
      "application"    : app,
      "healthProviders": ['Discovery']
    ])

    when:
    task.execute(stage)

    then:
    0 * task.createInstanceService(_) >> instanceService
    !stage.context?.instances
    thrown(RuntimeException)

    where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "eu-west-1"
  }

  def "verifies at least one instance without quip"() {
    given:
    def pipe = pipeline {
      application = app
    }
    def stage = new StageExecutionImpl(pipe, 'verifyQuip', [
      "clusterName"    : cluster,
      "account"        : account,
      "region"         : region,
      "application"    : app,
      "healthProviders": ['Discovery'],
      "instances"      : ["i-123": ["hostName": "http://foo.com"], "i-234": ["hostName": "http://foo2.com"]]
    ])

    ResponseBody instanceResponse = ResponseBody.create(MediaType.parse("application/json"), instance)

    when:
    TaskResult result = task.execute(stage)

    then:
    2 * task.createInstanceService(_) >> instanceService
    1 * instanceService.listTasks() >> Calls.response(instanceResponse)
    1 * instanceService.listTasks() >> {
      throw new SpinnakerServerException(new RuntimeException(null), new Request.Builder().url("http://some-url").build())
    }
    !result?.context
    thrown(RuntimeException)

    where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"

    instance = '[]'
  }

  def "verifies quip is running"() {
    given:
    def pipe = pipeline {
      application = app
    }
    def stage = new StageExecutionImpl(pipe, 'verifyQuip', [
      "clusterName"    : cluster,
      "account"        : account,
      "region"         : region,
      "application"    : app,
      "healthProviders": ['Discovery'],
      "instances"      : ["i-123": ["hostName": "http://foo.com"], "i-234": ["hostName": "http://foo2.com"]]
    ])

    ResponseBody instanceResponse = ResponseBody.create(MediaType.parse("application/json"), instance)

    when:
    TaskResult result = task.execute(stage)

    then:
    2 * task.createInstanceService(_) >> instanceService
    2 * instanceService.listTasks() >> { return Calls.response(instanceResponse) }
    //result.context?.instances?.size() == 2
    result.status == ExecutionStatus.SUCCEEDED

    where:
    app = 'foo'
    cluster = 'foo-test'
    account = 'test'
    region = "us-east-1"

    instance = '[]'
  }
}
