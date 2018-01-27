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

package com.netflix.spinnaker.orca.pipeline.tasks;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.annotation.Nonnull;
import com.google.common.collect.ImmutableMap;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.TaskResult;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ContextParameterProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import static com.netflix.spinnaker.orca.ExecutionStatus.SUCCEEDED;
import static com.netflix.spinnaker.orca.ExecutionStatus.TERMINAL;
import static com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator.SUMMARY;
import static java.util.Collections.singletonMap;

@Component
public class ExpressionPreconditionTask implements PreconditionTask {

  @Override public String getPreconditionType() {
    return "expression";
  }

  private final ContextParameterProcessor contextParameterProcessor;

  @Autowired
  public ExpressionPreconditionTask(ContextParameterProcessor contextParameterProcessor) {
    this.contextParameterProcessor = contextParameterProcessor;
  }

  @Override
  public @Nonnull TaskResult execute(@Nonnull Stage stage) {
    StageData stageData = stage.mapTo("/context", StageData.class);

    Map<String, Object> result = contextParameterProcessor.process(singletonMap(
      "expression", "${" + stageData.expression + '}'
    ), contextParameterProcessor.buildExecutionContext(stage, true), true);

    String expression = result.get("expression").toString();
    Matcher matcher = Pattern.compile("\\$\\{(.*)}").matcher(expression);
    if (matcher.matches()) {
      expression = matcher.group(1);
    }

    ensureEvaluationSummaryIncluded(result, stage, expression);
    ExecutionStatus status = Boolean.valueOf(expression) ? SUCCEEDED : TERMINAL;

    Map<String, Object> context = (Map<String, Object>) stage.getContext().get("context");
    context.put("expressionResult", expression);
    return new TaskResult(status, singletonMap("context", context));
  }

  private static void ensureEvaluationSummaryIncluded(Map<String, Object> result, Stage stage, String expression) {
    if (!expression.trim().startsWith("$") && result.containsKey(SUMMARY)) {
      Map<String, Object> context = stage.getContext();
      Map<String, Object> summaryFromContext = (Map<String, Object>) context.get(SUMMARY);
      Map<String, Object> summaryFromResult = (Map<String, Object>) result.get(SUMMARY);
      context.put(
        SUMMARY,
        summaryFromContext == null || summaryFromContext.isEmpty() ? summaryFromResult : ImmutableMap.builder().putAll(summaryFromContext).putAll(summaryFromResult).build()
      );
    }
  }

  private static class StageData {
    public String expression = "false";
  }
}
