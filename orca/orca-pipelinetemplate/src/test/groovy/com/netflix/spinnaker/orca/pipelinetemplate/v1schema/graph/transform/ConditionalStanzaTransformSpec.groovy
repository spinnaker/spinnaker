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
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.PipelineDefinition
import spock.lang.Specification
import spock.lang.Subject

class ConditionalStanzaTransformSpec extends Specification {

  TemplateConfiguration configuration = new TemplateConfiguration(
    pipeline: new PipelineDefinition(variables: [:])
  )

  @Subject ConditionalStanzaTransform subject = new ConditionalStanzaTransform(configuration, [:])

  def 'should remove falsy conditional stages'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: [
        new StageDefinition(
          id: 's1',
          when: [
            'true'
          ]
        ),
        new StageDefinition(
          id: 's2',
          when: [
            'true',
            'false'
          ]
        )
      ]
    )

    when:
    subject.visitPipelineTemplate(template)

    then:
    noExceptionThrown()
    !template.stages.find { it.id == 's1' }.removed
    template.stages.find { it.id == 's2' }.removed
  }
}
