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

import com.netflix.spinnaker.orca.ExecutionContext;
import com.netflix.spinnaker.orca.pipeline.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.orca.pipeline.expressions.SpelHelperFunctionException;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

import static java.lang.String.format;

@Component
public class StageExpressionFunctionProvider implements ExpressionFunctionProvider {
  @Nullable
  @Override
  public String getNamespace() {
    return null;
  }

  @NotNull
  @Override
  public Collection<FunctionDefinition> getFunctions() {
    return Arrays.asList(
        new FunctionDefinition("currentStage", Collections.singletonList(
            new FunctionParameter(
                Execution.class, "execution", "The execution containing the currently executing stage"
            )
        )),
        new FunctionDefinition("stageByRefId", Arrays.asList(
            new FunctionParameter(
                Execution.class, "execution", "The execution containing the currently executing stage"
            ),
            new FunctionParameter(
                String.class, "refId", "A valid stage reference identifier"
            )
        ))
    );
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
    return execution
        .getStages()
        .stream()
        .filter(s -> s.getId().equalsIgnoreCase(currentStageId))
        .findFirst()
        .orElseThrow(() -> new SpelHelperFunctionException("No stage found with id '" + currentStageId + "'"));
  }

  /**
   * Finds a Stage by refId. This function should only be used by programmatic pipeline generators, as refIds are
   * fragile and may change from execution-to-execution.
   *
   * @param execution the current execution
   * @param refId     the stage reference ID
   * @return a stage specified by refId
   */
  static Object stageByRefId(Execution execution, String refId) {
    if (refId == null) {
      throw new SpelHelperFunctionException(format(
          "Stage refId must not be null in #stageByRefId in execution %s", execution.getId()
      ));
    }
    return execution.getStages()
        .stream()
        .filter(s -> refId.equals(s.getRefId()))
        .findFirst()
        .orElseThrow(() -> new SpelHelperFunctionException(format(
            "Unable to locate [%1$s] using #stageByRefId(%1$s) in execution %2$s", refId, execution.getId()
        )));
  }
}
