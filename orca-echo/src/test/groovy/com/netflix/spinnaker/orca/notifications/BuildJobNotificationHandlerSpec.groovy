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


package com.netflix.spinnaker.orca.notifications

import groovy.json.JsonSlurper
import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.mayo.MayoService
import com.netflix.spinnaker.orca.pipeline.Pipeline
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import retrofit.client.Response
import retrofit.mime.TypedInput
import spock.lang.Specification

class BuildJobNotificationHandlerSpec extends Specification {

  def pipeline1 = [
    name    : "pipeline1",
    triggers: [[type: "jenkins",
                job : "SPINNAKER-package-pond"]],
    stages  : [[type: "bake"],
               [type: "deploy", cluster: [name: "bar"]]]
  ]

  def pipeline2 = [
    name    : "pipeline2",
    triggers: [[type: "jenkins",
                job : "SPINNAKER-package-pond"]],
    stages  : [[type: "bake"],
               [type: "deploy", cluster: [name: "foo"]]]
  ]

  void "should pick up stages subsequent to build job completing"() {
    setup:
    PipelineStarter pipelineStarter = Mock(PipelineStarter)
    def handler = new BuildJobNotificationHandler(pipelineStarter: pipelineStarter, objectMapper: new ObjectMapper())
    handler.interestingPipelines["SPINNAKER-package-pond"] = [pipeline1]

    when:
    handler.handle(input)

    then:
    1 * pipelineStarter.start(_) >> { json ->
      def config = new JsonSlurper().parseText(json) as Map
      assert config.stages.size() == 2
      assert config.stages[0].type == "bake"
      assert config.stages[1].type == "deploy"
      assert config.trigger.type == "jenkins"
      assert config.trigger.buildInfo == input
      def pipeline = new Pipeline()
      pipeline.id = "1"
      return pipeline
    }

    where:
    input = [name: "SPINNAKER-package-pond", lastBuildStatus: "Success"]
  }

  void "should add multiple pipeline targets to single trigger type"() {
    setup:
    def mayo = Mock(MayoService)
    def pipelineStarter = Mock(PipelineStarter)
    def handler = new BuildJobNotificationHandler(pipelineStarter: pipelineStarter, objectMapper: new ObjectMapper(), mayoService: mayo)

    when:
    handler.run()

    then:
    1 * mayo.getPipelines() >> {
      def response = GroovyMock(Response)
      def typedInput = Mock(TypedInput)
      typedInput.in() >> {
        def json = new ObjectMapper().writeValueAsString([pipeline1, pipeline2])
        new ByteArrayInputStream(json.bytes)
      }
      response.getBody() >> typedInput
      response
    }
    2 == handler.interestingPipelines[job].size()
    handler.interestingPipelines[job].name == ["pipeline1", "pipeline2"]

    where:
    job = "SPINNAKER-package-pond"
  }
}
