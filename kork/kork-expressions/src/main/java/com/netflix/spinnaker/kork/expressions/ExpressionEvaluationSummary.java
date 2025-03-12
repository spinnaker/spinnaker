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

package com.netflix.spinnaker.kork.expressions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Represents an evaluation summary Includes INO and ERROR Level messages ERROR is anything
 * evaluation that results in thrown Exception INFO unresolved value returned that doesn't throw
 */
public class ExpressionEvaluationSummary {
  private Map<String, Set<Result>> expressionResult;
  private Set<String> attempts;
  private AtomicInteger failureCount;
  private AtomicInteger totalEvaluated;

  public ExpressionEvaluationSummary() {
    this.expressionResult = new HashMap<>();
    this.failureCount = new AtomicInteger();
    this.totalEvaluated = new AtomicInteger();
    this.attempts = new HashSet<>();
  }

  public int getTotalEvaluated() {
    return totalEvaluated.get();
  }

  public int getFailureCount() {
    return failureCount.get();
  }

  public Map<String, Set<Result>> getExpressionResult() {
    return expressionResult;
  }

  public void add(
      String escapedExpression, Result.Level level, String description, Class<?> exceptionType) {
    Set<Result> messages = expressionResult.getOrDefault(escapedExpression, new HashSet<>());
    messages.add(new Result(level, System.currentTimeMillis(), description, exceptionType));
    expressionResult.put(escapedExpression, messages);
    failureCount.incrementAndGet();
  }

  public void incrementTotalEvaluated() {
    totalEvaluated.incrementAndGet();
  }

  public void appendAttempted(String expression) {
    attempts.add(expression);
  }

  public String toString() {
    String attempted = attempts.stream().collect(Collectors.joining(","));
    String failed = expressionResult.keySet().stream().collect(Collectors.joining(","));
    return String.format(
        "%d expression(s) - (%s), %d failed - (%s)",
        getTotalEvaluated(), attempted, getFailureCount(), failed);
  }

  public boolean wasAttempted(String expression) {
    return attempts.contains(expression);
  }

  public boolean hasFailed(String expression) {
    return expressionResult.containsKey(expression);
  }

  public static class Result {
    private String description;
    private Class<?> exceptionType;
    private long timestamp;
    private Level level;

    public Result(Level level, long timestamp, String description, Class<?> exceptionType) {
      this.level = level;
      this.timestamp = timestamp;
      this.description = description;
      this.exceptionType = exceptionType;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public void setTimestamp(long timestamp) {
      this.timestamp = timestamp;
    }

    public Level getLevel() {
      return level;
    }

    public void setLevel(Level level) {
      this.level = level;
    }

    public Class<?> getExceptionType() {
      return exceptionType;
    }

    public void setExceptionType(Class<?> exceptionType) {
      this.exceptionType = exceptionType;
    }

    @Override
    public String toString() {
      return "Result {"
          + "description='"
          + description
          + '\''
          + ", exceptionType="
          + exceptionType
          + ", timestamp="
          + timestamp
          + ", level="
          + level
          + '}';
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      Result result = (Result) o;

      return (description != null
              ? description.equals(result.description)
              : result.description == null)
          && (exceptionType != null
              ? exceptionType.equals(result.exceptionType)
              : result.exceptionType == null)
          && level == result.level;
    }

    @Override
    public int hashCode() {
      int result = description != null ? description.hashCode() : 0;
      result = 31 * result + (exceptionType != null ? exceptionType.hashCode() : 0);
      result = 31 * result + (level != null ? level.hashCode() : 0);
      return result;
    }

    enum Level {
      ERROR,
      INFO
    }
  }
}
