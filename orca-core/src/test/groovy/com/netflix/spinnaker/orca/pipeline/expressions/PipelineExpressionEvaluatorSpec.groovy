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

import com.netflix.spinnaker.kork.expressions.ExpressionFunctionProvider
import com.netflix.spinnaker.orca.pipeline.model.Execution
import spock.lang.Specification

class PipelineExpressionEvaluatorSpec extends Specification {

  def 'should set execution aware functions for the given function providers'() {

    given: 'function providers'
    ExpressionFunctionProvider expressionFunctionProvider1 = buildExpressionFunctionProvider('pipeline')
    ExpressionFunctionProvider expressionFunctionProvider2 = buildExpressionFunctionProvider('jenkins')

    when: 'registered with pipeline evaluator'
    PipelineExpressionEvaluator evaluator = new PipelineExpressionEvaluator([expressionFunctionProvider1, expressionFunctionProvider2])

    then:
    noExceptionThrown()
    evaluator.getExecutionAwareFunctions().size() == 2 // only 1 function is execution aware.
    evaluator.getExecutionAwareFunctions().findAll { it.contains('functionWithExecutionContext') }.size() == 2
  }

  private ExpressionFunctionProvider buildExpressionFunctionProvider(String providerName) {
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
            new ExpressionFunctionProvider.FunctionParameter(
              Execution.class,
              "execution",
              "The execution containing the currently executing stage"),
            new ExpressionFunctionProvider.FunctionParameter(
              String.class, "someArg", "A valid stage reference identifier")),
          new ExpressionFunctionProvider.FunctionDefinition(
            "functionWithNoExecutionContext-${providerName}",
            new ExpressionFunctionProvider.FunctionParameter(
              String.class, "someArg", "A valid stage reference identifier"))
        )
      }
    }
  }

}
