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

class V1SchemaExecutionGeneratorSpec extends Specification {

  @Subject
  ExecutionGenerator subject = new V1SchemaExecutionGenerator()

  def 'should create a basic execution json'() {
    given:
    // At this point, the template has already been rendered / merged into this state.
    PipelineTemplate template = new PipelineTemplate(
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
        ]
      ),
      stages: [
        new StageDefinition(
          id: 'bake',
          type: 'bake',
          requisiteStageRefIds: [] as Set,
          config: [
            regions: ['us-west-2', 'us-east-1'],
            package: 'orca-package',
            baseOs: 'trusty',
            vmType: 'hvm',
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
    TemplateConfiguration configuration = new TemplateConfiguration(
      pipeline: new PipelineDefinition(
        application: 'orca',
        name: 'My Template'
      )
    )

    when:
    def result = subject.generate(template, configuration)

    then:
    noExceptionThrown()
    result.id != null
    result.application == 'orca'
    result.name == 'My Template'
    result.parallel == true
    result.limitConcurrent == true
    result.keepWaitingPipelines == false
    result.stages*.type == ['bake', 'tagImage']
    result.stages*.requisiteStageRefIds == [[] as Set, ['bake'] as Set]
    result.stages.find { it.type == 'bake' }.baseOs == 'trusty'
  }
}
