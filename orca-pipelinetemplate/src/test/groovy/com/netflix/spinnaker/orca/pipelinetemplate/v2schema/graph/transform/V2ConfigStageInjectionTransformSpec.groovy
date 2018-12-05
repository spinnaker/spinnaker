/*
 * Copyright 2018 Google, Inc.
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

package com.netflix.spinnaker.orca.pipelinetemplate.v2schema.graph.transform

import com.netflix.spinnaker.orca.pipelinetemplate.exceptions.IllegalTemplateConfigurationException
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.graph.v2.transform.V2ConfigStageInjectionTransform
import com.netflix.spinnaker.orca.pipelinetemplate.v1schema.model.StageDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2PipelineTemplate
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2StageDefinition
import com.netflix.spinnaker.orca.pipelinetemplate.v2schema.model.V2TemplateConfiguration
import spock.lang.Specification
import spock.lang.Unroll

class V2ConfigStageInjectionTransformSpec extends Specification {

  def 'should replace stages from configuration into template'() {
    given:
    V2PipelineTemplate template = new V2PipelineTemplate(
      pipeline: [
        stages: [
          new V2StageDefinition(refId: 's1', type: 'findImageFromTags'),
          new V2StageDefinition(refId: 's2', type: 'deploy')
        ]
      ]
    )

    V2TemplateConfiguration configuration = new V2TemplateConfiguration(
      stages: [
        new V2StageDefinition(refId: 's1', type: 'findImageFromCluster'),
      ]
    )

    when:
    new V2ConfigStageInjectionTransform(configuration).visitPipelineTemplate(template)

    then:
    template.stages*.refId == ['s1', 's2']
    template.stages.find { it.refId == 's1' }.type == 'findImageFromCluster'
  }

  @Unroll('#subject should have #requisiteStages as requisites')
  def 'should create dag from requisiteStageRefIds'() {
    given:
    def stages = [
      new V2StageDefinition(refId: 's1', type: 'findImageFromTags'),
      new V2StageDefinition(refId: 's2', type: 'deploy', requisiteStageRefIds: ['s1']),
      new V2StageDefinition(refId: 's3', type: 'jenkins', requisiteStageRefIds: ['s1']),
      new V2StageDefinition(refId: 's4', type: 'wait', requisiteStageRefIds: ['s2']),
      new V2StageDefinition(refId: 's5', type: 'wait', requisiteStageRefIds: ['s3'])
    ]

    when:
    def result = V2ConfigStageInjectionTransform.createGraph(stages)

    then:
    requisiteStageRefIds(subject, result) == requisiteStages as Set

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
      new V2StageDefinition(refId: 's2', type: 'deploy', requisiteStageRefIds: ['s1']),
      new V2StageDefinition(refId: 's1', type: 'wait', requisiteStageRefIds: ['s2']),
      new V2StageDefinition(refId: 's3', type: 'findImageFromTags'),
      new V2StageDefinition(refId: 's5', type: 'wait', requisiteStageRefIds: ['s3'])
    ]

    when:
    V2ConfigStageInjectionTransform.createGraph(stages)

    then:
    thrown(IllegalStateException)
  }

  def 'should inject stage into dag'() {
    given:
    // s1 <- s2 <- s4
    //     \- s3 <- s5
    def templateBuilder = {
      new V2PipelineTemplate(
        pipeline: [
          stages: [
            new V2StageDefinition(refId: 's1', type: 'findImageFromTags'),
            new V2StageDefinition(refId: 's2', type: 'deploy', requisiteStageRefIds: ['s1']),
            new V2StageDefinition(refId: 's3', type: 'jenkins', requisiteStageRefIds: ['s1']),
            new V2StageDefinition(refId: 's4', type: 'wait', requisiteStageRefIds: ['s2']),
            new V2StageDefinition(refId: 's5', type: 'wait', requisiteStageRefIds: ['s3'])
          ]
        ]
      )
    }

    def configBuilder = { injectRule ->
      new V2TemplateConfiguration(
        stages: [
          new V2StageDefinition(refId: 'injected', type: 'manualJudgment', inject: injectRule)
        ]
      )
    }

    V2PipelineTemplate template

    when: 'injecting stage first in dag'
    template = templateBuilder()
    new V2ConfigStageInjectionTransform(configBuilder(new StageDefinition.InjectionRule(first: true))).visitPipelineTemplate(template)

    then:
    // injected <- s1 <- s2 <- s4
    //           \- s3 <- s5
    requisiteStageRefIds('s1', template.stages) == ['injected'] as Set

    when: 'injecting stage last in dag'
    template = templateBuilder()
    new V2ConfigStageInjectionTransform(configBuilder(new StageDefinition.InjectionRule(last: true))).visitPipelineTemplate(template)

    then:
    // s1 <- s2 <- s4  <- injected
    //     \- s3 <- s5 -/
    requisiteStageRefIds('injected', template.stages) == ['s4', 's5'] as Set

    when: 'injecting stage before another stage in dag'
    template = templateBuilder()
    new V2ConfigStageInjectionTransform(configBuilder(new StageDefinition.InjectionRule(before: ['s2']))).visitPipelineTemplate(template)

    then:
    // s1 <- injected <- s2 <- s4
    //     \- s3 <- s5
    requisiteStageRefIds('s1', template.stages) == [] as Set
    requisiteStageRefIds('s2', template.stages) == ['injected'] as Set
    requisiteStageRefIds('s3', template.stages) == ['s1'] as Set
    requisiteStageRefIds('injected', template.stages) == ['s1'] as Set

    when: 'injecting stage before a list of stages in dag'
    template = templateBuilder()
    new V2ConfigStageInjectionTransform(configBuilder(new StageDefinition.InjectionRule(before: ['s2', 's3']))).visitPipelineTemplate(template)

    then:
    // s1 <- injected <- s2 <- s4
    //          \- s3 <- s5
    requisiteStageRefIds('s1', template.stages) == [] as Set
    requisiteStageRefIds('s2', template.stages) == ['injected'] as Set
    requisiteStageRefIds('s3', template.stages) == ['injected'] as Set
    requisiteStageRefIds('injected', template.stages) == ['s1'] as Set

    when: 'injecting new stages that create a cycle (injected, s1, s2)'
    // injected <- s1 <- s3 <- s5
    //     \- s2 -/
    //         \- s4
    template = templateBuilder()
    new V2ConfigStageInjectionTransform(configBuilder(
      new StageDefinition.InjectionRule(before: ['s1', 's2'])
    )).visitPipelineTemplate(template)

    then:
    thrown(IllegalStateException)

    when: 'injecting stage after another stage in dag'
    template = templateBuilder()
    new V2ConfigStageInjectionTransform(configBuilder(new StageDefinition.InjectionRule(after: ['s2']))).visitPipelineTemplate(template)

    then:
    // s1 <- s2 <- injected <- s4
    //     \- s3 <- s5
    requisiteStageRefIds('s2', template.stages) == ['s1'] as Set
    requisiteStageRefIds('s4', template.stages) == ['injected'] as Set
    requisiteStageRefIds('injected', template.stages) == ['s2'] as Set

    when: 'injecting stage after a list of stages in dag'
    template = templateBuilder()
    new V2ConfigStageInjectionTransform(configBuilder(new StageDefinition.InjectionRule(after: ['s2', 's3']))).visitPipelineTemplate(template)

    then:
    // s1 <- s2 <- injected <- s4
    //     \- s3 -/
    //         \- s5
    requisiteStageRefIds('s2', template.stages) == ['s1'] as Set
    requisiteStageRefIds('s3', template.stages) == ['s1'] as Set
    requisiteStageRefIds('s4', template.stages) == ['injected'] as Set
    requisiteStageRefIds('injected', template.stages) == ['s2', 's3'] as Set
  }

  def 'should de-dupe template-injected stages'() {
    given:
    V2PipelineTemplate template = new V2PipelineTemplate(
      pipeline: [
        stages: [
          new V2StageDefinition(refId: 's2', type: 'deploy'),
          new V2StageDefinition(refId: 's1', inject: [first: true], type: 'findImageFromTags'),
        ]
      ]
    )

    when:
    new V2ConfigStageInjectionTransform(new V2TemplateConfiguration()).visitPipelineTemplate(template)

    then:
    template.stages*.refId == ['s1', 's2']
  }

  def 'should ignore empty inject object'() {
    given:
    V2PipelineTemplate template = new V2PipelineTemplate(
      pipeline: [
        stages: [
          new V2StageDefinition(refId: 's2', type: 'deploy'),
          new V2StageDefinition(refId: 's1', inject: [], type: 'findImageFromTags'),
        ]
      ]
    )

    when:
    new V2ConfigStageInjectionTransform(new V2TemplateConfiguration()).visitPipelineTemplate(template)

    then:
    notThrown(IllegalTemplateConfigurationException)
    template.stages*.refId == ['s1', 's2']
  }

  static V2StageDefinition getStageByRefId(String refId, List<V2StageDefinition> allStages) {
    return allStages.find { it.refId == refId }
  }

  static Set<String> requisiteStageRefIds(String stageRefId, List<V2StageDefinition> allStages) {
    getStageByRefId(stageRefId, allStages).requisiteStageRefIds
  }
}
