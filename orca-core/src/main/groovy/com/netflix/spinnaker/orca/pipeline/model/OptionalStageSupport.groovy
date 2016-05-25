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

class OptionalStageSupport {
  public static Map<String, Class<? extends OptionalStageEvaluator>> OPTIONAL_STAGE_TYPES = [
    "expression": ExpressionOptionalStageEvaluator
  ]

  /**
   * A Stage is optional if it has an {@link OptionalStageEvaluator} in it's context that evaluates {@code true}.
   */
  static boolean isOptional(Stage stage) {
    def optionalType = (stage.context.stageEnabled?.type as String)?.toLowerCase()
    if (!optionalType || !OPTIONAL_STAGE_TYPES[optionalType]) {
      return false
    }

    return stage.mapTo("/stageEnabled", OPTIONAL_STAGE_TYPES[optionalType]).isOptional(stage)
  }

  /**
   * Determines whether a stage is optional and should be skipped
   */
  private static interface OptionalStageEvaluator {
    boolean isOptional(Stage stage)
  }

  /**
   * An {@link OptionalStageEvaluator} that will evaluate an expression against the current execution.
   */
  private static class ExpressionOptionalStageEvaluator implements OptionalStageEvaluator {
    String expression

    @Override
    boolean isOptional(Stage stage) {
      String expression = ContextParameterProcessor.process([
        "expression": '${' + expression + '}'
      ], ContextParameterProcessor.buildExecutionContext(stage, true), true).expression

      def matcher = expression =~ /\$\{(.*)\}/
      if (matcher.matches()) {
        expression = matcher.group(1)
      }

      return Boolean.valueOf(expression)
    }
  }
}
