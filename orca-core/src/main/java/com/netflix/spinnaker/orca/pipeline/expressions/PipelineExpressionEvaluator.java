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

import com.google.common.base.Strings;
import com.netflix.spinnaker.kork.api.expressions.ExpressionFunctionProvider;
import com.netflix.spinnaker.kork.artifacts.model.Artifact;
import com.netflix.spinnaker.kork.expressions.ExpressionEvaluationSummary;
import com.netflix.spinnaker.kork.expressions.ExpressionTransform;
import com.netflix.spinnaker.kork.expressions.ExpressionsSupport;
import com.netflix.spinnaker.kork.expressions.config.ExpressionProperties;
import com.netflix.spinnaker.orca.api.pipeline.models.ExecutionStatus;
import com.netflix.spinnaker.orca.api.pipeline.models.PipelineExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.StageExecution;
import com.netflix.spinnaker.orca.api.pipeline.models.Trigger;
import com.netflix.spinnaker.orca.pipeline.model.*;
import java.util.*;
import java.util.function.Function;
import lombok.Getter;
import org.pf4j.PluginManager;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

public class PipelineExpressionEvaluator {
  public static final String SUMMARY = "expressionEvaluationSummary";
  public static final String ERROR = "Failed Expression Evaluation";

  public enum SpelEvaluatorVersion {
    V4(
        "v4",
        "Evaluates expressions at stage start; supports sequential evaluation of variables in Evaluate Variables stage",
        true),
    V3("v3", "Evaluates expressions as soon as possible, not recommended", true, false),
    V2("v2", "DO NOT USE", true, true);

    SpelEvaluatorVersion(String key, String description, boolean supported) {
      this(key, description, supported, false);
    }

    SpelEvaluatorVersion(String key, String description, boolean supported, boolean deprecated) {
      this.key = key;
      this.description = description;
      this.isSupported = supported;
      this.isDeprecated = deprecated;
    }

    String key;
    String description;
    boolean isSupported;
    boolean isDeprecated;

    public boolean getIsSupported() {
      return isSupported;
    }

    public boolean isDeprecated() {
      return isDeprecated;
    }

    public String getKey() {
      return key;
    }

    public String getDescription() {
      return description;
    }

    public static SpelEvaluatorVersion fromStringKey(String key) {
      if (Strings.isNullOrEmpty(key)) {
        return Default();
      }

      for (SpelEvaluatorVersion spelVersion : values()) {
        if (key.equalsIgnoreCase(spelVersion.key)) {
          return spelVersion;
        }
      }

      return Default();
    }

    public static boolean isSupported(String version) {
      for (SpelEvaluatorVersion spelEvaluatorVersion : values()) {
        if (version.equals(spelEvaluatorVersion.key)) {
          return spelEvaluatorVersion.isSupported;
        }
      }

      return false;
    }

    public static SpelEvaluatorVersion Default() {
      return V3;
    }
  }

  // No new items should go in to this list. We should use functions instead of vars going forward.
  private static final List<String> EXECUTION_AWARE_ALIASES =
      Collections.singletonList("deployedServerGroups");

  private static Class[] extraAllowedReturnTypes =
      new Class[] {
        Artifact.class,
        PipelineExecution.class,
        StageExecution.class,
        Trigger.class,
        BuildInfo.class,
        JenkinsArtifact.class,
        JenkinsBuildInfo.class,
        ConcourseBuildInfo.class,
        SourceControl.class,
        ExecutionStatus.class,
        PipelineExecution.AuthenticationDetails.class,
        PipelineExecution.PausedDetails.class
      };

  private final ExpressionParser parser = new SpelExpressionParser();
  private final ParserContext parserContext = new TemplateParserContext("${", "}");
  private final ExpressionsSupport support;

  @Getter private final Set<String> executionAwareFunctions = new HashSet<String>();

  public PipelineExpressionEvaluator(
      List<ExpressionFunctionProvider> expressionFunctionProviders,
      PluginManager pluginManager,
      ExpressionProperties expressionProperties) {
    this.support =
        new ExpressionsSupport(
            extraAllowedReturnTypes,
            expressionFunctionProviders,
            pluginManager,
            expressionProperties);
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
                        && f.getParameters().get(0).getType() == PipelineExecution.class) {
                      this.executionAwareFunctions.add(f.getName());
                    }
                  });
        });
  }
}
