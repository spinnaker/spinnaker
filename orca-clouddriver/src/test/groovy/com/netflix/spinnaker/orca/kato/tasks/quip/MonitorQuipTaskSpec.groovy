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
import com.netflix.spinnaker.orca.clouddriver.InstanceService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.Stage
import retrofit.RetrofitError
import retrofit.client.Client
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class MonitorQuipTaskSpec extends Specification {

  @Subject task = Spy(MonitorQuipTask)
  InstanceService instanceService = Mock(InstanceService)

  def setup() {
    task.objectMapper = new ObjectMapper()
    task.retrofitClient = Stub(Client)
  }

  @Unroll
  def "check different success statuses, servers return #status expect #executionStatus"() {
    given:
    def pipe = Pipeline.builder()
      .withApplication("foo")
      .build()
    def stage = new Stage<>(pipe, 'monitorQuip', [:])
    stage.context.instances = instances
    stage.context.taskIds = taskIds

    def responses = []
    status?.each {
      responses << new Response('http://foo.com', 200, 'OK', [], new TypedString("{\"status\" : \"${it}\"}"))
    }

    instances.size() * task.createInstanceService(_) >> instanceService

    when:
    def result = task.execute(stage)

    then:
    taskIds.eachWithIndex { def entry, int i ->
      1 * instanceService.listTask(entry.value) >> responses.get(i)
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
    def pipe = Pipeline.builder()
      .withApplication("foo")
      .build()
    def stage = new Stage<>(pipe, 'monitorQuip', [:])
    stage.context.instances = instances
    stage.context.taskIds = taskIds

    def responses = []
    status?.each {
      responses << new Response('http://foo.com', 200, 'OK', [], new TypedString("{\"status\" : \"${it}\"}"))
    }
    task.createInstanceService(_) >> instanceService

    when:
    task.execute(stage)

    then:
    taskIds.eachWithIndex { def entry, int i ->
      instanceService.listTask(entry.value) >> responses.get(i)
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
    def pipe = Pipeline.builder()
      .withApplication("foo")
      .build()
    def stage = new Stage<>(pipe, 'monitorQuip', [:])
    stage.context.instances = instances
    stage.context.taskIds = taskIds

    task.createInstanceService(_) >> instanceService

    when:
    TaskResult result = task.execute(stage)

    then:
    taskIds.eachWithIndex { def entry, int i ->
      instanceService.listTask(entry.value) >> { throw new RetrofitError(null, null, null, null, null, null, null)}
    }
    result.status == ExecutionStatus.RUNNING

    where:
    instances | taskIds
    ["i-123" : ["hostName" : "foo.com"] ]| ["foo.com" : "abcd"]
    ["i-234" : ["hostName" : "foo.com"], "i-345" : ["hostName" : "foo2.com"] ] | ["foo.com" : "abcd", "foo2.com" : "efghij"]
  }

  def "servers return bad data"() {
    given:
    def pipe = Pipeline.builder()
      .withApplication("foo")
      .build()
    def stage = new Stage<>(pipe, 'monitorQuip', [:])
    stage.context.instances = instances
    stage.context.taskIds = taskIds

    task.createInstanceService(_) >> instanceService

    when:
    task.execute(stage)

    then:
    taskIds.eachWithIndex { def entry, int i ->
      instanceService.listTask(entry.value) >> new Response('http://foo.com', 200, 'OK', [], new TypedString("{\"noStatus\" : \"foo\"}"))
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
    def pipe = Pipeline.builder()
      .withApplication("foo")
      .build()
    def stage = new Stage<>(pipe, 'monitorQuip', [:])
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
