/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package com.netflix.spinnaker.orca.pipeline.expressions;

import java.util.*;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ContextFunctionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import static com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator.ExpressionEvaluationVersion.V1;
import static com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator.ExpressionEvaluationVersion.V2;

public class PipelineExpressionEvaluator extends ExpressionsSupport implements ExpressionEvaluator {
  public static final String SUMMARY = "expressionEvaluationSummary";
  private static final String SPEL_EVALUATOR = "spelEvaluator";
  public static final String ERROR = "Failed Expression Evaluation";

  private final Logger log = LoggerFactory.getLogger(PipelineExpressionEvaluator.class);

  private final ExpressionParser parser = new SpelExpressionParser();
  private final String spelEvaluator;

  public interface ExpressionEvaluationVersion {
    String V2 = "v2";
    String V1 = "v1";
  }

  public PipelineExpressionEvaluator(final ContextFunctionConfiguration contextFunctionConfiguration) {
    super(contextFunctionConfiguration);
    spelEvaluator = contextFunctionConfiguration.getSpelEvaluator();
  }

  @Override
  public Map<String, Object> evaluate(Map<String, Object> source, Object rootObject, ExpressionEvaluationSummary summary, boolean ignoreUnknownProperties) {
    StandardEvaluationContext evaluationContext = newEvaluationContext(rootObject, ignoreUnknownProperties);
    return new ExpressionTransform(parserContext, parser).transform(source, evaluationContext, summary);
  }

  @Deprecated // V2 is default, v1 is not available anymore.
  public boolean shouldUseV2Evaluator(Object obj) {
    try {
      String versionInPipeline = getSpelVersion(obj);
      if (Arrays.asList(V1, V2).contains(versionInPipeline) && obj instanceof Map) {
        updateSpelEvaluatorVersion((Map) obj, versionInPipeline);
      }

      return !V1.equals(versionInPipeline) && (V2.equals(spelEvaluator) || V2.equals(versionInPipeline));
    } catch (Exception e) {
      log.error("Failed to determine whether to use v2 expression evaluator. using V1.", e);
    }

    return false;
  }

  private static boolean hasVersionInContext(Object obj) {
    return obj instanceof Stage && ((Stage) obj).getContext().containsKey(SPEL_EVALUATOR);
  }

  private static String getSpelVersion(Object obj) {
    if (obj instanceof Map) {
      Map pipelineConfig = (Map) obj;
      if (pipelineConfig.containsKey(SPEL_EVALUATOR)) {
        return (String) pipelineConfig.get(SPEL_EVALUATOR);
      }

      List<Map> stages = (List<Map>) Optional.ofNullable(pipelineConfig.get("stages")).orElse(Collections.emptyList());
      Map stage = stages
        .stream()
        .filter(i -> i.containsKey(SPEL_EVALUATOR))
        .findFirst()
        .orElse(null);

      return (stage != null) ? (String) stage.get(SPEL_EVALUATOR) : null;
    } else if (obj instanceof Execution) {
      Execution pipeline = (Execution) obj;
      Stage stage = pipeline.getStages()
        .stream()
        .filter(PipelineExpressionEvaluator::hasVersionInContext)
        .findFirst()
        .orElse(null);

      return (stage != null) ? (String) stage.getContext().get(SPEL_EVALUATOR) : null;

    } else if (obj instanceof Stage) {
      Stage stage = (Stage) obj;
      if (hasVersionInContext(obj)) {
        return (String) stage.getContext().get(SPEL_EVALUATOR);
      }

      // if any using v2
      List stages = stage.getExecution().getStages();
      Stage withVersion = (Stage) stages.stream()
        .filter(PipelineExpressionEvaluator::hasVersionInContext)
        .findFirst()
        .orElse(null);

      return (withVersion != null) ? (String) withVersion.getContext().get(SPEL_EVALUATOR) : null;
    }

    return null;
  }

  private static void updateSpelEvaluatorVersion(Map rawPipeline, String versionInPipeline) {
    Optional.ofNullable((List<Map>) rawPipeline.get("stages")).orElse(Collections.emptyList())
      .forEach(i -> i.put(SPEL_EVALUATOR, versionInPipeline));
  }
}



