/*
 * Copyright 2018 Netflix, Inc.
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

package com.netflix.spinnaker.orca.pipeline;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.netflix.spinnaker.kork.expressions.ExpressionEvaluationSummary;
import com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.model.StageContext;
import com.netflix.spinnaker.orca.pipeline.tasks.EvaluateVariablesTask;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import java.util.*;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class EvaluateVariablesStage implements StageDefinitionBuilder {
  public static String STAGE_TYPE = "evaluateVariables";

  private ObjectMapper mapper;

  @Autowired
  public EvaluateVariablesStage(ObjectMapper objectMapper) {
    mapper = objectMapper;
  }

  @Override
  public void taskGraph(@Nonnull Stage stage, @Nonnull TaskNode.Builder builder) {
    builder.withTask("evaluateVariables", EvaluateVariablesTask.class);
  }

  @Override
  public boolean processExpressions(
      @Nonnull Stage stage,
      @Nonnull ContextParameterProcessor contextParameterProcessor,
      @Nonnull ExpressionEvaluationSummary summary) {

    EvaluateVariablesStageContext context = stage.mapTo(EvaluateVariablesStageContext.class);
    StageContext augmentedContext = contextParameterProcessor.buildExecutionContext(stage);
    Map<String, Object> varSourceToEval = new HashMap<>();
    int lastFailedCount = 0;

    List<Variable> variables =
        Optional.ofNullable(context.getVariables()).orElse(Collections.emptyList());

    for (Variable var : variables) {
      if (var.getValue() instanceof String) {
        var.saveSourceExpression();
        varSourceToEval.put("var", var.getValue());

        Map<String, Object> evaluatedVar =
            contextParameterProcessor.process(varSourceToEval, augmentedContext, true, summary);

        // Since we process one variable at a time, the way we know if the current variable was
        // evaluated properly is by
        // checking if the total number of failures has changed since last evaluation. We can make
        // this nicer, but that
        // will involve a decent refactor of ExpressionEvaluationSummary
        boolean evaluationSucceeded = summary.getFailureCount() == lastFailedCount;
        if (evaluationSucceeded) {
          var.setValue(evaluatedVar.get("var"));
          augmentedContext.put(var.key, var.value);
        } else {
          lastFailedCount = summary.getFailureCount();
        }
      }
    }

    Map<String, Object> evaluatedContext =
        mapper.convertValue(context, new TypeReference<Map<String, Object>>() {});
    stage.getContext().putAll(evaluatedContext);

    if (summary.getFailureCount() > 0) {
      stage.getContext().put(PipelineExpressionEvaluator.SUMMARY, summary.getExpressionResult());
    }

    return false;
  }

  public static final class EvaluateVariablesStageContext {
    private final List<Variable> vars;

    @JsonCreator
    public EvaluateVariablesStageContext(
        @JsonProperty("variables") @Nullable List<Variable> variables) {
      this.vars = variables;
    }

    public @Nullable List<Variable> getVariables() {
      return vars;
    }
  }

  public static class Variable {
    /** Variable name */
    private String key;

    /** Variable evaluated value */
    private Object value;

    /** Variable original value */
    private Object sourceExpression;

    public Variable() {}

    public void setKey(String key) {
      this.key = key;
    }

    public void setValue(Object value) {
      this.value = value;
    }

    public void setSourceValue(Object value) {
      this.sourceExpression = value;
    }

    public String getKey() {
      return key;
    }

    public Object getValue() {
      return value;
    }

    public Object getSourceValue() {
      return sourceExpression;
    }

    public void saveSourceExpression() {
      if (sourceExpression == null && value instanceof String) {
        sourceExpression = value.toString().replace("${", "{");
      }
    }
  }
}
