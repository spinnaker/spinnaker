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

import com.netflix.spinnaker.orca.pipeline.util.ContextFunctionConfiguration;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Map;

public class PipelineExpressionEvaluator extends ExpressionsSupport implements ExpressionEvaluator {
  public static final String SUMMARY = "expressionEvaluationSummary";
  public static final String ERROR = "Failed Expression Evaluation";

  private final ExpressionParser parser = new SpelExpressionParser();

  public interface ExpressionEvaluationVersion {
    String V2 = "v2";
  }

  public PipelineExpressionEvaluator(final ContextFunctionConfiguration contextFunctionConfiguration) {
    super(contextFunctionConfiguration);
  }

  @Override
  public Map<String, Object> evaluate(Map<String, Object> source, Object rootObject, ExpressionEvaluationSummary summary, boolean allowUnknownKeys) {
    StandardEvaluationContext evaluationContext = newEvaluationContext(rootObject, allowUnknownKeys);
    return new ExpressionTransform(parserContext, parser).transformMap(source, evaluationContext, summary);
  }
}



