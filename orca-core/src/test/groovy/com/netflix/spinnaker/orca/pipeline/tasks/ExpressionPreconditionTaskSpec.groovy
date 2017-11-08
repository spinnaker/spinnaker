/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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


package com.netflix.spinnaker.orca.pipeline.tasks

import com.netflix.spinnaker.orca.ExecutionStatus
import com.netflix.spinnaker.orca.pipeline.model.Execution
import com.netflix.spinnaker.orca.pipeline.model.Stage
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import spock.lang.Specification
import spock.lang.Unroll

class ExpressionPreconditionTaskSpec extends Specification {
  @Unroll
  def "should evaluate expression precondition against stage context at execution time"() {
    given:
    def task = new ExpressionPreconditionTask(new ContextParameterProcessor())
    def stage = new Stage(Execution.newPipeline("orca"), "Expression", [
      param1 : param1,
      context: [
        expression: expression
      ]
    ])
    stage.execution.trigger.put("triggerKey", "triggerVal")

    when:
    def taskResult = task.execute(stage)

    then:
    taskResult.status == taskResultStatus
    taskResult.context.context == [
      expression      : expression,
      expressionResult: expressionResult
    ]

    where:
    expression                           | param1  || expressionResult         || taskResultStatus
    "param1 == 'true'"                   | 'true'  || 'true'                   || ExecutionStatus.SUCCEEDED
    "param1 == 'true'"                   | 'false' || 'false'                  || ExecutionStatus.TERMINAL
    "param1 == 'true'"                   | null    || 'false'                  || ExecutionStatus.TERMINAL
    "param1.xxx() == 'true'"             | null    || "param1.xxx() == 'true'" || ExecutionStatus.TERMINAL
    "trigger.triggerKey == 'triggerVal'" | null    || 'true'                   || ExecutionStatus.SUCCEEDED
  }
}
