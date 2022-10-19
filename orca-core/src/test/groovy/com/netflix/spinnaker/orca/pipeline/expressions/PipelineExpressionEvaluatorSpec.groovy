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

package com.netflix.spinnaker.orca.pipeline.expressions

import com.netflix.spinnaker.kork.expressions.ExpressionEvaluationSummary
import com.netflix.spinnaker.kork.api.expressions.ExpressionFunctionProvider
import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution
import org.pf4j.PluginManager
import spock.lang.Specification

class PipelineExpressionEvaluatorSpec extends Specification {

  PluginManager pluginManager = Mock() {
    getExtensions(_) >> []
  }

  ExpressionProperties expressionProperties = Mock() {
    ExpressionProperties.FeatureFlag featureFlag = new ExpressionProperties.FeatureFlag()
    featureFlag.setEnabled(true) // arbitrary, the tests here don't care
    getDoNotEvalSpel() >> featureFlag
  }

  def 'should set execution aware functions for the given function providers'() {

    given: 'function providers'
    ExpressionFunctionProvider expressionFunctionProvider1 = buildExpressionFunctionProvider('pipeline')
    ExpressionFunctionProvider expressionFunctionProvider2 = buildExpressionFunctionProvider('jenkins')

    when: 'registered with pipeline evaluator'
    PipelineExpressionEvaluator evaluator = new PipelineExpressionEvaluator(
      [expressionFunctionProvider1, expressionFunctionProvider2],
      pluginManager, expressionProperties
    )

    then:
    noExceptionThrown()
    evaluator.getExecutionAwareFunctions().size() == 2 // only 1 function is execution aware.
    evaluator.getExecutionAwareFunctions().findAll { it.contains('functionWithExecutionContext') }.size() == 2
  }

  def "should allow comparing ExecutionStatus to string"() {
    given:
    def source = [test: testCase]
    PipelineExpressionEvaluator evaluator = new PipelineExpressionEvaluator([], pluginManager, expressionProperties)

    when:
    ExpressionEvaluationSummary evaluationSummary = new ExpressionEvaluationSummary()
    def result = evaluator.evaluate(source, [status: ExecutionStatus.TERMINAL], evaluationSummary, true)

    then:
    result.test == evalResult

    where:
    testCase                                    | evalResult
    '${status.toString() == "TERMINAL"}'        | true
    '${status == "TERMINAL"}'                   | true
    '${status.toString() == "SUCCEEDED"}'       | false
    '${status == "SUCCEEDED"}'                  | false
  }

  static ExpressionFunctionProvider buildExpressionFunctionProvider(String providerName) {
    new ExpressionFunctionProvider() {
      @Override
      String getNamespace() {
        return null
      }

      @Override
      ExpressionFunctionProvider.Functions getFunctions() {
        return new ExpressionFunctionProvider.Functions(
          new ExpressionFunctionProvider.FunctionDefinition(
            "functionWithExecutionContext-${providerName}",
            "description for: functionWithExecutionContext-${providerName}",
            new ExpressionFunctionProvider.FunctionParameter(
              PipelineExecution.class,
              "execution",
              "The execution containing the currently executing stage"),
            new ExpressionFunctionProvider.FunctionParameter(
              String.class, "someArg", "A valid stage reference identifier")),
          new ExpressionFunctionProvider.FunctionDefinition(
            "functionWithNoExecutionContext-${providerName}",
            "description for: functionWithNoExecutionContext-${providerName}",
            new ExpressionFunctionProvider.FunctionParameter(
              String.class, "someArg", "A valid stage reference identifier"))
        )
      }
    }
  }

}
