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
package com.netflix.spinnaker.orca.pipelinetemplate.v1schema

import com.netflix.spinnaker.orca.pipelinetemplate.TemplatedPipelineRequest
import com.netflix.spinnaker.orca.pipelinetemplate.generator.ExecutionGenerator

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.NamedHashMap
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Configuration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate.Variable
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration.PipelineDefinition
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class V1SchemaExecutionGeneratorSpec extends Specification {

  @Subject
  ExecutionGenerator subject = new V1SchemaExecutionGenerator()

  def 'should create a basic execution json'() {
    given:
    // At this point, the template has already been rendered / merged into this state.
    PipelineTemplate template = getPipelineTemplate()
    TemplateConfiguration configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition(
        application: 'orca',
        name: 'My Template'
      )
    )

    when:
    def result = subject.generate(template, configuration, new TemplatedPipelineRequest(id: "124"))

    then:
    noExceptionThrown()
    result.id != null
    result.application == 'orca'
    result.name == 'My Template'
    result.limitConcurrent == true
    result.keepWaitingPipelines == false
    result.stages*.type == ['bake', 'tagImage']
    result.stages*.requisiteStageRefIds == [[] as Set, ['bake'] as Set]
    result.stages.find { it.type == 'bake' }.baseOs == 'trusty'
  }

  @Unroll
  def "should fallback to pipelineConfigId if id is not set"() {
    given:
    PipelineTemplate template = getPipelineTemplate()
    TemplateConfiguration configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition(
        pipelineDefinitionData
      )
    )

    when:
    def result = subject.generate(template, configuration, new TemplatedPipelineRequest(id: pipelineId))

    then:
    result.id == expectedId

    where:
    pipelineId | pipelineDefinitionData             || expectedId
    "124"  | [application: 'orca',
              pipelineConfigId: 'pipelineConfigId'] || "124"
    null   | [application: 'orca',
              pipelineConfigId: 'pipelineConfigId'] || "pipelineConfigId"
    "124"  | [application: 'orca']                  || "124"
  }

  @Unroll
  def "should set notifications in execution json"() {
    given:
    PipelineTemplate template = getPipelineTemplate()
    TemplateConfiguration configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition([application: 'orca', pipelineConfigId: 'pipelineConfigId']),
      configuration: new TemplateConfiguration.PipelineConfiguration(
        inherit: inherit,
        notifications: [
          new NamedHashMap().with {
            put('name', 'configuration-notification')
            put('address', 'email-from-configuration@spinnaker.io')
            it
          }
        ]
      )
    )

    when:
    def result = subject.generate(template, configuration, new TemplatedPipelineRequest(id: "pipelineConfigId"))

    then:
    result.notifications*.address == addresses

    where:
    inherit           || addresses
    ['notifications'] || ['email-from-template@spinnaker.io', 'email-from-configuration@spinnaker.io']
    []                || ['email-from-configuration@spinnaker.io']
  }

  @Unroll
  def "should set expected artifacts in execution json"() {
    given:
    PipelineTemplate template = getPipelineTemplate()
    template.configuration.setExpectedArtifacts([createExpectedArtifact('artifact-from-template')])

    TemplateConfiguration configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition([application: 'orca', pipelineConfigId: 'pipelineConfigId']),
      configuration: new TemplateConfiguration.PipelineConfiguration(
        inherit: inherit,
        expectedArtifacts: [
          createExpectedArtifact('artifact-from-configuration')
        ]
      )
    )

    when:
    def request = new TemplatedPipelineRequest(id: "pipelineConfigId")
    if (artifactInRequest) {
      request.setExpectedArtifacts([createExpectedArtifact('artifact-from-request')])
    }
    def result = subject.generate(template, configuration, request)


    then:
    result.expectedArtifacts*.id == expectedArtifactIds

    where:
    inherit               | artifactInRequest || expectedArtifactIds
    ['expectedArtifacts'] | false             || ['artifact-from-template', 'artifact-from-configuration']
    []                    | false             || ['artifact-from-configuration']
    []                    | true              || ['artifact-from-request', 'artifact-from-configuration']
    ['expectedArtifacts'] | true              || ['artifact-from-template', 'artifact-from-request', 'artifact-from-configuration']
  }

  @Unroll
  def "should override expected artifacts in execution json"() {
    given:
    PipelineTemplate template = getPipelineTemplate()
    if (artifactInTemplate) {
      template.configuration.setExpectedArtifacts([createExpectedArtifact('artifact', 'from-template')])
    }

    TemplateConfiguration configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition([application: 'orca', pipelineConfigId: 'pipelineConfigId']),
      configuration: new TemplateConfiguration.PipelineConfiguration(
        inherit: ['expectedArtifacts']
      )
    )

    if (artifactInConfiguration) {
      configuration.getConfiguration().setExpectedArtifacts([createExpectedArtifact('artifact', 'from-configuration')])
    }

    when:
    def request = new TemplatedPipelineRequest(id: "pipelineConfigId")
    if (artifactInRequest) {
      request.setExpectedArtifacts([createExpectedArtifact('artifact', 'from-request')])
    }
    def result = subject.generate(template, configuration, request)


    then:
    result.expectedArtifacts*.displayName == expectedArtifactNames

    where:
    artifactInTemplate | artifactInConfiguration | artifactInRequest || expectedArtifactNames
    true               | true                    | true              || ['from-request']
    true               | true                    | false             || ['from-configuration']
    true               | false                   | true              || ['from-request']
    false              | true                    | true              || ['from-request']
  }

  private PipelineTemplate getPipelineTemplate() {
    new PipelineTemplate(
      id: 'simpleTemplate',
      variables: [
        new Variable(name: 'regions', type: 'list')
      ],
      configuration: new Configuration(
        triggers: [
          new NamedHashMap().with {
            put('type', 'jenkins')
            put('master', 'spinnaker')
            put('job', 'SPINNAKER-package-orca')
            put('enabled', true)
            it
          }
        ],
        notifications: [
          new NamedHashMap().with {
            put('name', 'template-notification')
            put('address', 'email-from-template@spinnaker.io')
            it
          }
        ]
      ),
      stages: [
        new StageDefinition(
          id: 'bake',
          type: 'bake',
          requisiteStageRefIds: [] as Set,
          config: [
            regions  : ['us-west-2', 'us-east-1'],
            package  : 'orca-package',
            baseOs   : 'trusty',
            vmType   : 'hvm',
            storeType: 'ebs',
            baseLabel: 'release',
          ],
        ),
        new StageDefinition(
          id: 'tagImage',
          type: 'tagImage',
          dependsOn: ['bake'],
          requisiteStageRefIds: ['bake'] as Set,
          config: [tags: [stack: 'test']]
        )
      ]
    )
  }

  private HashMap<String, Object> createExpectedArtifact(String id, String name = null) {
    def effectiveName = name ?: id
    return [
      id: id,
      displayName: effectiveName,
      defaultArtifact: [customKind: true],
      matchArtifact: [customKind: true, type: 'http/file', name: effectiveName],
      useDefaultArtifact: false,
      usePriorArtifact: false
    ]
  }
}
