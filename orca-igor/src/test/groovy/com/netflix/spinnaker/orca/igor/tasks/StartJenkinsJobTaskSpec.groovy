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
import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.igor.BuildService
import com.netflix.spinnaker.orca.pipeline.model.Pipeline
import com.netflix.spinnaker.orca.pipeline.model.PipelineStage
import retrofit.RetrofitError
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
  }

  @Shared
  Pipeline pipeline = new Pipeline()

    def "should trigger build without parameters"() {
        given:
        def stage = new PipelineStage(pipeline, "jenkins", [master: "builds", job: "orca"])

        and:
        task.buildService = Stub(BuildService) {
           build(stage.context.master, stage.context.job, stage.context.parameters) >> [result: 'SUCCESS', running: true, number: 4]
        }

        when:
        def result = task.execute(stage)

        then:
        result.status == ExecutionStatus.SUCCEEDED
    }

  def "should trigger build with parameters"() {
      given:
      def stage = new PipelineStage(pipeline, "jenkins", [master: "builds", job: "orca", parameters: [foo: "bar", version: "12345"]])

      and:
      task.buildService = Stub(BuildService) {
          build(stage.context.master, stage.context.job, stage.context.parameters) >> [ result : 'SUCCESS', running: true, number: 4 ]
      }

      when:
      def result = task.execute(stage)

      then:
      result.status == ExecutionStatus.SUCCEEDED
    }

    def "throw exception when you can't trigger a build"() {
        given:
        def stage = new PipelineStage(pipeline, "jenkins", [master: "builds", job: "orca", parameters: [foo: "bar", version: "12345"]])

        and:
        task.buildService = Stub(BuildService) {
            build(stage.context.master, stage.context.job, stage.context.parameters) >> {throw RetrofitError.unexpectedError("http://test", new RuntimeException())}
        }

        when:
        task.execute(stage)

        then:
        thrown(RetrofitError)
    }
}
