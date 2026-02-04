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
import com.netflix.spinnaker.kork.actuator.observability.registry.ObservabilityCompositeRegistry;
import com.netflix.spinnaker.kork.actuator.observability.service.TagsService;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.datadog.DatadogMeterRegistry;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.datadog.DatadogMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

public class ObservabilityConfigurationContextTest {

  @Test
  public void test_beans_are_created_when_observability_is_enabled() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class))
            .withUserConfiguration(
                ObservabilityConfiguration.class,
                ObservabilityConfiguration.PrometheusScrapeEndpointConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "observability.config.metrics.prometheus.enabled=true",
                "spring.application.name=test-service")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          assertNotNull(context.getBean(ObservabilityConfigurationProperties.class));
          assertNotNull(context.getBean(TagsService.class));
          assertNotNull(context.getBean(PrometheusScrapeEndpoint.class));
          assertNotNull(context.getBean(ObservabilityCompositeRegistry.class));
        });
  }

  @Test
  public void test_boot_datadog_registry_exists_when_composite_opted_out() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class,
                    DatadogMetricsExportAutoConfiguration.class))
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "spring.application.name=test-service",
                // opt-out of composite so Boot can own the registries
                "observability.config.override-primary-registry=false",
                // enable provider-level Datadog
                "observability.config.metrics.datadog.enabled=true",
                // ApplicationContextRunner does not invoke EnvironmentPostProcessors, so emulate
                // the effect of DatadogMetricsPostProcessor here
                "management.metrics.export.datadog.enabled=true",
                "management.metrics.export.datadog.api-key=TEST_API_KEY")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          // Our composite should not be present
          assertFalse(context.containsBean("observabilityCompositeRegistry"));

          // Boot should provide Datadog registry bean
          DatadogMeterRegistry dd = context.getBean(DatadogMeterRegistry.class);
          assertNotNull(dd);

          // Boot should provide a MeterRegistry for injection; depending on Boot version,
          // this may be a CompositeMeterRegistry or the concrete Datadog registry
          MeterRegistry injected = context.getBean(MeterRegistry.class);
          assertNotNull(injected);
          // Do not assert exact type to avoid Boot-version-specific behavior
        });
  }

  @Test
  public void test_boot_prometheus_registry_exists_when_composite_opted_out() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class,
                    PrometheusMetricsExportAutoConfiguration.class))
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "spring.application.name=test-service",
                // opt-out of composite so Boot can own the registries
                "observability.config.override-primary-registry=false",
                // enable provider-level Prometheus
                "observability.config.metrics.prometheus.enabled=true",
                // Explicitly enable Boot's Prometheus exporter in this test
                "management.metrics.export.prometheus.enabled=true")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          // Our composite should not be present
          assertFalse(context.containsBean("observabilityCompositeRegistry"));

          // Boot should provide Prometheus registry bean
          PrometheusMeterRegistry prom = context.getBean(PrometheusMeterRegistry.class);
          assertNotNull(prom);

          // Boot should provide a MeterRegistry for injection; depending on Boot version,
          // this may be a CompositeMeterRegistry or the concrete Prometheus registry
          MeterRegistry injected = context.getBean(MeterRegistry.class);
          assertNotNull(injected);
          // Do not assert exact type to avoid Boot-version-specific behavior
        });
  }

  @Test
  public void test_composite_disabled_when_override_primary_registry_false() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class))
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "spring.application.name=test-service",
                "observability.config.override-primary-registry=false")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          // Composite registry should not be present when override is explicitly false
          assertFalse(context.containsBean("observabilityCompositeRegistry"));
          // Other observability beans should still be wired
          assertNotNull(context.getBean(ObservabilityConfigurationProperties.class));
          assertNotNull(context.getBean(TagsService.class));
        });
  }

  @Test
  public void test_prometheus_registry_is_enabled_by_property() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class))
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "observability.config.metrics.prometheus.enabled=true",
                "spring.application.name=test-service")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          ObservabilityCompositeRegistry composite =
              context.getBean(ObservabilityCompositeRegistry.class);
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
            .withConfiguration(
                AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class))
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                // leave all metrics.*.enabled false (defaults)
                "spring.application.name=test-service")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          ObservabilityCompositeRegistry composite =
              context.getBean(ObservabilityCompositeRegistry.class);
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
            .withConfiguration(
                AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class))
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
          assertEquals("kork-observability", tagsAsMap.get(TagsService.LIB));
          // version tag may be resolved or not; presence is optional in unit env
        });
  }

  @Test
  public void test_datadog_registry_is_enabled_by_property() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class))
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
          ObservabilityCompositeRegistry composite =
              context.getBean(ObservabilityCompositeRegistry.class);
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
            .withConfiguration(
                AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class))
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
          ObservabilityCompositeRegistry composite =
              context.getBean(ObservabilityCompositeRegistry.class);
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
          assertFalse(context.containsBean("observabilityCompositeRegistry"));
        });
  }

  @Test
  public void test_multiple_registries_enabled_composes_both() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class))
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
          ObservabilityCompositeRegistry composite =
              context.getBean(ObservabilityCompositeRegistry.class);
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

  @Test
  public void test_prometheus_endpoint_not_created_when_prometheus_disabled() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class))
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "spring.application.name=test-service",
                "observability.config.metrics.prometheus.enabled=false",
                "observability.config.metrics.datadog.enabled=true",
                "observability.config.metrics.datadog.api-key=TEST_API_KEY")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          assertFalse(
              "PrometheusScrapeEndpoint should not be created when prometheus is disabled",
              context.containsBean("prometheusScrapeEndpoint"));
          assertFalse(
              "CollectorRegistry should not be created when prometheus is disabled",
              context.containsBean("collectorRegistry"));
        });
  }

  @Test
  public void test_prometheus_endpoint_created_when_prometheus_enabled() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class))
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "spring.application.name=test-service",
                "observability.config.metrics.prometheus.enabled=true")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          assertTrue(
              "PrometheusScrapeEndpoint should be created when prometheus is enabled",
              context.containsBean("prometheusScrapeEndpoint"));
          assertTrue(
              "CollectorRegistry should be created when prometheus is enabled",
              context.containsBean("collectorRegistry"));
        });
  }

  @Test
  public void test_prometheus_endpoint_not_created_when_override_primary_registry_false() {
    ApplicationContextRunner runner =
        new ApplicationContextRunner()
            .withConfiguration(
                AutoConfigurations.of(
                    ConfigurationPropertiesAutoConfiguration.class,
                    MetricsAutoConfiguration.class,
                    CompositeMeterRegistryAutoConfiguration.class))
            .withUserConfiguration(ObservabilityConfiguration.class)
            .withPropertyValues(
                "observability.enabled=true",
                "spring.application.name=test-service",
                "observability.config.metrics.prometheus.enabled=true",
                "observability.config.override-primary-registry=false")
            .withBean(Clock.class, () -> Clock.SYSTEM);

    runner.run(
        context -> {
          // Endpoint should not be created when override-primary-registry is false
          // to avoid collision with Spring Boot's prometheus endpoint
          assertFalse(
              "PrometheusScrapeEndpoint should not be created when override-primary-registry=false",
              context.containsBean("prometheusScrapeEndpoint"));
          // CollectorRegistry and supplier are still created for prometheus to work
          assertTrue(
              "CollectorRegistry should still be created",
              context.containsBean("collectorRegistry"));
        });
  }
}
