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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.transform

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import spock.lang.Ignore
import spock.lang.Specification

class ConfigStageInjectionTransformSpec extends Specification {

  def 'should add all stages from configuration if none in template'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: []
    )

    TemplateConfiguration configuration = new TemplateConfiguration(
      stages: [
        new StageDefinition(id: 's1')
      ]
    )

    when:
    new ConfigStageInjectionTransform(configuration).visitPipelineTemplate(template)

    then:
    template.stages*.id == ['s1']
  }

  def 'should replace stages from configuration into template'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: [
        new StageDefinition(id: 's1', type: 'findImageFromTags'),
        new StageDefinition(id: 's2', type: 'deploy')
      ]
    )

    TemplateConfiguration configuration = new TemplateConfiguration(
      stages: [
        new StageDefinition(id: 's1', type: 'findImageFromCluster'),
      ]
    )

    when:
    new ConfigStageInjectionTransform(configuration).visitPipelineTemplate(template)

    then:
    template.stages*.id == ['s1', 's2']
    template.stages.find { it.id == 's1' }.type == 'findImageFromCluster'
  }

  @Ignore(value = 'inject not implemented yet')
  def 'should inject stages from configuration into template'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: [
        new StageDefinition(id: 's1', type: 'findImageFromTags'),
        new StageDefinition(id: 's2', type: 'deploy')
      ]
    )

    TemplateConfiguration configuration = new TemplateConfiguration(
      stages: [
        new StageDefinition(id: 's3', type: 'manualJudgement', inject: [before: 's2'])
      ]
    )

    when:
    new ConfigStageInjectionTransform(configuration).visitPipelineTemplate(template)

    then:
    template.stages*.id == ['s1', 's3', 's2']
  }
}
