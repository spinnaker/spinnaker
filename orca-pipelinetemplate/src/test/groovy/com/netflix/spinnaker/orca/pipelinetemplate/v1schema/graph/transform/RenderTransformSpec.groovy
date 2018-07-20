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
import com.netflix.spinnaker.orca.front50.Front50Service
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedHashMap
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PartialDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Configuration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Variable
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition.InjectionRule
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.PipelineConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.PipelineDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateModule
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.JinjaRenderer
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.render.Renderer
import spock.lang.Specification

class RenderTransformSpec extends Specification {

  ObjectMapper objectMapper = new ObjectMapper()

  Renderer renderer = new JinjaRenderer(objectMapper, Mock(Front50Service), [])

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
        new Variable(name: 'slackChannel', type: 'string', defaultValue: '#spinnaker'),
        new Variable(name: 'deployStageName', defaultValue: 'Deploy to Env')
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
            regions: '{{ regions|json }}',
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
          ],
          when: [
            "{{ application == 'blah' }}"
          ]
        ),
        new StageDefinition(
          id: 'deploy',
          type: 'deploy',
          name: '{{deployStageName}}',
          dependsOn: ['manualJudgment'],
          config: [
            clusters: '[{% for region in regions %}{% module deployCluster region=region %}{% if not loop.last %},{% endif %}{% endfor %}]'
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
            availabilityZones: [
              "{{ region }}": ["{{ region }}a"]
            ],
            securityGroups: ['{{ application }}']
          ]
        )
      ]
    )
    TemplateConfiguration configuration = new TemplateConfiguration(
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
        availabilityZones: [
          'us-west-2': ["us-west-2a"]
        ],
        securityGroups: ['gate']
      ],
      [
        provider: 'aws',
        availabilityZones: [
          'us-east-1': ['us-east-1a']
        ],
        securityGroups: ['gate']
      ]
    ]
    findStage(template, 'deploy').name == 'Deploy to Env'
    findStage(template, 'manualjudgment').when == ['false']
  }

  def 'should render partials'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: [
        new StageDefinition(
          id: 'foo',
          type: 'partial.myPartial',
          config: [
            waitTime: 5
          ]
        ),
        new StageDefinition(
          id: 'bar',
          type: 'partial.myPartial',
          config: [
            waitTime: 10
          ]
        )
      ],
      partials: [
        new PartialDefinition(
          id: 'myPartial',
          variables: [
            new NamedHashMap().with {
              put('name', 'waitTime')
              it
            }
          ],
          stages: [
            new StageDefinition(
              id: 'wait',
              type: 'wait',
              config: [
                waitTime: '{{ waitTime }}'
              ]
            )
          ]
        )
      ]
    )
    TemplateConfiguration configuration = new TemplateConfiguration(
      schema: '1',
      pipeline: new PipelineDefinition(
        application: 'orca',
        name: 'Wait',
        variables: [:]
      ),
      configuration: new PipelineConfiguration()
    )

    when:
    new RenderTransform(configuration, renderer, registry, [:]).visitPipelineTemplate(template)

    then:
    noExceptionThrown()
    template.partials[0].renderedPartials['foo']*.id == ['foo.wait']
    template.partials[0].renderedPartials['foo']*.config == [[waitTime: 5]]
    template.partials[0].renderedPartials['bar']*.id == ['bar.wait']
    template.partials[0].renderedPartials['bar']*.config == [[waitTime: 10]]
  }

  def 'should render partials from config'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: [],
      partials: [
        new PartialDefinition(
          id: 'myPartial',
          variables: [
            new NamedHashMap().with {
              put('name', 'waitTime')
              it
            }
          ],
          stages: [
            new StageDefinition(
              id: 'wait',
              type: 'wait',
              config: [
                waitTime: '{{ waitTime }}'
              ]
            ),
            new StageDefinition(
              id: 'wait2',
              type: 'wait',
              config: [
                waitTime: '{{ waitTime * 2 }}'
              ],
              dependsOn: ['wait']
            )
          ]
        )
      ]
    )
    TemplateConfiguration configuration = new TemplateConfiguration(
      schema: '1',
      pipeline: new PipelineDefinition(
        application: 'orca',
        name: 'Wait',
        variables: [:]
      ),
      stages: [
          new StageDefinition(
            id: 'foo',
            type: 'partial.myPartial',
            config: [
              waitTime: 5
            ],
            inject: new InjectionRule(first: true)
          ),
          new StageDefinition(
            id: 'bar',
            type: 'partial.myPartial',
            config: [
              waitTime: 10
            ],
            inject: new InjectionRule(after: ['foo'])
          )
        ],
      configuration: new PipelineConfiguration()
    )

    when:
    new RenderTransform(configuration, renderer, registry, [:]).visitPipelineTemplate(template)

    then:
    noExceptionThrown()
    template.stages.size() == 0
    template.partials[0].renderedPartials['foo']*.id == ['foo.wait', 'foo.wait2']
    template.partials[0].renderedPartials['foo']*.config == [[waitTime: 5], [waitTime: 10]]
    template.partials[0].renderedPartials['bar']*.id == ['bar.wait', 'bar.wait2']
    template.partials[0].renderedPartials['bar']*.config == [[waitTime: 10], [waitTime: 20]]
  }

  def 'should expand and render looped stages'() {
    given:
    PipelineTemplate template = new PipelineTemplate()
    TemplateConfiguration configuration = new TemplateConfiguration(
      schema: '1',
      pipeline: new PipelineDefinition(
        application: 'orca',
        name: 'Wait',
        variables: [
          waitTime: 10,
          waitTimes: [2, 5]
        ]
      ),
      stages: [
        new StageDefinition(
          id: 'wait',
          type: 'wait',
          config: [
            waitTime: '{{ waitTime }}'
          ],
        ),
        new StageDefinition(
          id: 'wait2',
          type: 'wait',
          config: [
            waitTime: '{{ templateLoop.value }}'
          ],
          dependsOn: ['wait'],
          loopWith: "{{ waitTimes }}"
        )
      ],
      configuration: new PipelineConfiguration()
    )

    when:
    new RenderTransform(configuration, renderer, registry, [:]).visitPipelineTemplate(template)

    then:
    noExceptionThrown()
    // Loops are not injected to the graph at this point, just rendered
    configuration.stages.size() == 2
    configuration.stages[0].config.waitTime == 10
    configuration.stages[1].loopedStages*.config.waitTime == [2, 5]
  }

  StageDefinition findStage(PipelineTemplate template, String id) {
    return template.stages.find { it.id == id }
  }
}
