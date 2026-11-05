/*
 * Copyright 2018 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
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
package com.netflix.spinnaker.kork.telemetry;

import static com.netflix.spinnaker.kork.telemetry.MetricTags.RESULT_KEY;
import static com.netflix.spinnaker.kork.telemetry.MetricTags.ResultValue.FAILURE;
import static com.netflix.spinnaker.kork.telemetry.MetricTags.ResultValue.SUCCESS;

import com.google.common.base.Strings;
import com.netflix.spinnaker.kork.annotations.Metered;
import com.netflix.spinnaker.kork.telemetry.MetricTags.ResultValue;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Adds automatic instrumentation to a target object's method invocations.
 *
 * <p>Two metrics will be recorded for any target: timing and invocations, with an additional tag
 * for "success", having either the value "success" or "failure".
 *
 * <p>Instrumented methods will be generated at proxy creation time, each associated with a metric
 * name following a pattern of "{namespace}.{method}.{metricName}", where "{metricName}" is either
 * "timing" or "invocations". The namespace is provided at creation time, and is typically unique
 * per target. The "method" is automatically generated, using the method name and parameter count of
 * the method.
 *
 * <p>Instrumented methods can be customized slightly via the {@code Metered} annotation:
 *
 * <p>- A method can be ignored, causing no metrics to be collected on it. - Provided a custom
 * metric name, in case auto naming produces naming conflicts. - A list of tags added to the
 * metrics.
 */
public class InstrumentedProxy implements InvocationHandler {

  public static <T> T proxy(MeterRegistry registry, Object target, String metricNamespace) {
    return proxy(registry, target, metricNamespace, new HashMap<>());
  }

  @SuppressWarnings("unchecked")
  public static <T> T proxy(
      MeterRegistry registry, Object target, String metricNamespace, Map<String, String> tags) {
    final Set<Class<?>> interfaces = new LinkedHashSet<>();
    addHierarchy(interfaces, target.getClass());

    final Class[] proxyInterfaces =
        interfaces.stream().filter(Class::isInterface).toArray(Class[]::new);

    return (T)
        Proxy.newProxyInstance(
            target.getClass().getClassLoader(),
            proxyInterfaces,
            new InstrumentedProxy(registry, target, metricNamespace, tags));
  }

  private static final String INVOCATIONS = "invocations";
  private static final String TIMING = "timing";

  private final MeterRegistry registry;
  private final Object target;
  private final String metricNamespace;
  private final Map<String, String> tags;

  private final Map<Method, MethodMetrics> instrumentedMethods = new ConcurrentHashMap<>();
  private final List<Method> seenMethods = new ArrayList<>();

  public InstrumentedProxy(MeterRegistry registry, Object target, String metricNamespace) {
    this(registry, target, metricNamespace, new HashMap<>());
  }

  public InstrumentedProxy(
      MeterRegistry registry, Object target, String metricNamespace, Map<String, String> tags) {
    this.registry = registry;
    this.target = target;
    this.metricNamespace = metricNamespace;
    this.tags = tags;
  }

  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    MethodMetrics methodMetrics = getMethodMetrics(method);
    ResultValue resultValue = FAILURE;
    final long start = System.currentTimeMillis();
    try {
      Object result = method.invoke(target, args);
      resultValue = SUCCESS;
      return result;
    } catch (InvocationTargetException e) {
      throw e.getCause();
    } finally {
      if (methodMetrics != null) {
        registry
            .counter(
                methodMetrics.invocationsName,
                methodMetrics.invocationsTags.and(RESULT_KEY, resultValue.toString()))
            .increment();
        recordTiming(
            methodMetrics.timingName,
            methodMetrics.timingTags.and(RESULT_KEY, resultValue.toString()),
            start);
      }
    }
  }

  private void recordTiming(String name, Tags tags, long startTimeMs) {
    Timer.builder(name)
        .tags(tags)
        .publishPercentileHistogram()
        .register(registry)
        .record(System.currentTimeMillis() - startTimeMs, TimeUnit.MILLISECONDS);
  }

  private static Tags toTags(Map<String, String> tags) {
    return Tags.of(
        tags.entrySet().stream()
            .map(e -> Tag.of(e.getKey(), e.getValue()))
            .collect(Collectors.toList()));
  }

  private MethodMetrics methodMetrics(Method method, Map<String, String> tags) {
    Tags micrometerTags = toTags(tags);
    return new MethodMetrics(
        MethodInstrumentation.toMetricId(metricNamespace, method, TIMING),
        micrometerTags,
        MethodInstrumentation.toMetricId(metricNamespace, method, INVOCATIONS),
        micrometerTags);
  }

  private MethodMetrics methodMetrics(String methodOverride, Map<String, String> tags) {
    Tags micrometerTags = toTags(tags);
    return new MethodMetrics(
        MethodInstrumentation.toMetricId(methodOverride, metricNamespace, TIMING),
        micrometerTags,
        MethodInstrumentation.toMetricId(methodOverride, metricNamespace, INVOCATIONS),
        micrometerTags);
  }

  private MethodMetrics getMethodMetrics(Method method) {
    if (!instrumentedMethods.containsKey(method) && !seenMethods.contains(method)) {
      seenMethods.add(method);
      boolean processed = false;
      for (Annotation a : method.getDeclaredAnnotations()) {
        if (a instanceof Metered) {
          processed = true;

          Metered metered = (Metered) a;
          if (metered.ignore()) {
            return null;
          }

          Map<String, String> methodTags =
              MethodInstrumentation.coalesceTags(target, method, tags, metered.tags());
          if (Strings.isNullOrEmpty(metered.metricName())) {
            addInstrumentedMethod(instrumentedMethods, method, methodMetrics(method, methodTags));
          } else {
            addInstrumentedMethod(
                instrumentedMethods, method, methodMetrics(metered.metricName(), methodTags));
          }
        }
      }

      if (!processed && !instrumentedMethods.containsKey(method)) {
        addInstrumentedMethod(instrumentedMethods, method, methodMetrics(method, tags));
      }
    }
    return instrumentedMethods.get(method);
  }

  private void addInstrumentedMethod(
      Map<Method, MethodMetrics> existingMethodMetrics,
      Method method,
      MethodMetrics methodMetrics) {
    if (!MethodInstrumentation.isMethodAllowed(method)) {
      return;
    }

    existingMethodMetrics.putIfAbsent(method, methodMetrics);
  }

  private static void addHierarchy(Set<Class<?>> classes, Class<?> cl) {
    if (cl == null) {
      return;
    }

    if (classes.add(cl)) {
      for (Class<?> iface : cl.getInterfaces()) {
        addHierarchy(classes, iface);
      }
      Class<?> superclass = cl.getSuperclass();
      if (superclass != null) {
        addHierarchy(classes, superclass);
      }
    }
  }

  private static class MethodMetrics {
    final String timingName;
    final Tags timingTags;
    final String invocationsName;
    final Tags invocationsTags;

    MethodMetrics(
        String timingName, Tags timingTags, String invocationsName, Tags invocationsTags) {
      this.timingName = timingName;
      this.timingTags = timingTags;
      this.invocationsName = invocationsName;
      this.invocationsTags = invocationsTags;
    }
  }
}
