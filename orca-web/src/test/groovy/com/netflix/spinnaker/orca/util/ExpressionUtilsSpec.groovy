/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.orca.util

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.orca.pipeline.EvaluateVariablesStage
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilder
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import spock.lang.Specification

import javax.annotation.Nonnull

class ExpressionUtilsSpec extends Specification {
  private ExpressionUtils utils = new ExpressionUtils(
    new ContextParameterProcessor(),
    new StageDefinitionBuilderFactory() {
      @Override
      StageDefinitionBuilder builderFor(@Nonnull Stage stage) {
        return new EvaluateVariablesStage(new ObjectMapper())
      }
    });

  def 'should evaluate variables correctly with v4'() {
    Execution execution = createExecution()

    when:
    def result = utils.evaluateVariables(execution, ['1', '2'], 'v4',
      [
        [key: 'var1', value: '${varFromStage1}'],
        [key: 'var2', value: '${varFromStage2}'],
        [key: 'sum', value: '${var1 + var2}']
      ])

    then:
    result.size() == 2
    result.result == [
      [key: 'var1', value: 100, sourceValue: '{varFromStage1}'],
      [key: 'var2', value: 200, sourceValue: '{varFromStage2}'],
      [key: 'sum', value: 300, sourceValue: '{var1 + var2}']
    ]
  }

  def 'should correctly use refIds'() {
    Execution execution = createExecution()

    when:
    def result = utils.evaluateVariables(execution, ['1'], 'v4',
      [
        [key: 'var1', value: '${varFromStage1}'],
        [key: 'var2', value: '${varFromStage2}'],
        [key: 'sum', value: '${var1 + var2}']
      ])

    then:
    result.size() == 2
    result.result == [
      [key: 'var1', value: 100, sourceValue: '{varFromStage1}'],
      [key: 'var2', value: '${varFromStage2}', sourceValue: '{varFromStage2}'],
      [key: 'sum', value: '${var1 + var2}', sourceValue: '{var1 + var2}']
    ]
    result.detail.size() == 2
    result.detail.containsKey('varFromStage2')
    result.detail.containsKey('var1 + var2')
  }

  def 'should fail to evaluate variables that depend on prior variables in same stage when in v3'() {
    Execution execution = createExecution()

    when:
    def result = utils.evaluateVariables(execution, ['1', '2'], 'v3',
      [
        [key: 'var1', value: '${varFromStage1}'],
        [key: 'var2', value: '${varFromStage2}'],
        [key: 'sum', value: '${var1 + var2}']
      ])

    then:
    result.size() == 2
    result.result == [
      [key: 'var1', value: 100],
      [key: 'var2', value: 200],
      [key: 'sum', value: '${var1 + var2}']
    ]
    result.detail.size() == 1
    result.detail.containsKey('var1 + var2')
  }

  private static def createExecution() {
    def execution = new Execution(Execution.ExecutionType.PIPELINE, "test")
    def stage1 = new Stage(execution, "evaluateVariables")
    stage1.refId = "1"
    stage1.outputs = [varFromStage1: 100]
    execution.stages.add(stage1)

    def stage2 = new Stage(execution, "evaluateVariables")
    stage2.refId = "2"
    stage2.outputs = [varFromStage2: 200]
    execution.stages.add(stage2)

    return execution
  }
}
