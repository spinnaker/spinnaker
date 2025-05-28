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

package com.netflix.spinnaker.orca.igor.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.retrofit.exceptions.SpinnakerServerException
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.retrofit.exceptions.SpinnakerServerExceptionHandler
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.ResponseBody
import retrofit2.Response
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Subject

class StartJenkinsJobTaskSpec extends Specification {

  @Subject
  StartJenkinsJobTask task = new StartJenkinsJobTask()

  void setup(){
    task.objectMapper = Mock(ObjectMapper) {
      convertValue(_,_) >> [:]
    }

    task.spinnakerServerExceptionHandler = new SpinnakerServerExceptionHandler()
  }

  @Shared
  def pipeline = PipelineExecutionImpl.newPipeline("orca")

    def "should trigger build without parameters"() {
        given:
        def stage = new StageExecutionImpl(pipeline, "jenkins", [master: "builds", job: "orca"])

        and:
        task.buildService = Stub(BuildService) {
          build(stage.context.master, stage.context.job, stage.context.parameters, stage.startTime.toString()) >>
              Response.success(200, ResponseBody.create(MediaType.parse("application/json"),new ObjectMapper().writeValueAsString([result: 'SUCCESS', running: true, number: 4])))
        }

        when:
        def result = task.execute(stage)

        then:
        result.status == ExecutionStatus.SUCCEEDED
    }

  def "should trigger build with parameters"() {
      given:
      def stage = new StageExecutionImpl(pipeline, "jenkins", [master: "builds", job: "orca", parameters: [foo: "bar", version: "12345"]])

      and:
      task.buildService = Stub(BuildService) {
        build(stage.context.master, stage.context.job, stage.context.parameters, stage.startTime.toString()) >>
            Response.success(200, ResponseBody.create(MediaType.parse("application/json"),new ObjectMapper().writeValueAsString([result: 'SUCCESS', running: true, number: 4])))
      }

      when:
      def result = task.execute(stage)

      then:
      result.status == ExecutionStatus.SUCCEEDED
    }

    def "throw exception when you can't trigger a build"() {
        given:
        def stage = new StageExecutionImpl(pipeline, "jenkins", [master: "builds", job: "orca", parameters: [foo: "bar", version: "12345"]])

        and:
        task.buildService = Stub(BuildService) {
            build(stage.context.master, stage.context.job, stage.context.parameters, stage.startTime.toString()) >> {throw new SpinnakerServerException(new RuntimeException("some error"), new Request.Builder().url("http://some-url").build() )}
        }

        when:
        task.execute(stage)

        then:
        thrown(SpinnakerServerException)
    }

  def "handle 202 response from igor"() {
    given:
    def stage = new StageExecutionImpl(pipeline, "jenkins", [master: "builds", job: "orca"])

    and:
    task.buildService = Stub(BuildService) {
      build(stage.context.master, stage.context.job, stage.context.parameters, stage.startTime.toString()) >>
          Response.success(202, ResponseBody.create(MediaType.parse("application/json"), "[]"))
    }

    when:
    def result = task.execute(stage)

    then:
    result.status == ExecutionStatus.RUNNING
  }
}
