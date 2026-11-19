/*
 * Copyright 2026 Harness, Inc.
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
 */

package com.netflix.spinnaker.kork.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.springframework.context.ApplicationContext;

/** Assertions for service boot tests about the meters a started application registers. */
public final class MeterRegistryAssertions {

  private MeterRegistryAssertions() {}

  /**
   * Fails if OTLP export is on, or if any registry in {@code context} holds meters that OTLP and
   * Prometheus can't export: one name with more than one meter type, or Spectator percentile
   * counters ({@code statistic=percentile}).
   */
  public static void assertExportableByDefault(ApplicationContext context) {
    Set<String> otlpRegistries = context.getBeansOfType(OtlpMeterRegistry.class).keySet();
    if (!otlpRegistries.isEmpty()) {
      throw new AssertionError("OTLP export is on by default: " + otlpRegistries);
    }

    List<String> problems = new ArrayList<>();
    context
        .getBeansOfType(MeterRegistry.class)
        .forEach((beanName, registry) -> problems.addAll(exportProblems(beanName, registry)));
    if (!problems.isEmpty()) {
      throw new AssertionError(String.join("\n", problems));
    }
  }

  private static List<String> exportProblems(String beanName, MeterRegistry registry) {
    Map<String, Set<Meter.Type>> typesByName = new TreeMap<>();
    Set<String> percentileNames = new TreeSet<>();
    for (Meter meter : registry.getMeters()) {
      Meter.Id id = meter.getId();
      typesByName
          .computeIfAbsent(id.getName(), name -> EnumSet.noneOf(Meter.Type.class))
          .add(id.getType());
      if ("percentile".equals(id.getTag("statistic"))) {
        percentileNames.add(id.getName());
      }
    }

    List<String> problems = new ArrayList<>();
    typesByName.forEach(
        (name, types) -> {
          if (types.size() > 1) {
            problems.add(beanName + ": " + name + " has more than one meter type " + types);
          }
        });
    percentileNames.forEach(
        name -> problems.add(beanName + ": " + name + " has Spectator percentile counters"));
    return problems;
  }
}
