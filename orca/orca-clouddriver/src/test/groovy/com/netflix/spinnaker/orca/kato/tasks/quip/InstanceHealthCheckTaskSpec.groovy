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

import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.clouddriver.InstanceService
import com.netflix.spinnaker.orca.clouddriver.ModelUtils
import com.netflix.spinnaker.orca.clouddriver.utils.OortHelper
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import okhttp3.MediaType
import okhttp3.Request;
import okhttp3.ResponseBody
import retrofit2.mock.Calls;
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll
import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.pipeline

class InstanceHealthCheckTaskSpec extends Specification {

  @Subject task = Spy(InstanceHealthCheckTask)
  InstanceService instanceService = Mock(InstanceService)
  def oortHelper = Mock(OortHelper)


  @Unroll
  def "check different tasks statuses, servers with responseCode #responseCode expect #executionStatus"() {
    given:
    def pipe = pipeline {
      application = "foo"
    }
    def stage = new StageExecutionImpl(pipe, 'instanceHealthCheck', [:])
    stage.context.instances = instances

    def responses = []
    responseCode?.each {
      responses << ResponseBody.create(MediaType.parse("application/json"),"[]")
    }

    instances.size() * task.createInstanceService(_) >> instanceService

    when:
    def result = task.execute(stage)

    then:
    instances.eachWithIndex { def entry, int i ->
      if (responseCode.get(i) == 200) {
        1 * instanceService.healthCheck("healthCheck") >> Calls.response(responses.get(i))
      } else {
        1 * instanceService.healthCheck("healthCheck") >> { throw new SpinnakerServerException(new RuntimeException(""), new Request.Builder().url("http://some-url").build()) }
      }
    }

    result.status == executionStatus

    where:
    instances                                                                                                                                                                          | responseCode | executionStatus
    ["i-1234": ["hostName": "foo.com", "healthCheckUrl": "http://foo.com:7001/healthCheck"]]                                                                                           | [404]        | ExecutionStatus.RUNNING
    ["i-1234": ["hostName": "foo.com", "healthCheckUrl": "http://foo.com:7001/healthCheck"]]                                                                                           | [200]        | ExecutionStatus.SUCCEEDED
    ["i-1234": ["hostName": "foo.com", "healthCheckUrl": "http://foo.com:7001/healthCheck"], "i-2345": ["hostName": "foo2.com", "healthCheckUrl": "http://foo2.com:7001/healthCheck"]] | [404, 200]   | ExecutionStatus.RUNNING
    ["i-1234": ["hostName": "foo.com", "healthCheckUrl": "http://foo.com:7001/healthCheck"], "i-2345": ["hostName": "foo2.com", "healthCheckUrl": "http://foo2.com:7001/healthCheck"]] | [200, 404]   | ExecutionStatus.RUNNING
    ["i-1234": ["hostName": "foo.com", "healthCheckUrl": "http://foo.com:7001/healthCheck"], "i-2345": ["hostName": "foo2.com", "healthCheckUrl": "http://foo2.com:7001/healthCheck"]] | [404, 404]   | ExecutionStatus.RUNNING
    ["i-1234": ["hostName": "foo.com", "healthCheckUrl": "http://foo.com:7001/healthCheck"], "i-2345": ["hostName": "foo2.com", "healthCheckUrl": "http://foo2.com:7001/healthCheck"]] | [200, 200]   | ExecutionStatus.SUCCEEDED
  }

  @Unroll
  def "missing instance healthCheckUrl returns running"() {
    given:
    def pipe = pipeline {
      application = "foo"
    }
    def stage = new StageExecutionImpl(pipe, 'instanceHealthCheck', [:])
    stage.context.instances = instances

    and:
    task.createInstanceService(_) >> instanceService
    instanceService.healthCheck("healthCheck") >> Calls.response(ResponseBody.create(MediaType.parse("application/json"),"[]"))


    when:
    task.oortHelper = oortHelper
    1 * oortHelper.getInstancesForCluster(_, _, _) >> ["i-1234" : ModelUtils.instanceInfo(["hostName" : "foo.com", "healthCheckUrl" : "http://foo.com:7001/healthCheck"])]

    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING

    where:
    instances                                                                                                                     | _
    ["i-1234": ["hostName": "foo.com"]]                                                                                           | _
    ["i-1234": ["hostName": "foo.com", "healthCheckUrl": "http://foo.com:7001/healthCheck"], "i-2345": ["hostName": "foo2.com"]]  | _
    ["i-1234": ["hostName": "foo.com"], "i-2345": ["hostName": "foo2.com", "healthCheckUrl": "http://foo2.com:7001/healthCheck"]] | _
  }

  def "retry on missing instance healthCheckUrl"() {
    given:
    def pipe = pipeline {
      application = "foo"
    }
    def stage = new StageExecutionImpl(pipe, 'instanceHealthCheck', [:])
    stage.context.instances = instances
    task.oortHelper = oortHelper

    when:
    1 * oortHelper.getInstancesForCluster(_, _, _) >> ["i-1234" : ModelUtils.instanceInfo(["hostName" : "foo.com", "healthCheckUrl" : "http://foo.com:7001/healthCheck"])]
    1 * instanceService.healthCheck("healthCheck") >> Calls.response(ResponseBody.create(MediaType.parse("application/json"),"[]"))
    1 * task.createInstanceService(_) >> instanceService

    def result = task.execute(stage)
    stage.context << result.context
    result = task.execute(stage)

    then:
    result.status == ExecutionStatus.SUCCEEDED

    where:
    instances                           | _
    ["i-1234": ["hostName": "foo.com"]] | _
  }
}
