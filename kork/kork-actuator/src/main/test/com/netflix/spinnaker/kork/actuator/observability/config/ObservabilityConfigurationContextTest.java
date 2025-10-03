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

package com.netflix.spinnaker.kork.actuator.observability.config;

import static org.junit.Assert.*;

import com.netflix.spinnaker.kork.actuator.observability.model.ObservabilityConfigurationProperties;
import com.netflix.spinnaker.kork.actuator.observability.prometheus.MutatedPrometheusMeterRegistry;
import com.netflix.spinnaker.kork.actuator.observability.prometheus.PrometheusScrapeEndpoint;
import com.netflix.spinnaker.kork.actuator.observability.registry.ArmoryObservabilityCompositeRegistry;
import com.netflix.spinnaker.kork.actuator.observability.service.TagsService;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Tag;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class ObservabilityConfigurationContextTest {

  @Test
  public void test_beans_are_created_when_observability_is_enabled() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true", "spring.application.name=test-service")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          assertNotNull(context.getBean(ObservabilityConfigurationProperties.class));
          assertNotNull(context.getBean(TagsService.class));
          assertNotNull(context.getBean(PrometheusScrapeEndpoint.class));
          assertNotNull(context.getBean(ArmoryObservabilityCompositeRegistry.class));
        });
  }

  @Test
  public void test_prometheus_registry_is_enabled_by_property() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "observability.config.metrics.prometheus.enabled=true",
                "spring.application.name=test-service")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          ArmoryObservabilityCompositeRegistry composite =
              context.getBean(ArmoryObservabilityCompositeRegistry.class);
          boolean hasPrometheus =
              composite.getRegistries().stream()
                  .anyMatch(r -> r instanceof MutatedPrometheusMeterRegistry);
          assertTrue("expected a Prometheus registry when enabled", hasPrometheus);
        });
  }

  @Test
  public void test_fallback_to_simple_registry_when_all_disabled() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                // leave all metrics.*.enabled false (defaults)
                "spring.application.name=test-service")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          ArmoryObservabilityCompositeRegistry composite =
              context.getBean(ArmoryObservabilityCompositeRegistry.class);
          // When all registries are disabled, the composite should still contain exactly one
          // registry
          // (SimpleMeterRegistry). We cannot refer to SimpleMeterRegistry class from here without
          // importing, so assert size == 1 as a contract and that it's not a Prometheus registry.
          assertEquals(1, composite.getRegistries().size());
          assertFalse(
              composite.getRegistries().stream()
                  .anyMatch(r -> r instanceof MutatedPrometheusMeterRegistry));
        });
  }

  @Test
  public void test_default_tags_include_application_and_lib() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues("observability.enabled=true", "spring.application.name=my-app")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          TagsService tagsService = context.getBean(TagsService.class);
          Map<String, String> tagsAsMap =
              tagsService.getDefaultTags().stream()
                  .collect(Collectors.toMap(Tag::getKey, Tag::getValue, (a, b) -> a));
          assertEquals("my-app", tagsAsMap.get(TagsService.SPIN_SVC));
          assertEquals("aop", tagsAsMap.get(TagsService.LIB));
          // version tag may be resolved or not; presence is optional in unit env
        });
  }

  @Test
  public void test_datadog_registry_is_enabled_by_property() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "spring.application.name=test-service",
                "observability.config.metrics.datadog.enabled=true",
                // required by DataDogRegistryConfig.apiKey()
                "observability.config.metrics.datadog.api-key=TEST_API_KEY")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          ArmoryObservabilityCompositeRegistry composite =
              context.getBean(ArmoryObservabilityCompositeRegistry.class);
          boolean hasDatadog =
              composite.getRegistries().stream()
                  .anyMatch(r -> r instanceof io.micrometer.datadog.DatadogMeterRegistry);
          assertTrue("expected a Datadog registry when enabled", hasDatadog);
        });
  }

  @Test
  public void test_newrelic_registry_is_enabled_by_property() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "spring.application.name=test-service",
                "observability.config.metrics.newrelic.enabled=true",
                // required by NewRelicRegistryConfig.apiKey()
                "observability.config.metrics.newrelic.api-key=TEST_API_KEY")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          ArmoryObservabilityCompositeRegistry composite =
              context.getBean(ArmoryObservabilityCompositeRegistry.class);
          boolean hasNewRelic =
              composite.getRegistries().stream()
                  .anyMatch(r -> r.getClass().getName().contains("NewRelicRegistry"));
          assertTrue("expected a New Relic registry when enabled", hasNewRelic);
        });
  }

  @Test
  public void test_observability_disabled_wires_no_beans() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues("observability.enabled=false")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          assertFalse(context.containsBean("observabilityConfigurationProperties"));
          assertFalse(context.containsBean("prometheusScrapeEndpoint"));
          assertFalse(context.containsBean("armoryObservabilityCompositeRegistry"));
        });
  }

  @Test
  public void test_multiple_registries_enabled_composes_both() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "spring.application.name=test-service",
                "observability.config.metrics.prometheus.enabled=true",
                "observability.config.metrics.datadog.enabled=true",
                "observability.config.metrics.datadog.api-key=TEST_API_KEY")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          ArmoryObservabilityCompositeRegistry composite =
              context.getBean(ArmoryObservabilityCompositeRegistry.class);
          boolean hasProm =
              composite.getRegistries().stream()
                  .anyMatch(
                      r ->
                          r
                              instanceof
                              com.netflix.spinnaker.kork.actuator.observability.prometheus
                                  .MutatedPrometheusMeterRegistry);
          boolean hasDd =
              composite.getRegistries().stream()
                  .anyMatch(r -> r instanceof io.micrometer.datadog.DatadogMeterRegistry);
          assertTrue(hasProm && hasDd);
        });
  }
}
