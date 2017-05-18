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

import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PartialDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition.InjectionRule
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.TemplateConfiguration
import spock.lang.Specification
import spock.lang.Unroll

class ConfigStageInjectionTransformSpec extends Specification {

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

  @Unroll('#subject should have #requisiteStages as requisites')
  def 'should create dag from dependsOn'() {
    given:
    def stages = [
      new StageDefinition(id: 's1', type: 'findImageFromTags'),
      new StageDefinition(id: 's2', type: 'deploy', dependsOn: ['s1']),
      new StageDefinition(id: 's3', type: 'jenkins', dependsOn: ['s1']),
      new StageDefinition(id: 's4', type: 'wait', dependsOn: ['s2']),
      new StageDefinition(id: 's5', type: 'wait', dependsOn: ['s3'])
    ]

    when:
    def result = ConfigStageInjectionTransform.createGraph(stages)

    then:
    requisiteStageIds(subject, result) == requisiteStages as Set

    where:
    subject || requisiteStages
    's1'    || []
    's2'    || ['s1']
    's3'    || ['s1']
    's4'    || ['s2']
    's5'    || ['s3']
  }

  def 'should detect a cycle in dag creation'() {
    given:
    def stages = [
      new StageDefinition(id: 's2', type: 'deploy', dependsOn: ['s1']),
      new StageDefinition(id: 's1', type: 'wait', dependsOn: ['s2']),
      new StageDefinition(id: 's3', type: 'findImageFromTags'),
      new StageDefinition(id: 's5', type: 'wait', dependsOn: ['s3'])
    ]

    when:
    ConfigStageInjectionTransform.createGraph(stages)

    then:
    thrown(IllegalStateException)
  }

  def 'should inject stage into dag'() {
    given:
    // s1 <- s2 <- s4
    //     \- s3 <- s5
    def templateBuilder = {
      new PipelineTemplate(
        stages: [
          new StageDefinition(id: 's1', type: 'findImageFromTags'),
          new StageDefinition(id: 's2', type: 'deploy', dependsOn: ['s1']),
          new StageDefinition(id: 's3', type: 'jenkins', dependsOn: ['s1']),
          new StageDefinition(id: 's4', type: 'wait', dependsOn: ['s2']),
          new StageDefinition(id: 's5', type: 'wait', dependsOn: ['s3'])
        ]
      )
    }

    def configBuilder = { injectRule ->
      new TemplateConfiguration(
        stages: [
          new StageDefinition(id: 'injected', type: 'manualJudgment', inject: injectRule)
        ]
      )
    }

    PipelineTemplate template

    when: 'injecting stage first in dag'
    template = templateBuilder()
    new ConfigStageInjectionTransform(configBuilder(new InjectionRule(first: true))).visitPipelineTemplate(template)

    then:
    // injected <- s1 <- s2 <- s4
    //           \- s3 <- s5
    requisiteStageIds('s1', template.stages) == ['injected'] as Set

    when: 'injecting stage last in dag'
    template = templateBuilder()
    new ConfigStageInjectionTransform(configBuilder(new InjectionRule(last: true))).visitPipelineTemplate(template)

    then:
    // s1 <- s2 <- s4  <- injected
    //     \- s3 <- s5 -/
    requisiteStageIds('injected', template.stages) == ['s4', 's5'] as Set

    when: 'injecting stage before another stage in dag'
    template = templateBuilder()
    new ConfigStageInjectionTransform(configBuilder(new InjectionRule(before: ['s2']))).visitPipelineTemplate(template)

    then:
    // s1 <- injected <- s2 <- s4
    //     \- s3 <- s5
    requisiteStageIds('s1', template.stages) == [] as Set
    requisiteStageIds('s2', template.stages) == ['injected'] as Set
    requisiteStageIds('s3', template.stages) == ['s1'] as Set
    requisiteStageIds('injected', template.stages) == ['s1'] as Set

    when: 'injecting stage before a list of stages in dag'
    template = templateBuilder()
    new ConfigStageInjectionTransform(configBuilder(new InjectionRule(before: ['s2', 's3']))).visitPipelineTemplate(template)

    then:
    // s1 <- injected <- s2 <- s4
    //          \- s3 <- s5
    requisiteStageIds('s1', template.stages) == [] as Set
    requisiteStageIds('s2', template.stages) == ['injected'] as Set
    requisiteStageIds('s3', template.stages) == ['injected'] as Set
    requisiteStageIds('injected', template.stages) == ['s1'] as Set

    when: 'injecting new stages that create a cycle (injected, s1, s2)'
    // injected <- s1 <- s3 <- s5
    //     \- s2 -/
    //         \- s4
    template = templateBuilder()
    new ConfigStageInjectionTransform(configBuilder(
      new InjectionRule(before: ['s1', 's2'])
    )).visitPipelineTemplate(template)

    then:
    thrown(IllegalStateException)

    when: 'injecting stage after another stage in dag'
    template = templateBuilder()
    new ConfigStageInjectionTransform(configBuilder(new InjectionRule(after: ['s2']))).visitPipelineTemplate(template)

    then:
    // s1 <- s2 <- injected <- s4
    //     \- s3 <- s5
    requisiteStageIds('s2', template.stages) == ['s1'] as Set
    requisiteStageIds('s4', template.stages) == ['injected'] as Set
    requisiteStageIds('injected', template.stages) == ['s2'] as Set

    when: 'injecting stage after a list of stages in dag'
    template = templateBuilder()
    new ConfigStageInjectionTransform(configBuilder(new InjectionRule(after: ['s2', 's3']))).visitPipelineTemplate(template)

    then:
    // s1 <- s2 <- injected <- s4
    //     \- s3 -/
    //         \- s5
    requisiteStageIds('s2', template.stages) == ['s1'] as Set
    requisiteStageIds('s3', template.stages) == ['s1'] as Set
    requisiteStageIds('s4', template.stages) == ['injected'] as Set
    requisiteStageIds('injected', template.stages) == ['s2', 's3'] as Set
  }

  def 'should de-dupe template-injected stages'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: [
        new StageDefinition(id: 's2', type: 'deploy'),
        new StageDefinition(id: 's1', inject: [first: true], type: 'findImageFromTags'),
      ]
    )

    when:
    new ConfigStageInjectionTransform(new TemplateConfiguration()).visitPipelineTemplate(template)

    then:
    template.stages*.id == ['s1', 's2']
  }

  def 'should expand stage partials'() {
    given:
    PipelineTemplate template = new PipelineTemplate(
      stages: [
        new StageDefinition(
          id: 's1',
          type: 'partial.foo',
          config: [:],
        ),
        new StageDefinition(
          id: 's2',
          type: 'wait',
          dependsOn: ['s1'],
          config: [:]
        )
      ],
      partials: [
        new PartialDefinition(
          renderedPartials: [
            's1': [
              new StageDefinition(
                id: 's1.p1',
                type: 'wait',
                config: [:]
              ),
              new StageDefinition(
                id: 's1.p2',
                type: 'wait',
                dependsOn: ['s1.p1'],
                config: [:]
              )
            ]
          ]
        )
      ]
    )

    when:
    new ConfigStageInjectionTransform(new TemplateConfiguration()).visitPipelineTemplate(template)

    then:
    noExceptionThrown()
    template.stages.size() == 3
    template.stages*.id == ['s1.p1', 's1.p2', 's2']
    template.stages[2].dependsOn == ['s1.p2'] as Set
  }

  static StageDefinition getStageById(String id, List<StageDefinition> allStages) {
    return allStages.find { it.id == id }
  }

  static Set<String> requisiteStageIds(String stageId, List<StageDefinition> allStages) {
    getStageById(stageId, allStages).requisiteStageRefIds
  }
}
