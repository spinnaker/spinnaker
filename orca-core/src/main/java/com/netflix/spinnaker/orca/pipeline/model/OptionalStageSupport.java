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

package com.netflix.spinnaker.orca.pipeline.model;

import static java.lang.String.format;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonMap;

import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class OptionalStageSupport {

  private static final Logger log = LoggerFactory.getLogger(OptionalStageSupport.class);

  private static Map<String, Class<? extends OptionalStageEvaluator>> OPTIONAL_STAGE_TYPES =
      singletonMap("expression", ExpressionOptionalStageEvaluator.class);

  /**
   * A Stage is optional if it has an {@link OptionalStageEvaluator} in its context that evaluates
   * {@code false}.
   */
  public static boolean isOptional(
      Stage stage, ContextParameterProcessor contextParameterProcessor) {
    Map stageEnabled = (Map) stage.getContext().get("stageEnabled");
    String type = stageEnabled == null ? null : (String) stageEnabled.get("type");
    String optionalType = type == null ? null : type.toLowerCase();
    if (!OPTIONAL_STAGE_TYPES.containsKey(optionalType)) {
      if (stage.getSyntheticStageOwner() != null || stage.getParentStageId() != null) {
        Stage parentStage =
            stage.getExecution().getStages().stream()
                .filter(it -> it.getId().equals(stage.getParentStageId()))
                .findFirst()
                .orElseThrow(
                    () ->
                        new IllegalStateException(
                            format("stage %s not found", stage.getParentStageId())));
        return isOptional(parentStage, contextParameterProcessor);
      }
      return false;
    }

    try {
      return !stage
          .mapTo("/stageEnabled", OPTIONAL_STAGE_TYPES.get(optionalType))
          .evaluate(stage, contextParameterProcessor);
    } catch (InvalidExpression e) {
      log.warn(
          "Unable to determine stage optionality, reason: {} (executionId: {}, stageId: {})",
          e.getMessage(),
          stage.getExecution().getId(),
          stage.getId());
      return false;
    }
  }

  /** Determines whether a stage is optional and should be skipped */
  private interface OptionalStageEvaluator {
    boolean evaluate(Stage stage, ContextParameterProcessor contextParameterProcessor);
  }

  /**
   * An {@link OptionalStageEvaluator} that will evaluate an expression against the current
   * execution.
   */
  private static class ExpressionOptionalStageEvaluator implements OptionalStageEvaluator {

    private String expression;

    public String getExpression() {
      return expression;
    }

    public void setExpression(String expression) {
      this.expression = expression;
    }

    @Override
    public boolean evaluate(Stage stage, ContextParameterProcessor contextParameterProcessor) {
      String expression =
          contextParameterProcessor
              .process(
                  singletonMap("expression", format("${%s}", this.expression)),
                  contextParameterProcessor.buildExecutionContext(stage, true),
                  true)
              .get("expression")
              .toString();

      Matcher matcher = Pattern.compile("\\$\\{(. *)}").matcher(expression);
      if (matcher.matches()) {
        expression = matcher.group(1);
      }

      if (!asList("true", "false").contains(expression.toLowerCase())) {
        // expression failed to evaluate successfully
        throw new InvalidExpression(
            format("Expression '%s' could not be evaluated", this.expression));
      }

      return Boolean.valueOf(expression);
    }
  }

  static class InvalidExpression extends RuntimeException {
    InvalidExpression(String message) {
      super(message);
    }
  }
}
