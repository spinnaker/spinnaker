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

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spectator.api.Clock
import com.netflix.spectator.api.Registry
import com.netflix.spectator.api.Timer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedHashMap
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Configuration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Variable
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.PipelineConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.PipelineDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateModule
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.HandlebarsRenderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import spock.lang.Specification

class RenderTransformSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()

  Renderer renderer = new HandlebarsRenderer(objectMapper)

  Registry registry = Mock() {
    clock() >> Mock(Clock) {
      monotonicTime() >> 0L
    }
    timer(_) >> Mock(Timer)
  }

  def 'should render template'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      id: 'deployToPrestaging',
      schema: '1',
      variables: [
        new Variable(name: 'regions', type: 'list'),
        new Variable(name: 'slackChannel', type: 'string', defaultValue: '#det')
      ],
      configuration: new Configuration(
        triggers: [
          new NamedHashMap().with {
            put('name', 'trigger1')
            put('type', 'jenkins')
            put('application', '{{ application }}')
            put('pipeline', 'Deploy to Test')
            put('pipelineStatus', ['successful'])
            put('enabled', true)
            it
          }
        ]
      ),
      stages: [
        new StageDefinition(
          id: 'findImage',
          type: 'findImageFromTags',
          config: [
            package: '{{ application }}',
            regions: '{{ json regions }}',
            tags: [stack: 'test']
          ]
        ),
        new StageDefinition(
          id: 'manualjudgment',
          type: 'manualjudgment',
          dependsOn: ['findImage'],
          config: [
            propagateAuthentication: true,
            notifications: [
              type: 'slack',
              channel: '{{slackChannel}}',
              when: ['awaiting']
            ]
          ]
        ),
        new StageDefinition(
          id: 'deploy',
          type: 'deploy',
          dependsOn: ['manualJudgment'],
          config: [
            clusters: '[{{#each regions}}{{module "deployCluster" region=this}}{{#unless @last}},{{/unless}}{{/each}}]'
          ]
        )
      ],
      modules: [
        new TemplateModule(
          id: 'deployCluster',
          usage: 'Deploy a cluster into an AWS region',
          variables: [
            new NamedHashMap().with {
              put('name', 'region')
              it
            }
          ],
          definition: [
            provider: 'aws',
            region: '{{ region }}',
            securityGroups: ['{{ application }}']
          ]
        )
      ]
    )
    TemplateConfiguration configuration = new TemplateConfiguration(
      id: 'gateDeployToPrestaging',
      schema: '1',
      pipeline: new PipelineDefinition(
        application: 'gate',
        name: 'Deploy to Prestaging',
        variables: [
          regions: ['us-west-2', 'us-east-1']
        ]
      ),
      configuration: new PipelineConfiguration(inherit: ['triggers'])
    )

    when:
    new RenderTransform(configuration, renderer, registry, [:]).visitPipelineTemplate(template)

    then:
    noExceptionThrown()
    findStage(template, 'findImage').config['package'] == 'gate'
    findStage(template, 'findImage').config['regions'] == ['us-west-2', 'us-east-1']
    findStage(template, 'deploy').config['clusters'] == [
      [
        provider: 'aws',
        region: 'us-west-2',
        securityGroups: ['gate']
      ],
      [
        provider: 'aws',
        region: 'us-east-1',
        securityGroups: ['gate']
      ]
    ]
  }

  StageDefinition findStage(PipelineTemplate template, String id) {
    return template.stages.find { it.id == id }
  }
}
