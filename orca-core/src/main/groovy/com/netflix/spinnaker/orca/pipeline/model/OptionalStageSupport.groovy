/*
 * Copyright 2016 Netflix, Inc.
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


package com.netflix.spinnaker.orca.pipeline.model

import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j

@Slf4j
class OptionalStageSupport {
  public static Map<String, Class<? extends OptionalStageEvaluator>> OPTIONAL_STAGE_TYPES = [
    "expression": ExpressionOptionalStageEvaluator
  ]

  /**
   * A Stage is optional if it has an {@link OptionalStageEvaluator} in its context that evaluates {@code false}.
   */
  static boolean isOptional(Stage stage, ContextParameterProcessor contextParameterProcessor) {
    def optionalType = (stage.context.stageEnabled?.type as String)?.toLowerCase()
    if (!optionalType || !OPTIONAL_STAGE_TYPES[optionalType]) {
      if (stage.syntheticStageOwner || stage.parentStageId) {
        def parentStage = stage.execution.stages.find { it.id == stage.parentStageId }
        return isOptional(parentStage, contextParameterProcessor)
      }

      return false
    }

    try {
      return !stage.mapTo("/stageEnabled", OPTIONAL_STAGE_TYPES[optionalType]).evaluate(stage, contextParameterProcessor)
    } catch (InvalidExpression e) {
      log.warn("Unable to determine stage optionality, reason: ${e.message} (executionId: ${stage.execution.id}, stageId: ${stage.id})")
      return false
    }
  }

  /**
   * Determines whether a stage is optional and should be skipped
   */
  private static interface OptionalStageEvaluator {
    boolean evaluate(Stage stage, ContextParameterProcessor contextParameterProcessor)
  }

  /**
   * An {@link OptionalStageEvaluator} that will evaluate an expression against the current execution.
   */
  private static class ExpressionOptionalStageEvaluator implements OptionalStageEvaluator {
    String expression

    @Override
    boolean evaluate(Stage stage, ContextParameterProcessor contextParameterProcessor) {
      String expression = contextParameterProcessor.process([
        "expression": '${' + expression + '}'
      ], contextParameterProcessor.buildExecutionContext(stage, true), true).expression

      def matcher = expression =~ /\$\{(.*)\}/
      if (matcher.matches()) {
        expression = matcher.group(1)
      }

      if (!["true", "false"].contains(expression.toLowerCase())) {
        // expression failed to evaluate successfully
        throw new InvalidExpression("Expression '${this.expression}' could not be evaluated")
      }

      return Boolean.valueOf(expression)
    }
  }

  @InheritConstructors
  static class InvalidExpression extends RuntimeException {
    // do nothing
  }
}
