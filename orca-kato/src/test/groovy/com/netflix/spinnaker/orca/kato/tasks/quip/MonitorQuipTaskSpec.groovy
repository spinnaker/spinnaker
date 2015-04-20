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
import com.netflix.spinnaker.orca.oort.InstanceService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.client.Response
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class MonitorQuipTaskSpec extends Specification {

  @Subject task = new MonitorQuipTask()
  InstanceService instanceService = Mock(InstanceService)

  def setup() {
    task.instanceService = instanceService
    task.testing = true
    task.objectMapper = new ObjectMapper()
  }

  @Unroll
  def "check different statuses, servers return #status expect #executionStatus"() {
    given:
    def pipe = new Pipeline.Builder()
      .withApplication("foo")
      .build()
    def stage = new PipelineStage(pipe, 'monitorQuip', [:])
    stage.context.instances = instances
    stage.context.taskIds = taskIds

    def responses = []
    status?.each {
      responses << new Response('http://foo.com', 200, 'OK', [], new TypedString("{\"status\" : \"${it}\"}"))
    }

    when:
    def result = task.execute(stage)

    then:
    taskIds.eachWithIndex { def entry, int i ->
      1 * instanceService.listTask(entry.value) >> responses.get(i)
    }

    result.status == executionStatus

    where:
    instances | taskIds | status | executionStatus
    ["foo.com"] | ["foo.com" : "abcd"] | ["Running"] | ExecutionStatus.RUNNING
    ["foo.com"] | ["foo.com" : "abcd"] | ["Successful"] | ExecutionStatus.SUCCEEDED
    ["foo.com"] | ["foo.com" : "abcd"] | ["Failed"] | ExecutionStatus.FAILED
    ["foo.com"] | ["foo.com" : "abcd"] |[null] | ExecutionStatus.FAILED

    ["foo.com", "foo2.com"] | ["foo.com" : "abcd", "foo2.com" : "efghij"] | ["Running", "Successful"] | ExecutionStatus.RUNNING
    ["foo.com", "foo2.com"] | ["foo.com" : "abcd", "foo2.com" : "efghij"] | ["Succeeded", "Running"] | ExecutionStatus.RUNNING

    ["foo.com", "foo2.com"] | ["foo.com" : "abcd", "foo2.com" : "efghij"] | ["Running", "Running"] | ExecutionStatus.RUNNING
    ["foo.com", "foo2.com"] | ["foo.com" : "abcd", "foo2.com" : "efghij"] | ["Successful", "Successful"] | ExecutionStatus.SUCCEEDED
    ["foo.com", "foo2.com"] | ["foo.com" : "abcd", "foo2.com" : "efghij"] | ["Successful", "Failed"] | ExecutionStatus.FAILED
    ["foo.com", "foo2.com"] | ["foo.com" : "abcd", "foo2.com" : "efghij"] | ["Failed", "Successful"] | ExecutionStatus.FAILED
  }

  @Unroll
  def "missing configuration"() {
    given:
    def pipe = new Pipeline.Builder()
      .withApplication("foo")
      .build()
    def stage = new PipelineStage(pipe, 'monitorQuip', [:])
    stage.context.instances = instances
    stage.context.taskIds = taskIds

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.FAILED

    where:
    instances | taskIds
    null | ["foo.com" : "abcd"]
    [:] | ["foo.com" : "abcd"]
    [["publicDnsName": "foo.com"]] | null
  }
}
