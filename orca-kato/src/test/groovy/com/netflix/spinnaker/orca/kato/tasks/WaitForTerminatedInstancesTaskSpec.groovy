/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.spinnaker.orca.kato.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.jackson.OrcaObjectMapper
import com.netflix.spinnaker.orca.oort.OortService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.client.Response
import retrofit.mime.TypedInput
import retrofit.mime.TypedString
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForTerminatedInstancesTaskSpec extends Specification {

  @Subject task = new WaitForTerminatedInstancesTask()

  def mapper = new OrcaObjectMapper()

  @Unroll
  void "should return #taskStatus status when #matches found via oort search"() {
    given:
    def pipeline = new Pipeline()
    def instanceId = 'i-123456'
    task.objectMapper = mapper
    task.oortService = Stub(OortService) {
      getSearchResults(instanceId, 'serverGroupInstances', 'aws') >> { new Response('oort', 200, 'ok', [], new TypedString(mapper.writeValueAsString([[totalMatches: matches]]))) }
    }

    and:
    def stage = new PipelineStage(pipeline, "whatever", [
      "instance.ids": [instanceId]
    ]).asImmutable()

    expect:
    task.execute(stage).status == taskStatus

    where:
    matches || taskStatus
    0 || ExecutionStatus.SUCCEEDED
    1 || ExecutionStatus.RUNNING
  }

  void "should return RUNNING status when search returns error"() {
    given:
    def pipeline = new Pipeline()
    def instanceId = 'i-123456'
    task.objectMapper = mapper
    def response = GroovyMock(Response)
    response.getStatus() >> 500

    task.oortService = Stub(OortService) {
      getSearchResults(instanceId, 'serverGroupInstances', 'aws') >> response
    }

    and:
    def stage = new PipelineStage(pipeline, "whatever", [
      "instance.ids": [instanceId]
    ]).asImmutable()

    expect:
    task.execute(stage).status == ExecutionStatus.RUNNING
  }

  void "should return RUNNING status when search returns multiple result sets"() {
    given:
    def pipeline = new Pipeline()
    def instanceId = 'i-123456'
    task.objectMapper = mapper
    def response = GroovyMock(Response)
    response.getStatus() >> 200
    response.getBody() >> {
      def input = Mock(TypedInput)
      input.in() >> {
        def jsonObj = [[:], [:]]
        new ByteArrayInputStream(mapper.writeValueAsString(jsonObj).bytes)
      }
      input
    }
    task.oortService = Stub(OortService) {
      getSearchResults(instanceId, 'serverGroupInstances', 'aws') >> response
    }

    and:
    def stage = new PipelineStage(pipeline, "whatever", [
      "instance.ids": [instanceId]
    ]).asImmutable()

    expect:
    task.execute(stage).status == ExecutionStatus.RUNNING
  }

  void "should search all instanceIds"() {
    given:
    def pipeline = new Pipeline()
    def instanceIds = ['i-123456', 'i-654321']
    def emptyResult = new Response('oort', 200, 'ok', [], new TypedString('[{"totalMatches":0}]'))
    task.objectMapper = mapper
    task.oortService = Stub(OortService) {
      getSearchResults(instanceIds[0], 'serverGroupInstances', 'aws') >> emptyResult
      getSearchResults(instanceIds[1], 'serverGroupInstances', 'aws') >> emptyResult
    }

    and:
    def stage = new PipelineStage(pipeline, "whatever", [
      "instance.ids": instanceIds
    ]).asImmutable()

    expect:
    task.execute(stage).status == ExecutionStatus.SUCCEEDED
  }

  void "should return running if any instance found via search"() {
    given:
    def pipeline = new Pipeline()
    def instanceIds = ['i-123456', 'i-654321']
    task.objectMapper = mapper
    def response = GroovyMock(Response)
    response.getStatus() >> 200
    response.getBody() >> {
      def input = Mock(TypedInput)
      input.in() >> {
        def jsonObj = [
          [totalMatches: 1]
        ]
        new ByteArrayInputStream(mapper.writeValueAsString(jsonObj).bytes)
      }
      input
    }
    task.oortService = Stub(OortService) {
      getSearchResults(instanceIds[0], 'serverGroupInstances', 'aws') >> response
    }

    and:
    def stage = new PipelineStage(pipeline, "whatever", [
      "instance.ids": instanceIds
    ]).asImmutable()

    expect:
    task.execute(stage).status == ExecutionStatus.RUNNING
  }
}
