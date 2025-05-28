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

class MonitorQuipTaskSpec extends Specification {

  @Subject task = Spy(MonitorQuipTask)
  InstanceService instanceService = Mock(InstanceService)

  def setup() {
    task.objectMapper = new ObjectMapper()
  }

  @Unroll
  def "check different success statuses, servers return #status expect #executionStatus"() {
    given:
    def pipe = pipeline {
      application = "foo"
    }
    def stage = new StageExecutionImpl(pipe, 'monitorQuip', [:])
    stage.context.instances = instances
    stage.context.taskIds = taskIds

    def responses = []
    status?.each {
      responses << ResponseBody.create(MediaType.parse("application/json"),"{\"status\" : \"${it}\"}")
    }

    instances.size() * task.createInstanceService(_) >> instanceService

    when:
    def result = task.execute(stage)

    then:
    taskIds.eachWithIndex { def entry, int i ->
      1 * instanceService.listTask(entry.value) >> { return Calls.response(responses.get(i)) }
    }

    result.status == executionStatus

    where:
    instances | taskIds | status | executionStatus
    ["i-1234" : ["hostName" : "foo.com"] ] | ["foo.com" : "abcd"] | ["Running"] | ExecutionStatus.RUNNING
    ["i-1234" : ["hostName" : "foo.com"] ] | ["foo.com" : "abcd"] | ["Successful"] | ExecutionStatus.SUCCEEDED
    ["i-1234" : ["hostName" : "foo.com"], "i-2345" : ["hostName" : "foo2.com"] ]  | ["foo.com" : "abcd", "foo2.com" : "efghij"] | ["Running", "Successful"] | ExecutionStatus.RUNNING
    ["i-1234" : ["hostName" : "foo.com"], "i-2345" : ["hostName" : "foo2.com"] ] | ["foo.com" : "abcd", "foo2.com" : "efghij"] | ["Successful", "Running"] | ExecutionStatus.RUNNING
    ["i-1234" : ["hostName" : "foo.com"], "i-2345" : ["hostName" : "foo2.com"] ] | ["foo.com" : "abcd", "foo2.com" : "efghij"] | ["Running", "Running"] | ExecutionStatus.RUNNING
    ["i-1234" : ["hostName" : "foo.com"], "i-2345" : ["hostName" : "foo2.com"] ] | ["foo.com" : "abcd", "foo2.com" : "efghij"] | ["Successful", "Successful"] | ExecutionStatus.SUCCEEDED
  }

  @Unroll
  def "check different failure statuses, servers return #status, expect exception"() {
    given:
    def pipe = pipeline {
      application = "foo"
    }
    def stage = new StageExecutionImpl(pipe, 'monitorQuip', [:])
    stage.context.instances = instances
    stage.context.taskIds = taskIds

    def responses = []
    status?.each {
      responses << ResponseBody.create(MediaType.parse("application/json"),"{\"status\" : \"${it}\"}")
    }
    task.createInstanceService(_) >> instanceService

    when:
    task.execute(stage)

    then:
    taskIds.eachWithIndex { def entry, int i ->
      instanceService.listTask(entry.value) >> { return Calls.response(responses.get(i)) }
    }
    thrown(RuntimeException)

    where:
    instances | taskIds | status
    ["i-1234" : ["hostName" : "foo.com"] ] | ["foo.com" : "abcd"] | ["Failed"]
    ["i-1234" : ["hostName" : "foo.com"], "i-2345" : ["hostName" : "foo2.com"] ]| ["foo.com" : "abcd", "foo2.com" : "efghij"] | ["Successful", "Failed"]
    ["i-1234" : ["hostName" : "foo.com"], "i-2345" : ["hostName" : "foo2.com"] ]| ["foo.com" : "abcd", "foo2.com" : "efghij"] | ["Failed", "Successful"]
  }

  @Unroll
  def "servers return non-200 responses"() {
    given:
    def pipe = pipeline {
      application = "foo"
    }
    def stage = new StageExecutionImpl(pipe, 'monitorQuip', [:])
    stage.context.instances = instances
    stage.context.taskIds = taskIds
    task.createInstanceService(_) >> instanceService

    when:
    TaskResult result = task.execute(stage)

    then:
    taskIds.eachWithIndex { def entry, int i ->
      instanceService.listTask(entry.value) >> { throw new SpinnakerServerException(new RuntimeException(""), new Request.Builder().url("http://some-url").build())}
    }
    result.status == ExecutionStatus.RUNNING

    where:
    instances | taskIds
    ["i-123" : ["hostName" : "foo.com"] ]| ["foo.com" : "abcd"]
    ["i-234" : ["hostName" : "foo.com"], "i-345" : ["hostName" : "foo2.com"] ] | ["foo.com" : "abcd", "foo2.com" : "efghij"]
  }

  def "servers return bad data"() {
    given:
    def pipe = pipeline {
      application = "foo"
    }
    def stage = new StageExecutionImpl(pipe, 'monitorQuip', [:])
    stage.context.instances = instances
    stage.context.taskIds = taskIds

    task.createInstanceService(_) >> instanceService

    when:
    task.execute(stage)

    then:
    taskIds.eachWithIndex { def entry, int i ->
      instanceService.listTask(entry.value) >> { return Calls.response(ResponseBody.create(MediaType.parse("application/json"),"{\"noStatus\" : \"foo\"}")) }
    }
    thrown(RuntimeException)

    where:
    instances | taskIds
    ["i-1234" : ["hostName" : "foo.com"] ] | ["foo.com" : "abcd"]
    ["i-1234" : ["hostName" : "foo.com"], "i-2345" : ["hostName" : "foo2.com"] ] | ["foo.com" : "abcd", "foo2.com" : "efghij"]
  }

  @Unroll
  def "missing configuration"() {
    given:
    def pipe = pipeline {
      application = "foo"
    }
    def stage = new StageExecutionImpl(pipe, 'monitorQuip', [:])
    stage.context.instances = instances
    stage.context.taskIds = taskIds

    when:
    task.execute(stage)

    then:
    thrown(RuntimeException)

    where:
    instances                           | taskIds
    ["i-1234": ["hostName": "foo.com"]] | null
  }
}
