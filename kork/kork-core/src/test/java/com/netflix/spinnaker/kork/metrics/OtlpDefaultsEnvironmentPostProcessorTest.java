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

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.registry.otlp.OtlpConfig;
import io.micrometer.registry.otlp.OtlpMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;

class OtlpDefaultsEnvironmentPostProcessorTest {

  private static final String EXPORT_ENABLED = "management.otlp.metrics.export.enabled";
  private static final String SDK_ENABLED = "management.opentelemetry.enabled";

  /** Spring Boot's SDK with no exporters, registered when the OpenTelemetry SDK is disabled. */
  private static final String DISABLED_SDK_BEAN = "disabledOpenTelemetrySdk";

  @Test
  void otlpExportAndTheOpenTelemetrySdkAreOffByDefault() {
    try (ConfigurableApplicationContext context = minimal().run()) {
      assertThat(context.getEnvironment().getProperty(EXPORT_ENABLED)).isEqualTo("false");
      assertThat(context.getEnvironment().getProperty(SDK_ENABLED)).isEqualTo("false");
    }
  }

  @Test
  void applicationDefaultPropertiesWinAndAreKept() {
    try (ConfigurableApplicationContext context =
        minimal()
            .properties(Map.of(EXPORT_ENABLED, "true", "spring.application.name", "test-app"))
            .run()) {
      assertThat(context.getEnvironment().getProperty(EXPORT_ENABLED)).isEqualTo("true");
      assertThat(context.getEnvironment().getProperty(SDK_ENABLED)).isEqualTo("false");
      assertThat(context.getEnvironment().getProperty("spring.application.name"))
          .isEqualTo("test-app");
    }
  }

  @Test
  void applicationConfigFileWins() {
    try (ConfigurableApplicationContext context =
        minimal().run("--spring.config.location=classpath:/otlp-export-enabled.yml")) {
      assertThat(context.getEnvironment().getProperty(EXPORT_ENABLED)).isEqualTo("true");
    }
  }

  @Test
  void commandLineArgumentWins() {
    try (ConfigurableApplicationContext context = minimal().run("--" + EXPORT_ENABLED + "=true")) {
      assertThat(context.getEnvironment().getProperty(EXPORT_ENABLED)).isEqualTo("true");
    }
  }

  @Test
  void systemPropertyWins() {
    System.setProperty(EXPORT_ENABLED, "true");
    try (ConfigurableApplicationContext context = minimal().run()) {
      assertThat(context.getEnvironment().getProperty(EXPORT_ENABLED)).isEqualTo("true");
    } finally {
      System.clearProperty(EXPORT_ENABLED);
    }
  }

  @Test
  void fullAutoConfigurationStartsWithoutOtlpRegistryAndWithTheDisabledSdk() {
    try (ConfigurableApplicationContext context = autoConfigured().run()) {
      assertThat(context.getBeansOfType(OtlpMeterRegistry.class)).isEmpty();
      assertThat(context.getBeansOfType(OpenTelemetry.class)).containsOnlyKeys(DISABLED_SDK_BEAN);
      assertThat(context.getBean(MeterRegistry.class)).isInstanceOf(SimpleMeterRegistry.class);
    }
  }

  @Test
  void configuredOpenTelemetrySdkStartsWhenSwitchedOn() {
    try (ConfigurableApplicationContext context =
        autoConfigured().run("--" + SDK_ENABLED + "=true")) {
      assertThat(context.getBeansOfType(OpenTelemetry.class))
          .isNotEmpty()
          .doesNotContainKey(DISABLED_SDK_BEAN);
    }
  }

  @Test
  void exportSwitchedOnStartsTheOtlpRegistryWithTheServiceName() {
    try (ConfigurableApplicationContext context =
        autoConfigured()
            .run(
                "--" + EXPORT_ENABLED + "=true",
                "--management.otlp.metrics.export.url=http://localhost:1/v1/metrics",
                "--management.otlp.metrics.export.step=1h",
                "--spring.application.name=test-app")) {
      assertThat(context.getBean(MeterRegistry.class)).isInstanceOf(OtlpMeterRegistry.class);
      assertThat(context.getBean(OtlpConfig.class).resourceAttributes())
          .containsEntry("service.name", "test-app");
    }
  }

  private static SpringApplicationBuilder minimal() {
    return new SpringApplicationBuilder(MinimalApp.class).web(WebApplicationType.NONE);
  }

  private static SpringApplicationBuilder autoConfigured() {
    return new SpringApplicationBuilder(AutoConfiguredApp.class).web(WebApplicationType.NONE);
  }

  @Configuration(proxyBeanMethods = false)
  static class MinimalApp {}

  @Configuration(proxyBeanMethods = false)
  @EnableAutoConfiguration
  static class AutoConfiguredApp {}
}
