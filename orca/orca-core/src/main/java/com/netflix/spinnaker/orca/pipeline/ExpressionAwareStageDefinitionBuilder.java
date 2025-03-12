/*
 * Copyright 2020 Netflix, Inc.
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
package com.netflix.spinnaker.orca.pipeline;

import com.netflix.spinnaker.kork.expressions.ExpressionEvaluationSummary;
import com.netflix.spinnaker.orca.api.pipeline.graph.StageDefinitionBuilder;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.model.StageContext;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nonnull;

/**
 * Decorates a StageDefinitionBuilder with expression processing capabilities.
 *
 * <p>This is not available for the public Orca API.
 */
public abstract class ExpressionAwareStageDefinitionBuilder implements StageDefinitionBuilder {

  /**
   * Allows the stage to process SpEL expression in its own context in a custom way
   *
   * @return true to continue processing, false to stop generic processing of expressions
   */
  public abstract boolean processExpressions(
      @Nonnull StageExecution stage,
      @Nonnull ContextParameterProcessor contextParameterProcessor,
      @Nonnull ExpressionEvaluationSummary summary);

  /**
   * Processes all entries in the stage context that aren't handled in a special way by the stage
   * itself (e.g. things like comments, notifications, etc)
   *
   * <p>This function should be called by the implementation of processExpressions, ideally, in the
   * very beginning
   *
   * @param stage (should be same as passed into processExpressions)
   * @param contextParameterProcessor (should be same as passed into processExpressions)
   * @param summary (should be same as passed into processExpressions)
   * @param excludedKeys keys to exclude from processing
   */
  public final void processDefaultEntries(
      @Nonnull StageExecution stage,
      @Nonnull ContextParameterProcessor contextParameterProcessor,
      @Nonnull ExpressionEvaluationSummary summary,
      Collection<String> excludedKeys) {

    StageContext augmentedContext = contextParameterProcessor.buildExecutionContext(stage);
    Map<String, Object> stageContext = stage.getContext();

    for (String key : stageContext.keySet()) {
      if (!excludedKeys.contains(key)) {
        Map<String, Object> temp = new HashMap<>();
        temp.put(key, stageContext.get(key));
        stageContext.put(
            key, contextParameterProcessor.process(temp, augmentedContext, true, summary).get(key));
      }
    }
  }
}
