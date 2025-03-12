/*
 * Copyright 2020 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
 *
 */

package com.netflix.spinnaker.kork.telemetry;

import static java.lang.String.format;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper functions and common exceptions to use when instrumenting methods with metric IDs and
 * tags.
 */
public class MethodInstrumentation {

  public static boolean isMethodAllowed(Method method) {
    return
    // Only instrument public methods
    Modifier.isPublic(method.getModifiers())
        &&
        // Ignore any methods from the root Object class
        Arrays.stream(Object.class.getDeclaredMethods())
            .noneMatch(m -> m.getName().equals(method.getName()));
  }

  public static String toMetricId(String metricNamespace, Method method, String metricName) {
    String methodName =
        (method.getParameterCount() == 0)
            ? method.getName()
            : format("%s%d", method.getName(), method.getParameterCount());
    return toMetricId(metricNamespace, methodName, metricName);
  }

  public static String toMetricId(String metricNamespace, String methodName, String metricName) {
    return format("%s.%s.%s", metricNamespace, methodName, metricName);
  }

  public static Map<String, String> coalesceTags(
      Object target, Method method, Map<String, String> defaultTags, String[] methodTags) {
    if (methodTags.length % 2 != 0) {
      throw new UnevenTagSequenceException(target, method.toGenericString());
    }
    Map<String, String> result = new HashMap<>(defaultTags);
    for (int i = 0; i < methodTags.length; i = i + 2) {
      result.put(methodTags[i], methodTags[i + 1]);
    }
    return result;
  }

  private static class UnevenTagSequenceException extends IllegalStateException {
    public UnevenTagSequenceException(Object target, String method) {
      super(
          format(
              "There are an uneven number of values provided for tags on method '%s' in '%s'",
              method, target.getClass().getSimpleName()));
    }
  }

  public static class MetricNameCollisionException extends IllegalStateException {
    public MetricNameCollisionException(
        Object target, String metricName, Method method1, Method method2) {
      super(
          format(
              "Metric name (%s) collision detected between methods '%s' and '%s' in '%s'",
              metricName,
              method1.toGenericString(),
              method2.toGenericString(),
              target.getClass().getSimpleName()));
    }
  }
}
