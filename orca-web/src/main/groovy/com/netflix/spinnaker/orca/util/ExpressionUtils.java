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

package com.netflix.spinnaker.orca.util;

import com.google.common.base.Strings;
import com.netflix.spinnaker.kork.exceptions.UserException;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.pipeline.EvaluateVariablesStage;
import com.netflix.spinnaker.orca.pipeline.StageDefinitionBuilderFactory;
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator;
import com.netflix.spinnaker.orca.pipeline.model.StageExecutionImpl;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import com.netflix.spinnaker.orca.q.handler.ExpressionAware;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class ExpressionUtils implements ExpressionAware {
  private ContextParameterProcessor contextParameterProcessor;
  private StageDefinitionBuilderFactory stageDefinitionBuilderFactory;

  @Autowired
  public ExpressionUtils(
      ContextParameterProcessor contextParameterProcessor,
      StageDefinitionBuilderFactory stageDefinitionBuilderFactory) {
    this.contextParameterProcessor = contextParameterProcessor;
    this.stageDefinitionBuilderFactory = stageDefinitionBuilderFactory;
  }

  public Map<String, Object> evaluateVariables(
      @Nonnull PipelineExecution execution,
      @Nonnull List<String> requisiteStageRefIds,
      @Nullable String spelVersionOverride,
      @Nonnull List<Map<String, String>> expressions) {
    // Create a stage that is downstream from the specified one, because we want to make sure that
    // 'outputs'
    // form the specified stage are included in the evaluation
    Map<String, Object> stageContext = new HashMap<>();
    stageContext.put("refId", "_AD_HOC_EVALUATE_VARIABLES_STAGE_");
    stageContext.put("requisiteStageRefIds", requisiteStageRefIds);
    stageContext.put("variables", expressions);

    if (!Strings.isNullOrEmpty(spelVersionOverride)) {
      if (!PipelineExpressionEvaluator.SpelEvaluatorVersion.isSupported(spelVersionOverride)) {
        throw new UserException(
            "SpEL evaluator version " + spelVersionOverride + " is not supported");
      }

      execution.setSpelEvaluator(spelVersionOverride);
    }

    StageExecution evalVarsStage =
        new StageExecutionImpl(execution, EvaluateVariablesStage.STAGE_TYPE, stageContext);

    evalVarsStage = ExpressionAware.DefaultImpls.withMergedContext(this, evalVarsStage);
    ExpressionAware.DefaultImpls.includeExpressionEvaluationSummary(this, evalVarsStage);

    Map<String, Object> result = new HashMap<>();
    result.put("result", evalVarsStage.getContext().get("variables"));
    result.put("detail", evalVarsStage.getContext().get(PipelineExpressionEvaluator.SUMMARY));

    return result;
  }

  @NotNull
  @Override
  public ContextParameterProcessor getContextParameterProcessor() {
    return contextParameterProcessor;
  }

  @NotNull
  @Override
  public StageDefinitionBuilderFactory getStageDefinitionBuilderFactory() {
    return stageDefinitionBuilderFactory;
  }

  @Override
  public boolean shouldFailOnFailedExpressionEvaluation(@NotNull StageExecution stage) {
    return false;
  }

  // NOTE: the following unfortunate method is needed because `withMergedContext` is a Kotlin
  // extension method
  // implemented on an interface. The interface provides all default implementations so technically
  // no overrides are needed
  // but we are not compiling with the JvmDefault option (which may bring about it's own set of
  // problems)
  // Hence, the following methods just defer to the "DefaultImpls"
  @Override
  public void includeExpressionEvaluationSummary(@NotNull StageExecution stage) {
    DefaultImpls.includeExpressionEvaluationSummary(this, stage);
  }

  @Override
  public boolean hasFailedExpressions(@NotNull StageExecution stage) {
    return DefaultImpls.hasFailedExpressions(this, stage);
  }

  @Override
  @Nonnull
  public StageExecution withMergedContext(@NotNull StageExecution stage) {
    return DefaultImpls.withMergedContext(this, stage);
  }

  @Override
  @Nonnull
  public Logger getLog() {
    return DefaultImpls.getLog(this);
  }
}
