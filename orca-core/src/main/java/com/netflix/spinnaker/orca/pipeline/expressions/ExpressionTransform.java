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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.ParserContext;
import org.springframework.expression.common.CompositeStringExpression;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.lang.String.format;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

public class ExpressionTransform {
  private final Logger log = LoggerFactory.getLogger(getClass());

  private static final List<String> EXECUTION_AWARE_FUNCTIONS = Arrays.asList("judgment", "judgement", "stage", "stageExists", "deployedServerGroups");
  private static final List<String> EXECUTION_AWARE_ALIASES = Arrays.asList("deployedServerGroups");
  private final ParserContext parserContext;
  private final ExpressionParser parser;

  public ExpressionTransform(ParserContext parserContext, ExpressionParser parser) {
    this.parserContext = parserContext;
    this.parser = parser;
  }

  public <T> T transform(T source, EvaluationContext evaluationContext, ExpressionEvaluationSummary summary) {
    return transform(source, evaluationContext, summary, emptyMap());
  }

  /**
   * Traverses and attempts to evaluate expressions
   * Failures can either be INFO (for a simple unresolved expression) or ERROR when an exception is thrown
   *
   * @param source
   * @param evaluationContext
   * @param summary
   * @return the transformed source object
   */
  public <T> T transform(T source, EvaluationContext evaluationContext, ExpressionEvaluationSummary summary, Map<String, ?> additionalContext) {
    if (source == null) {
      return null;
    }

    if (source instanceof Map) {
      Map<String, Object> copy = Collections.unmodifiableMap((Map) source);
      Map<String, Object> result = new HashMap<>();
      for (Map.Entry<String, Object> entry : ((Map<String, Object>) source).entrySet()) {
        result.put(
          transform(entry.getKey(), evaluationContext, summary, copy),
          transform(entry.getValue(), evaluationContext, summary, copy)
        );
      }
      return (T) result;
    } else if (source instanceof List) {
      return (T) ((List) source).stream().map(it ->
        transform(it, evaluationContext, summary)
      ).collect(toList());
    } else if ((source instanceof CharSequence) && source.toString().contains(parserContext.getExpressionPrefix())) {
      String literalExpression = source.toString();
      literalExpression = includeExecutionParameter(literalExpression);

      T result = null;
      Expression exp;
      String escapedExpressionString = null;
      Throwable exception = null;
      try {
        exp = parser.parseExpression(literalExpression, parserContext);
        escapedExpressionString = escapeExpression(exp);
        result = (T) exp.getValue(evaluationContext);
      } catch (Exception e) {
        log.info("Failed to evaluate {}, returning raw value {}", source, e.getMessage());
        exception = e;
      } finally {
        escapedExpressionString = escapedExpressionString != null ? escapedExpressionString : escapeSimpleExpression(source.toString());
        if (exception != null) {
          Set<String> keys = getKeys(source, additionalContext);
          Object fields = !keys.isEmpty() ? keys : literalExpression;
          String errorDescription = format("Failed to evaluate %s ", fields);
          Throwable originalException = unwrapOriginalException(exception);
          if (originalException == null || originalException.getMessage() == null || originalException.getMessage().contains(exception.getMessage())) {
            errorDescription += exception.getMessage();
          } else {
            errorDescription += originalException.getMessage() + " - " + exception.getMessage();
          }

          summary.add(
            escapedExpressionString,
            ExpressionEvaluationSummary.Result.Level.ERROR,
            errorDescription.replaceAll("\\$", ""),
            Optional.ofNullable(originalException).map(Object::getClass).orElse(null)
          );

          result = source;
        } else if (result == null) {
          Set<String> keys = getKeys(source, additionalContext);
          Object fields = !keys.isEmpty() ? keys : literalExpression;
          String errorDescription = format("Failed to evaluate %s ", fields);
          summary.add(
            escapedExpressionString,
            ExpressionEvaluationSummary.Result.Level.INFO,
            format("%s: %s not found", errorDescription, escapedExpressionString),
            null
          );

          result = source;
        }

        summary.appendAttempted(escapedExpressionString);
        summary.incrementTotalEvaluated();
      }

      return result;
    } else {
      return source;
    }
  }

  /**
   * finds parent keys by value in a nested map
   */
  private static Set<String> getKeys(Object value, final Map<String, ?> map) {
    if (map == null || map.isEmpty()) {
      return emptySet();
    }

    return map
      .entrySet()
      .stream()
      .filter(it ->
        flatten(it.getValue()).collect(toSet()).stream().flatMap(Stream::of).collect(toSet())/*.flatten()*/.contains(value)
      )
      .map(Map.Entry::getKey)
      .collect(toSet());
  }

  private static Stream<?> flatten(Object o) {
    if (o instanceof Map) {
      Map<Object, Object> map = (Map) o;
      List<Object> tokens = new ArrayList<>();
      tokens.addAll(map.keySet());
      tokens.addAll(map.values());
      return tokens
        .stream()
        .flatMap(ExpressionTransform::flatten);
    }

    return Stream.of(o);
  }

  /**
   * Finds the original exception in the exception hierarchy
   */
  private static Throwable unwrapOriginalException(Throwable e) {
    if (e == null || e.getCause() == null) return e;
    return unwrapOriginalException(e.getCause());
  }

  /**
   * Helper to escape an expression: stripping ${ }
   */
  static String escapeExpression(Expression expression) {
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
   * Helper to escape  a simple expression string
   * Used to extract a simple expression when parsing fails
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
   * @param expression #stage('property') becomes #stage(#root.execution, 'property')
   * @return an execution aware helper function
   */
  private static String includeExecutionParameter(String e) {
    String expression = e;
    for (String fn : EXECUTION_AWARE_FUNCTIONS) {
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


