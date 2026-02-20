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

import static org.junit.Assert.assertEquals;

import java.util.Properties;
import org.junit.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.PropertiesPropertySource;
import org.springframework.core.env.StandardEnvironment;

public class DatadogMetricsPostProcessorTest {

  @Test
  public void setsManagementPropertyWhenEnabledTrueWithCompositeOptOut() {
    ConfigurableEnvironment env = new StandardEnvironment();
    Properties p = new Properties();
    p.setProperty("observability.enabled", "true");
    p.setProperty("observability.config.metrics.datadog.enabled", "true");
    // Opt-out from composite so Boot can own the exporter
    p.setProperty("observability.config.override-primary-registry", "false");
    env.getPropertySources().addFirst(new PropertiesPropertySource("test", p));

    new DatadogMetricsPostProcessor()
        .postProcessEnvironment(env, new SpringApplication(Object.class));

    assertEquals("true", env.getProperty("management.metrics.export.datadog.enabled"));
  }

  @Test
  public void setsManagementPropertyDisabledWhenCompositeOverrideEnabled() {
    ConfigurableEnvironment env = new StandardEnvironment();
    Properties p = new Properties();
    p.setProperty("observability.enabled", "true");
    p.setProperty("observability.config.metrics.datadog.enabled", "true");
    // Default is override-primary-registry=true; set explicitly for clarity
    p.setProperty("observability.config.override-primary-registry", "true");
    env.getPropertySources().addFirst(new PropertiesPropertySource("test", p));

    new DatadogMetricsPostProcessor()
        .postProcessEnvironment(env, new SpringApplication(Object.class));

    assertEquals("false", env.getProperty("management.metrics.export.datadog.enabled"));
  }

  @Test
  public void setsManagementPropertyWhenEnabledFalse() {
    ConfigurableEnvironment env = new StandardEnvironment();
    Properties p = new Properties();
    p.setProperty("observability.enabled", "true");
    p.setProperty("observability.config.metrics.datadog.enabled", "false");
    env.getPropertySources().addFirst(new PropertiesPropertySource("test", p));

    new DatadogMetricsPostProcessor()
        .postProcessEnvironment(env, new SpringApplication(Object.class));

    assertEquals("false", env.getProperty("management.metrics.export.datadog.enabled"));
  }

  @Test
  public void setsManagementPropertyToFalseWhenProviderFlagMissing() {
    ConfigurableEnvironment env = new StandardEnvironment();
    Properties p = new Properties();
    p.setProperty("observability.enabled", "true");
    env.getPropertySources().addFirst(new PropertiesPropertySource("test", p));

    new DatadogMetricsPostProcessor()
        .postProcessEnvironment(env, new SpringApplication(Object.class));

    assertEquals("false", env.getProperty("management.metrics.export.datadog.enabled"));
  }

  @Test
  public void disablesBootExporterWhenObservabilityDisabled() {
    ConfigurableEnvironment env = new StandardEnvironment();

    new DatadogMetricsPostProcessor()
        .postProcessEnvironment(env, new SpringApplication(Object.class));

    assertEquals("false", env.getProperty("management.metrics.export.datadog.enabled"));
  }

  @Test
  public void keepsExplicitManagementPropertyValueWhenAlreadyConfigured() {
    ConfigurableEnvironment env = new StandardEnvironment();
    Properties p = new Properties();
    p.setProperty("observability.enabled", "true");
    p.setProperty("observability.config.metrics.datadog.enabled", "false");
    p.setProperty("management.metrics.export.datadog.enabled", "true");
    env.getPropertySources().addFirst(new PropertiesPropertySource("test", p));

    new DatadogMetricsPostProcessor()
        .postProcessEnvironment(env, new SpringApplication(Object.class));

    assertEquals("true", env.getProperty("management.metrics.export.datadog.enabled"));
  }
}
