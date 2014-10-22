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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.Pipeline
import com.netflix.spinnaker.orca.pipeline.PipelineStarter
import groovy.json.JsonSlurper
import spock.lang.Specification

class BuildJobNotificationHandlerSpec extends Specification {

  void "should pick up stages subsequent to build job completing"() {
    setup:
      PipelineStarter pipelineStarter = Mock(PipelineStarter)
      def handler = new BuildJobNotificationHandler(pipelineStarter: pipelineStarter, objectMapper: new ObjectMapper())
      handler.interestingPipelines["SPINNAKER-package-pond"] = [
          stages: [[type: "jenkins",
                    name: "SPINNAKER-package-pond"],
                   [type: "bake"],
                   [type: "deploy"]]
      ]

    when:
      handler.handle([name: "SPINNAKER-package-pond", lastBuildStatus: "Success"])

    then:
      1 * pipelineStarter.start(_) >> { json ->
        def config = new JsonSlurper().parseText(json) as Map
        assert config.stages.size() == 2
        assert config.stages[0].type == "bake"
        assert config.stages[1].type == "deploy"
        def pipeline = new Pipeline()
        pipeline.id = "1"
        rx.Observable.from(pipeline)
      }
  }
}
