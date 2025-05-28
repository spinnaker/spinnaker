/*
 * Copyright 2017 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipelinetemplate.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.front50.pipeline.UpdatePipelineStage
import com.netflix.spinnaker.orca.pipeline.graph.StageGraphBuilderImpl
import com.netflix.spinnaker.orca.pipeline.model.PipelineExecutionImpl
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Configuration
import retrofit2.mock.Calls
import spock.lang.Specification

class UpdatePipelineTemplateStageSpec extends Specification {

  def front50Service = Mock(Front50Service)
  def stageBuilder = new UpdatePipelineTemplateStage()

  def setup() {
    stageBuilder.updatePipelineStage = new UpdatePipelineStage()
    stageBuilder.pipelineTemplateObjectMapper = new ObjectMapper()
    stageBuilder.front50Service = front50Service
  }

  def "should create synthetic save pipeline stages for each dependent pipeline"() {
    setup:
    def pipelineTemplate = new PipelineTemplate(
      schema: "1",
      id: 'myTemplate',
      metadata: [
        name: 'myTemplate'
      ],
      configuration: new Configuration(
        triggers: [
          [
            name: 'myTrigger',
            type: 'jenkins'
          ]
        ]
      ),
      variables: []
    )

    def pipeline1 = [
      id: 'one',
      config: [
        configuration: [
          inherit: ['triggers']
        ]
      ]
    ]
    def pipeline2 = [
      id: 'two',
      config: [
        configuration: [
          inherit: ['triggers']
        ]
      ]
    ]
    def pipeline3 = [
      id: 'three',
      config: [
        configuration: [
          inherit: []
        ]
      ]
    ]

    and:
    def config = [pipelineTemplate: Base64.encoder.encodeToString(new ObjectMapper().writeValueAsString(pipelineTemplate).bytes)]
    def stage = new StageExecutionImpl(PipelineExecutionImpl.newPipeline("orca"), "updatePipelineTemplate", config)
    def graphBefore = StageGraphBuilderImpl.beforeStages(stage)
    def graphAfter = StageGraphBuilderImpl.afterStages(stage)

    when:
    stageBuilder.beforeStages(stage, graphBefore)
    stageBuilder.afterStages(stage, graphAfter)

    def beforeStages = graphBefore.build()
    def afterStages = graphAfter.build()

    then:
    1 * front50Service.getPipelineTemplateDependents("myTemplate", true) >> {
      Calls.response([pipeline1, pipeline2, pipeline3])
    }

    afterStages.size() == 3
    beforeStages.size() == 0
  }
}
