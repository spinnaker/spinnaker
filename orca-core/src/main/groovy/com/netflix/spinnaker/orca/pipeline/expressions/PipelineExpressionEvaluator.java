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

import com.netflix.spinnaker.orca.pipeline.model.Pipeline;
import com.netflix.spinnaker.orca.pipeline.model.Stage;
import com.netflix.spinnaker.orca.pipeline.util.ContextFunctionConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.netflix.spinnaker.orca.pipeline.expressions.PipelineExpressionEvaluator.ExpressionEvaluationVersion.V2;

public class PipelineExpressionEvaluator extends ExpressionsSupport implements ExpressionEvaluator {
  private static final Logger LOGGER = LoggerFactory.getLogger(PipelineExpressionEvaluator.class);
  public static final String SUMMARY = "expressionEvaluationSummary";
  private static final String SPEL_EVALUATOR = "spel-evaluator";
  private final ExpressionParser parser = new SpelExpressionParser();

  interface ExpressionEvaluationVersion {
    String V2 = "v2";
  }

  public PipelineExpressionEvaluator(final ContextFunctionConfiguration contextFunctionConfiguration) {
    super(contextFunctionConfiguration);
  }

  @Override
  public Map<String, Object> evaluate(Map<String, Object> source, Object rootObject, ExpressionEvaluationSummary summary, boolean ignoreUnknownProperties) {
    StandardEvaluationContext evaluationContext = newEvaluationContext(rootObject, ignoreUnknownProperties);
    return new ExpressionTransform(parserContext, parser).transform(source, evaluationContext, summary);
  }

  public static boolean shouldUseV2Evaluator(Object obj) {
    try {
      if (obj instanceof Map) {
        Map pipelineConfig = (Map) obj;
        //if using v2, add version to stage context.
        if (V2.equals(pipelineConfig.get(SPEL_EVALUATOR))) {
          updateSpelEvaluatorVersion(pipelineConfig);
          return true;
        }

        List<Map> stages = (List<Map>) Optional.ofNullable(pipelineConfig.get("stages")).orElse(Collections.emptyList());
        boolean useV2 = stages
          .stream()
          .filter(i -> i.containsKey(SPEL_EVALUATOR) && V2.equals(i.get(SPEL_EVALUATOR)))
          .findFirst()
          .orElse(null) != null;

        if (useV2) {
          updateSpelEvaluatorVersion(pipelineConfig);
        }
      } else if (obj instanceof Pipeline) {
        Pipeline pipeline = (Pipeline) obj;
        List stages = Optional.ofNullable(pipeline.getStages()).orElse(Collections.emptyList());
        return stages
          .stream()
          .filter(s -> hasV2InContext(s))
          .findFirst()
          .orElse(null) != null;

      } else if (obj instanceof Stage) {
        if (hasV2InContext(obj)) {
          return true;
        }

        Stage stage = (Stage) obj;
        // if any using v2
        return stage.getExecution().getStages()
          .stream()
          .filter(s -> hasV2InContext(s))
          .findFirst()
          .orElse(null) != null;
      }
    } catch (Exception e) {
      LOGGER.error("Failed to determine whether to use v2 expression evaluator. using V1.", e);
    }

    return false;
  }

  private static boolean hasV2InContext(Object obj) {
    return obj instanceof Stage && ((Stage) obj).getContext() != null && V2.equals(((Stage) obj).getContext().get(SPEL_EVALUATOR));
  }

  private static void updateSpelEvaluatorVersion(Map rawPipeline) {
    Optional.ofNullable((List<Map>) rawPipeline.get("stages")).orElse(Collections.emptyList())
      .forEach(i -> i.put(SPEL_EVALUATOR, V2));
  }
}



