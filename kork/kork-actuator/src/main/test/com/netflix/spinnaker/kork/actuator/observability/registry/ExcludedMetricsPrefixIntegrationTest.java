/*
 * Copyright 2025 Harness, Inc.
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

package com.netflix.spinnaker.kork.actuator.observability.registry;

import static org.junit.Assert.*;

import com.netflix.spinnaker.kork.actuator.observability.config.ObservabilityConfiguration;
import com.netflix.spinnaker.kork.actuator.observability.prometheus.PrometheusScrapeEndpoint;
import io.micrometer.core.instrument.Clock;
import org.junit.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class ExcludedMetricsPrefixIntegrationTest {

  @Test
  public void test_excluded_metrics_prefix_filters_metrics() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "spring.application.name=test-service",
                "observability.config.metrics.prometheus.enabled=true",
                "observability.config.metrics.prometheus.registry.excluded-metrics-prefix[0]=foo")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          ArmoryObservabilityCompositeRegistry composite =
              context.getBean(ArmoryObservabilityCompositeRegistry.class);
          PrometheusScrapeEndpoint endpoint = context.getBean(PrometheusScrapeEndpoint.class);

          composite.counter("foo_metric").increment();
          composite.counter("bar_metric").increment();

          String body = endpoint.scrape().getBody();
          assertNotNull(body);
          assertFalse(body.contains("foo_metric"));
          assertTrue(body.contains("bar_metric"));
        });
  }
}
