/*
 * Copyright 2025 Netflix, Inc.
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
import io.micrometer.core.instrument.Tag;
import java.util.List;
import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class ArmoryRecommendedFiltersIntegrationTest {

  @Test
  public void test_recommended_filters_deny_controller_invocations_and_tags_are_snake_cased() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "spring.application.name=test-service",
                "observability.config.metrics.prometheus.enabled=true",
                // Enable Armory recommended filters for the Prometheus registry
                "observability.config.metrics.prometheus.registry.armory-recommended-filters-enabled=true")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          ArmoryObservabilityCompositeRegistry composite =
              context.getBean(ArmoryObservabilityCompositeRegistry.class);
          PrometheusScrapeEndpoint endpoint = context.getBean(PrometheusScrapeEndpoint.class);

          // This metric should be denied by the recommended filter
          composite.counter("controller.invocations.foo").increment();

          // Create a metric with a tag that requires normalization
          composite.counter("sample.metric", List.of(Tag.of("my.Tag", "myValue"))).increment();

          String body = endpoint.scrape().getBody();
          assertNotNull(body);

          // Verify the denied metric is not present
          assertFalse(body.contains("controller_invocations_foo"));

          // Verify snake_case normalization of tags in scrape output
          assertTrue(body.contains("my_Tag"));
          assertFalse(body.contains("my.Tag"));
        });
  }
}
