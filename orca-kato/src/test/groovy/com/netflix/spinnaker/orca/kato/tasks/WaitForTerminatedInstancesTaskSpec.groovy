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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.SimpleTaskContext
import com.netflix.spinnaker.orca.TaskResult
import com.netflix.spinnaker.orca.oort.OortService
import retrofit.client.Response
import retrofit.mime.TypedInput
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class WaitForTerminatedInstancesTaskSpec extends Specification {

  @Subject task = new WaitForTerminatedInstancesTask()

  def mapper = new ObjectMapper()

  @Unroll
  void "should return #taskStatus status when #matches found via oort search"() {
    given:
    def instanceId = 'i-123456'
    task.objectMapper = mapper
    def response = GroovyMock(Response)
    response.getStatus() >> 200
    response.getBody() >> {
      def input = Mock(TypedInput)
      input.in() >> {
        def jsonObj = [
          [ totalMatches: matches ]
        ]
        new ByteArrayInputStream(mapper.writeValueAsString(jsonObj).bytes)
      }
      input
    }
    task.oortService = Stub(OortService) {
      getSearchResults(instanceId, 'serverGroupInstances', 'aws') >> response
    }

    and:
    def context = new SimpleTaskContext()
    context."terminate.instance.ids" = [instanceId]

    expect:
    task.execute(context).status == taskStatus

    where:
    matches || taskStatus
    0       || TaskResult.Status.SUCCEEDED
    1       || TaskResult.Status.RUNNING
  }

  void "should return RUNNING status when search returns error"() {
    given:
    def instanceId = 'i-123456'
    task.objectMapper = mapper
    def response = GroovyMock(Response)
    response.getStatus() >> 500

    task.oortService = Stub(OortService) {
      getSearchResults(instanceId, 'serverGroupInstances', 'aws') >> response
    }

    and:
    def context = new SimpleTaskContext()
    context."terminate.instance.ids" = [instanceId]

    expect:
    task.execute(context).status == TaskResult.Status.RUNNING
  }

  void "should return RUNNING status when search returns multiple result sets"() {
    given:
    def instanceId = 'i-123456'
    task.objectMapper = mapper
    def response = GroovyMock(Response)
    response.getStatus() >> 200
    response.getBody() >> {
      def input = Mock(TypedInput)
      input.in() >> {
        def jsonObj = [ [:], [:] ]
        new ByteArrayInputStream(mapper.writeValueAsString(jsonObj).bytes)
      }
      input
    }
    task.oortService = Stub(OortService) {
      getSearchResults(instanceId, 'serverGroupInstances', 'aws') >> response
    }

    and:
    def context = new SimpleTaskContext()
    context."terminate.instance.ids" = [instanceId]

    expect:
    task.execute(context).status == TaskResult.Status.RUNNING
  }

  void "should search all instanceIds"() {
    given:
    def instanceIds = ['i-123456', 'i-654321']
    task.objectMapper = mapper
    def responses = instanceIds.collect {
      def response = GroovyMock(Response)
      response.getStatus() >> 200
      response.getBody() >> {
        def input = Mock(TypedInput)
        input.in() >> {
          def jsonObj = [
            [ totalMatches: 0 ]
          ]
          new ByteArrayInputStream(mapper.writeValueAsString(jsonObj).bytes)
        }
        input
      }
      response
    }
    task.oortService = Stub(OortService) {
      getSearchResults(instanceIds[0], 'serverGroupInstances', 'aws') >> responses[0]
      getSearchResults(instanceIds[1], 'serverGroupInstances', 'aws') >> responses[1]
    }

    and:
    def context = new SimpleTaskContext()
    context."terminate.instance.ids" = instanceIds

    expect:
    task.execute(context).status == TaskResult.Status.SUCCEEDED
  }

  void "should return running if any instance found via search"() {
    given:
    def instanceIds = ['i-123456', 'i-654321']
    task.objectMapper = mapper
    def response = GroovyMock(Response)
    response.getStatus() >> 200
    response.getBody() >> {
      def input = Mock(TypedInput)
      input.in() >> {
        def jsonObj = [
          [ totalMatches: 1 ]
        ]
        new ByteArrayInputStream(mapper.writeValueAsString(jsonObj).bytes)
      }
      input
    }
    task.oortService = Stub(OortService) {
      getSearchResults(instanceIds[0], 'serverGroupInstances', 'aws') >> response
    }

    and:
    def context = new SimpleTaskContext()
    context."terminate.instance.ids" = instanceIds

    expect:
    task.execute(context).status == TaskResult.Status.RUNNING
  }
}
