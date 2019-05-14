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

import static java.lang.String.format;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toSet;

import com.netflix.spinnaker.orca.ExecutionStatus;
import com.netflix.spinnaker.orca.pipeline.model.Execution;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.CompositeStringExpression;

public class ExpressionTransform {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final List<String> EXECUTION_AWARE_FUNCTIONS =
      Arrays.asList(
          "judgment",
          "judgement",
          "stage",
          "stageExists",
          "deployedServerGroups",
          "manifestLabelValue");
  private static final List<String> EXECUTION_AWARE_ALIASES =
      Collections.singletonList("deployedServerGroups");
  private static final List<Class> STRINGIFYABLE_TYPES =
      Collections.singletonList(ExecutionStatus.class);

  private final Collection<ExpressionFunctionProvider> expressionFunctionProviders;
  private final ParserContext parserContext;
  private final ExpressionParser parser;

  public ExpressionTransform(
      Collection<ExpressionFunctionProvider> expressionFunctionProviders,
      ParserContext parserContext,
      ExpressionParser parser) {
    this.expressionFunctionProviders = expressionFunctionProviders;
    this.parserContext = parserContext;
    this.parser = parser;
  }

  /**
   * Traverses and attempts to evaluate expressions Failures can either be INFO (for a simple
   * unresolved expression) or ERROR when an exception is thrown
   *
   * @return the transformed source object
   */
  public Map<String, Object> transformMap(
      Map<String, Object> source,
      EvaluationContext evaluationContext,
      ExpressionEvaluationSummary summary) {
    Map<String, Object> result = new HashMap<>();
    Map<String, Object> copy = Collections.unmodifiableMap(source);
    source.forEach(
        (key, value) -> {
          if (value instanceof Map) {
            result.put(
                transformString(key, evaluationContext, summary, copy).toString(),
                transformMap((Map) value, evaluationContext, summary));
          } else if (value instanceof List) {
            result.put(
                transformString(key, evaluationContext, summary, copy).toString(),
                transformList((List) value, evaluationContext, summary, copy));
          } else {
            result.put(
                transformString(key, evaluationContext, summary, copy).toString(),
                transformString(value, evaluationContext, summary, copy));
          }
        });

    return result;
  }

  public List transformList(
      List source,
      EvaluationContext evaluationContext,
      ExpressionEvaluationSummary summary,
      Map<String, Object> additionalContext) {
    List<Object> result = new ArrayList<>();
    for (Object obj : source) {
      if (obj instanceof Map) {
        result.add(transformMap((Map<String, Object>) obj, evaluationContext, summary));
      } else if (obj instanceof List) {
        result.addAll(transformList((List) obj, evaluationContext, summary, additionalContext));
      } else {
        result.add(transformString(obj, evaluationContext, summary, additionalContext));
      }
    }

    return result;
  }

  public Object transformString(
      Object source,
      EvaluationContext evaluationContext,
      ExpressionEvaluationSummary summary,
      Map<String, Object> additionalContext) {
    boolean hasUnresolvedExpressions = false;
    if (isExpression(source)) {
      String literalExpression = includeExecutionParameter(source.toString());
      Object result = null;
      String escapedExpressionString = null;
      Throwable exception = null;
      try {
        Expression exp = parser.parseExpression(literalExpression, parserContext);
        escapedExpressionString = escapeExpression(exp);
        if (exp instanceof CompositeStringExpression) {
          StringBuilder sb = new StringBuilder();
          Expression[] expressions = ((CompositeStringExpression) exp).getExpressions();
          for (Expression e : expressions) {
            String value = e.getValue(evaluationContext, String.class);
            if (value == null) {
              value = String.format("${%s}", e.getExpressionString());
              hasUnresolvedExpressions = true;
            }
            sb.append(value);
          }

          result = sb.toString();
        } else {
          result = exp.getValue(evaluationContext);
        }
      } catch (Exception e) {
        log.info("Failed to evaluate {}, returning raw value {}", source, e.getMessage());
        exception = e;
      } finally {
        Set keys = getKeys(source, additionalContext);
        Object fields = !keys.isEmpty() ? keys : literalExpression;
        String errorDescription = format("Failed to evaluate %s ", fields);
        escapedExpressionString =
            escapedExpressionString != null
                ? escapedExpressionString
                : escapeSimpleExpression(source.toString());
        if (exception != null) {
          Throwable originalException = unwrapOriginalException(exception);
          if (originalException == null
              || originalException.getMessage() == null
              || originalException.getMessage().contains(exception.getMessage())) {
            errorDescription += exception.getMessage();
          } else {
            errorDescription += originalException.getMessage() + " - " + exception.getMessage();
          }

          summary.add(
              escapedExpressionString,
              ExpressionEvaluationSummary.Result.Level.ERROR,
              errorDescription.replaceAll("\\$", ""),
              Optional.ofNullable(originalException).map(Object::getClass).orElse(null));

          result = source;
        } else if (result == null || hasUnresolvedExpressions) {
          summary.add(
              escapedExpressionString,
              ExpressionEvaluationSummary.Result.Level.INFO,
              format("%s: %s not found", errorDescription, escapedExpressionString),
              null);

          if (result == null) {
            result = source;
          }
        }

        summary.appendAttempted(escapedExpressionString);
        summary.incrementTotalEvaluated();
      }

      if (STRINGIFYABLE_TYPES.contains(result.getClass())) {
        result = result.toString();
      }

      return result;
    }

    return source;
  }

  private boolean isExpression(Object obj) {
    return (obj instanceof String && obj.toString().contains(parserContext.getExpressionPrefix()));
  }

  /** finds parent keys by value in a nested map */
  private Set<String> getKeys(Object value, final Map<String, Object> map) {
    if (map == null || map.isEmpty()) {
      return emptySet();
    }

    return map.entrySet().stream()
        .filter(
            it ->
                flatten(it.getValue()).collect(toSet()).stream()
                    .flatMap(Stream::of)
                    .collect(toSet())
                    .contains(value))
        .map(Map.Entry::getKey)
        .collect(toSet());
  }

  private static Stream<?> flatten(Object o) {
    if (o instanceof Map) {
      Map<Object, Object> map = (Map) o;
      List<Object> tokens = new ArrayList<>();
      tokens.addAll(map.keySet());
      tokens.addAll(map.values());
      return tokens.stream().flatMap(ExpressionTransform::flatten);
    }

    return Stream.of(o);
  }

  /** Finds the original exception in the exception hierarchy */
  private static Throwable unwrapOriginalException(Throwable e) {
    if (e == null || e.getCause() == null) return e;
    return unwrapOriginalException(e.getCause());
  }

  /** Helper to escape an expression: stripping ${ } */
  private String escapeExpression(Expression expression) {
    if (expression instanceof CompositeStringExpression) {
      StringBuilder sb = new StringBuilder();
      for (Expression e : ((CompositeStringExpression) expression).getExpressions()) {
        sb.append(e.getExpressionString());
      }

      return sb.toString();
    }

    return expression.getExpressionString();
  }

  /**
   * Helper to escape a simple expression string Used to extract a simple expression when parsing
   * fails
   */
  static String escapeSimpleExpression(String expression) {
    String escaped = null;
    Matcher matcher = Pattern.compile("\\$\\{(.*)}").matcher(expression);
    if (matcher.matches()) {
      escaped = matcher.group(1).trim();
    }

    return escaped != null ? escaped : expression.replaceAll("\\$", "");
  }

  /**
   * Lazily include the execution object (#root.execution) for Stage locating functions & aliases
   *
   * @param e #stage('property') becomes #stage(#root.execution, 'property')
   * @return an execution aware helper function
   */
  private String includeExecutionParameter(String e) {
    String expression = e;

    // An expression aware function is any that takes an Execution as its first parameter
    Set<String> expressionAwareFunctions = new HashSet<>(EXECUTION_AWARE_FUNCTIONS);
    expressionFunctionProviders.forEach(
        p -> {
          p.getFunctions()
              .forEach(
                  f -> {
                    if (!f.getParameters().isEmpty()
                        && f.getParameters().get(0).getType() == Execution.class) {
                      expressionAwareFunctions.add(f.getName());
                    }
                  });
        });

    for (String fn : expressionAwareFunctions) {
      if (expression.contains("#" + fn) && !expression.contains("#" + fn + "( #root.execution, ")) {
        expression = expression.replaceAll("#" + fn + "\\(", "#" + fn + "( #root.execution, ");
      }
    }

    for (String a : EXECUTION_AWARE_ALIASES) {
      if (expression.contains(a) && !expression.contains("#" + a + "( #root.execution, ")) {
        expression = expression.replaceAll(a, "#" + a + "( #root.execution)");
      }
    }

    return expression;
  }
}
