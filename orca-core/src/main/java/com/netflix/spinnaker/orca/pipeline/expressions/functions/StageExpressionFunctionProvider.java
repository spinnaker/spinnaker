/*
 * Copyright 2019 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline.expressions.functions;

import static java.lang.String.format;

import com.netflix.spinnaker.kork.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.kork.expressions.SpelHelperFunctionException;
import com.netflix.spinnaker.orca.ExecutionContext;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import java.util.function.Predicate;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

@Component
public class StageExpressionFunctionProvider implements ExpressionFunctionProvider {
  @Nullable
  @Override
  public String getNamespace() {
    return null;
  }

  @NotNull
  @Override
  public Functions getFunctions() {
    FunctionParameter[] stageParameters = {
      new FunctionParameter(Execution.class, "execution", "The execution for the stage"),
      new FunctionParameter(String.class, "idOrName", "The name or id of the stage to find")
    };

    return new Functions(
        new FunctionDefinition(
            "currentStage",
            new FunctionParameter(
                Execution.class,
                "execution",
                "The execution containing the currently executing stage")),
        new FunctionDefinition(
            "stageByRefId",
            new FunctionParameter(
                Execution.class,
                "execution",
                "The execution containing the currently executing stage"),
            new FunctionParameter(String.class, "refId", "A valid stage reference identifier")),
        new FunctionDefinition("stage", stageParameters),
        new FunctionDefinition("stageExists", stageParameters),
        new FunctionDefinition("judgment", stageParameters),
        new FunctionDefinition("judgement", stageParameters));
  }

  /**
   * @param execution the current execution
   * @return the currently executing stage
   */
  public static Stage currentStage(Execution execution) {
    ExecutionContext executionContext = ExecutionContext.get();
    if (executionContext == null) {
      throw new SpelHelperFunctionException("An execution context is required for this function");
    }

    String currentStageId = ExecutionContext.get().getStageId();
    return execution.getStages().stream()
        .filter(s -> s.getId().equalsIgnoreCase(currentStageId))
        .findFirst()
        .orElseThrow(
            () ->
                new SpelHelperFunctionException("No stage found with id '" + currentStageId + "'"));
  }

  /**
   * Finds a Stage by refId. This function should only be used by programmatic pipeline generators,
   * as refIds are fragile and may change from execution-to-execution.
   *
   * @param execution the current execution
   * @param refId the stage reference ID
   * @return a stage specified by refId
   */
  static Object stageByRefId(Execution execution, String refId) {
    if (refId == null) {
      throw new SpelHelperFunctionException(
          format(
              "Stage refId must not be null in #stageByRefId in execution %s", execution.getId()));
    }
    return execution.getStages().stream()
        .filter(s -> refId.equals(s.getRefId()))
        .findFirst()
        .orElseThrow(
            () ->
                new SpelHelperFunctionException(
                    format(
                        "Unable to locate [%1$s] using #stageByRefId(%1$s) in execution %2$s",
                        refId, execution.getId())));
  }

  /**
   * Finds a Stage by id
   *
   * @param execution #root.execution
   * @param id the name or id of the stage to find
   * @return a stage specified by id
   */
  public static Object stage(Execution execution, String id) {
    return execution.getStages().stream()
        .filter(i -> id != null && (id.equals(i.getName()) || id.equals(i.getId())))
        .findFirst()
        .orElseThrow(
            () ->
                new SpelHelperFunctionException(
                    format(
                        "Unable to locate [%s] using #stage(%s) in execution %s",
                        id, id, execution.getId())));
  }

  /**
   * Checks existence of a Stage by id
   *
   * @param execution #root.execution
   * @param id the name or id of the stage to check existence
   * @return W
   */
  public static boolean stageExists(Execution execution, String id) {
    return execution.getStages().stream()
        .anyMatch(i -> id != null && (id.equals(i.getName()) || id.equals(i.getId())));
  }

  /**
   * Finds a stage by id and returns the judgment input text
   *
   * @param execution #root.execution
   * @param id the name of the stage to find
   * @return the judgment input text
   */
  public static String judgment(Execution execution, String id) {
    Stage stageWithJudgmentInput =
        execution.getStages().stream()
            .filter(isManualStageWithManualInput(id))
            .findFirst()
            .orElseThrow(
                () ->
                    new SpelHelperFunctionException(
                        format(
                            "Unable to locate manual Judgment stage [%s] using #judgment(%s) in execution %s. "
                                + "Stage doesn't exist or doesn't contain judgmentInput in its context ",
                            id, id, execution.getId())));

    return (String) stageWithJudgmentInput.getContext().get("judgmentInput");
  }

  /** Alias to judgment */
  public static String judgement(Execution execution, String id) {
    return judgment(execution, id);
  }

  private static Predicate<Stage> isManualStageWithManualInput(String id) {
    return i ->
        (id != null && id.equals(i.getName()))
            && (i.getContext() != null
                && i.getType().equals("manualJudgment")
                && i.getContext().get("judgmentInput") != null);
  }
}
