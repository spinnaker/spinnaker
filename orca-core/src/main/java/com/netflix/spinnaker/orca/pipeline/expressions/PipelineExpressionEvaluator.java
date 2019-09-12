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

import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.expressions.ExpressionEvaluationSummary;
import com.netflix.spinnaker.kork.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.kork.expressions.ExpressionTransform;
import com.netflix.spinnaker.kork.expressions.ExpressionsSupport;
import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import lombok.Getter;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

public class PipelineExpressionEvaluator {
  public static final String SUMMARY = "expressionEvaluationSummary";
  public static final String ERROR = "Failed Expression Evaluation";

  // No new items should go in to this list. We should use functions instead of vars going forward.
  private static final List<String> EXECUTION_AWARE_ALIASES =
      Collections.singletonList("deployedServerGroups");

  private static Class[] extraAllowedReturnTypes =
      new Class[] {
        Artifact.class,
        Execution.class,
        Stage.class,
        Trigger.class,
        BuildInfo.class,
        JenkinsArtifact.class,
        JenkinsBuildInfo.class,
        ConcourseBuildInfo.class,
        SourceControl.class,
        ExecutionStatus.class,
        Execution.AuthenticationDetails.class,
        Execution.PausedDetails.class
      };

  private final ExpressionParser parser = new SpelExpressionParser();
  private final ParserContext parserContext = new TemplateParserContext("${", "}");
  private final ExpressionsSupport support;

  @Getter private final Set<String> executionAwareFunctions = new HashSet<String>();

  public PipelineExpressionEvaluator(List<ExpressionFunctionProvider> expressionFunctionProviders) {
    this.support = new ExpressionsSupport(extraAllowedReturnTypes, expressionFunctionProviders);
    initExecutionAwareFunctions(expressionFunctionProviders);
  }

  public Map<String, Object> evaluate(
      Map<String, Object> source,
      Object rootObject,
      ExpressionEvaluationSummary summary,
      boolean allowUnknownKeys) {
    StandardEvaluationContext evaluationContext =
        support.buildEvaluationContext(rootObject, allowUnknownKeys);
    return new ExpressionTransform(
            parserContext, parser, includeExecutionParameter, ExecutionStatus.class)
        .transformMap(source, evaluationContext, summary);
  }

  private final Function<String, String> includeExecutionParameter =
      e -> {
        String expression = e;
        for (String fn : this.executionAwareFunctions) {
          if (expression.contains("#" + fn)
              && !expression.contains("#" + fn + "( #root.execution, ")) {
            expression = expression.replaceAll("#" + fn + "\\(", "#" + fn + "( #root.execution, ");
          }
        }

        // 'deployServerGroups' is a variable instead of a function and this block handles that.
        // Migrate the pipelines to use function instead, before removing this block of code.
        for (String a : EXECUTION_AWARE_ALIASES) {
          if (expression.contains(a) && !expression.contains("#" + a + "( #root.execution, ")) {
            expression = expression.replaceAll(a, "#" + a + "( #root.execution)");
          }
        }

        return expression;
      };

  private void initExecutionAwareFunctions(
      List<ExpressionFunctionProvider> expressionFunctionProviders) {

    expressionFunctionProviders.forEach(
        p -> {
          p.getFunctions()
              .getFunctionsDefinitions()
              .forEach(
                  f -> {
                    if (!f.getParameters().isEmpty()
                        && f.getParameters().get(0).getType() == Execution.class) {
                      this.executionAwareFunctions.add(f.getName());
                    }
                  });
        });
  }
}
