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
package com.netflix.kayenta.configuration;

import com.netflix.kayenta.metrics.CanaryAnalysisCasesConfigurationProperties;
import com.netflix.kayenta.metrics.MetricsGenerator;
import com.netflix.kayenta.metrics.PercentilePrecisionMeterConfigurationFilter;
import com.netflix.kayenta.metrics.RandomProvider;
import com.netflix.kayenta.steps.StandaloneCanaryAnalysisSteps;
import com.netflix.spectator.api.DefaultRegistry;
import com.netflix.spectator.api.Registry;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.config.MeterFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@EnableConfigurationProperties(CanaryAnalysisCasesConfigurationProperties.class)
@Configuration
public class MetricsReportingConfiguration {

  /**
   * Override the spectator registry so that the spring composite micrometer registry gets created.
   */
  @Bean
  Registry registry() {
    return new DefaultRegistry();
  }

  @Bean
  public RandomProvider randomProvider() {
    return new RandomProvider();
  }

  @Bean
  public MetricsGenerator metricsGenerator(
      MeterRegistry registry,
      RandomProvider randomProvider,
      CanaryAnalysisCasesConfigurationProperties configuration) {
    return new MetricsGenerator(registry, randomProvider, configuration);
  }

  @Bean
  public StandaloneCanaryAnalysisSteps canaryAnalysisSteps(
      Environment environment, CanaryAnalysisCasesConfigurationProperties configuration) {
    return new StandaloneCanaryAnalysisSteps(environment, configuration);
  }

  @Bean
  public MeterFilter percentilePrecisionConfigurationFilter() {
    return new PercentilePrecisionMeterConfigurationFilter();
  }
}
