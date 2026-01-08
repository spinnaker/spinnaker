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

package com.netflix.spinnaker.orca.pipeline.tasks

import com.fasterxml.jackson.databind.ObjectMapper
import com.netflix.spinnaker.kork.expressions.ExpressionEvaluationSummary
import com.netflix.spinnaker.orca.pipeline.EvaluateVariablesStage
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import spock.lang.Specification
import spock.lang.Subject

import static com.netflix.spinnaker.orca.test.model.ExecutionBuilder.stage

class EvaluateVariablesStageSpec extends Specification {
  ContextParameterProcessor contextParameterProcessor = new ContextParameterProcessor()

  @Subject
  evaluateVariablesStage = new EvaluateVariablesStage(new ObjectMapper())

  void "Should sequentially eval variables"() {
    setup:
    def summary = new ExpressionEvaluationSummary()
    def correctVars = [
      [key: "a", value: 10, sourceValue: "{1+2+3+4}", description: null],
      [key: "b", value: 24, sourceValue: "{1*2*3*4}", description: null],
      [key: "product", value: 240, sourceValue: "{a * b}", description: "product of a(10) and b(24)"],
      [key: "nonworking", value: 'this one should fail: ${a * c}', sourceValue: 'this one should fail: {a * c}', description: null]
    ]

    def stage = stage {
      refId = "1"
      type = "evaluateVariables"
      context["variables"] = [
        [key: "a", value: '${1+2+3+4}'],
        [key: "b", value: '${1*2*3*4}'],
        [key: "product", value: '${a * b}', description: 'product of a(${a}) and b(${b})'],
        [key: "nonworking", value: 'this one should fail: ${a * c}']
      ]
    }

    when:
    def shouldContinue = evaluateVariablesStage.processExpressions(stage, contextParameterProcessor, summary)

    then:
    shouldContinue == false
    stage.context.variables == correctVars
    summary.totalEvaluated == 5
    summary.failureCount == 1
  }

  void "Should eval non-variable part of context"() {
    setup:
    def summary = new ExpressionEvaluationSummary()

    def stage = stage {
      refId = "1"
      type = "evaluateVariables"
      context["notifications"] = [
          [address: '${"someone" + "@somewhere.com"}']
          ]
    }

    when:
    def shouldContinue = evaluateVariablesStage.processExpressions(stage, contextParameterProcessor, summary)

    then:
    shouldContinue == false
    stage.context.notifications[0].address == "someone@somewhere.com"
  }

  void "Should correctly clean variables in restart scenario"() {
    setup:
    def summary = new ExpressionEvaluationSummary()

    def stage = stage {
      refId = "1"
      type = "evaluateVariables"
      context["variables"] = [
          ["key": "status", "value": "expressionToEvaluate"],
      ]
    }

    when:
    evaluateVariablesStage.processExpressions(stage, contextParameterProcessor, summary)
    evaluateVariablesStage.prepareStageForRestart(stage)
    def variables = stage.mapTo(EvaluateVariablesStage.EvaluateVariablesStageContext.class).getVariables()
    def variablesCleaned = false
    variables.each {
      variablesCleaned = it.sourceExpression == null
    }

    then:
    variablesCleaned == true
  }

  void "Should eval variable correctly when evaluating default entries failed"() {
    setup:
    def summary = new ExpressionEvaluationSummary()

    def correctVars = [
      [key: "a", value: 10, sourceValue: "{1+2+3+4}", description: null],
      [key: "b", value: 24, sourceValue: "{1*2*3*4}", description: null],
      [key: "product", value: 240, sourceValue: "{a * b}", description: "product of a(10) and b(24)"]
    ]

    def stage = stage {
      refId = "1"
      type = "evaluateVariables"
      context["notifications"] = [
        [address: '${someFailingVar}']
      ]
      context["variables"] = [
        [key: "a", value: '${1+2+3+4}'],
        [key: "b", value: '${1*2*3*4}'],
        [key: "product", value: '${a * b}', description: 'product of a(${a}) and b(${b})']
      ]
    }

    when:
    def shouldContinue = evaluateVariablesStage.processExpressions(stage, contextParameterProcessor, summary)

    then:
    shouldContinue == false

    // If the processDefaultEntries failure isn't accounted for properly, the
    // first variable can be treated as failed to evaluate, even when it's
    // correct.  Explicitly assert that the first variable is correct so it's
    // easier to see when something isn't working than with assert on all
    // variables.
    stage.context.variables[0].value == correctVars[0].value
    stage.context.variables == correctVars
    summary.totalEvaluated == 5
    summary.failureCount == 1
    summary.expressionResult.size() == 1
    summary.expressionResult.keySet().contains("someFailingVar")
  }

  void "Should eval variable correctly when evaluating variables and default entries both failed"() {
    setup:
    def summary = new ExpressionEvaluationSummary()

    def correctVars = [
      [key: "a", value: 10, sourceValue: "{1+2+3+4}", description: null],
      [key: "b", value: 24, sourceValue: "{1*2*3*4}", description: null],
      [key: "product", value: 240, sourceValue: "{a * b}", description: "product of a(10) and b(24)"],
      [key: "nonworking", value: 'this one should fail: ${a * c}', sourceValue: 'this one should fail: {a * c}', description: null]
    ]

    def stage = stage {
      refId = "1"
      type = "evaluateVariables"
      context["notifications"] = [
        [address: '${someFailingVar}']
      ]
      context["variables"] = [
        [key: "a", value: '${1+2+3+4}'],
        [key: "b", value: '${1*2*3*4}'],
        [key: "product", value: '${a * b}', description: 'product of a(${a}) and b(${b})'],
        [key: "nonworking", value: 'this one should fail: ${a * c}']
      ]
    }

    when:
    def shouldContinue = evaluateVariablesStage.processExpressions(stage, contextParameterProcessor, summary)

    then:
    shouldContinue == false
    stage.context.variables == correctVars
    summary.totalEvaluated == 6
    summary.failureCount == 2
    summary.expressionResult.size() == 2
    summary.expressionResult.keySet().contains("this one should fail: a * c")
    summary.expressionResult.keySet().contains("someFailingVar")
  }
}
