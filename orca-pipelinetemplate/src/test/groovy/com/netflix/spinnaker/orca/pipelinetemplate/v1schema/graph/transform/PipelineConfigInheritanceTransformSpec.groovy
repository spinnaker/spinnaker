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

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedHashMap
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Configuration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.PipelineConfiguration
import spock.lang.Specification

class PipelineConfigInheritanceTransformSpec extends Specification {

  def 'should inherit defined configurations'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      configuration: new Configuration(
        concurrentExecutions: [
          key: 'value'
        ],
        triggers: [
          new NamedHashMap().with {
            put('name', 'trigger1')
            it
          }
        ],
        expectedArtifacts: [
          new NamedHashMap().with {
            put('kind', 'custom')
            put('type', 'artifactType')
            it
          }
        ],
        parameters: [
          new NamedHashMap().with {
            put('name', 'param1')
            it
          }
        ],
        notifications: [
          new NamedHashMap().with {
            put('name', 'notify1')
            it
          }
        ]
      )
    )

    TemplateConfiguration configuration = new TemplateConfiguration(
      configuration: new PipelineConfiguration(
        inherit: inherit
      )
    )

    when:
    new PipelineConfigInheritanceTransform(configuration).visitPipelineTemplate(template)

    then:
    template.configuration.concurrentExecutions.size() == (inherit.contains('concurrentExecutions') ? 1 : 0)
    template.configuration.triggers.size() == (inherit.contains('triggers') ? 1 : 0)
    template.configuration.expectedArtifacts.size() == (inherit.contains('expectedArtifacts') ? 1 : 0)
    template.configuration.parameters.size() == (inherit.contains('parameters') ? 1 : 0)
    template.configuration.notifications.size() == (inherit.contains('notifications') ? 1 : 0)

    where:
    inherit << [
      [],
      ['triggers'],
      ['concurrentExecutions', 'triggers', 'parameters', 'notifications']
    ]
  }
}
