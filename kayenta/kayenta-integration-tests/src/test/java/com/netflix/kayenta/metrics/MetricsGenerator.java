/*
 * Copyright 2019 Playtika
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
package com.netflix.kayenta.metrics;

import com.netflix.kayenta.metrics.CanaryAnalysisCasesConfigurationProperties.ScopeMetricsConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.ToLongFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;

@RequiredArgsConstructor
public class MetricsGenerator {

  private final MeterRegistry registry;
  private final RandomProvider randomProvider;
  private final CanaryAnalysisCasesConfigurationProperties configuration;
  private final Map<Timer, ToLongFunction<RandomProvider>> timers = new HashMap<>();

  @PostConstruct
  public void register() {
    configuration
        .getCases()
        .forEach(
            (caseName, analysisConfiguration) -> {
              String namespace = analysisConfiguration.getNamespace();

              createMetricsForScope(namespace, analysisConfiguration.getControl());
              createMetricsForScope(namespace, analysisConfiguration.getExperiment());
            });
  }

  private void createMetricsForScope(String namespace, ScopeMetricsConfiguration scopeConfig) {
    String scope = scopeConfig.getScope();
    Tags tags = Tags.of(Tag.of("scope", scope), Tag.of("namespace", namespace));

    scopeConfig
        .getMetrics()
        .forEach(
            metric -> {
              String metricName = metric.getName();
              switch (metric.getType()) {
                case "gauge":
                  registry.gauge(
                      metricName,
                      tags,
                      randomProvider,
                      provider ->
                          provider.getDouble(metric.getLowerBound(), metric.getUpperBound()));
                  break;
                case "timer":
                  this.timers.put(
                      registry.timer(metricName, tags),
                      provider ->
                          randomProvider.getLong(metric.getLowerBound(), metric.getUpperBound()));
                  break;
                default:
                  throw new IllegalArgumentException("Unknown metric type for metric: " + metric);
              }
            });
  }

  @Scheduled(fixedRate = 1_000)
  public void requestMetrics() {
    this.timers.forEach(
        (timer, valueProvider) ->
            timer.record(valueProvider.applyAsLong(randomProvider), TimeUnit.MILLISECONDS));
  }
}
