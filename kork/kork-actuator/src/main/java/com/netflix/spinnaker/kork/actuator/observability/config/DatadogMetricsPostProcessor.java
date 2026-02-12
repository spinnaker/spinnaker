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

import java.util.Properties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

public class DatadogMetricsPostProcessor implements EnvironmentPostProcessor {
  private static final String PROPERTY_SOURCE_NAME = "datadog-metrics-defaults";
  private static final String OBSERVABILITY_ENABLED = "observability.enabled";
  private static final String OBSERVABILITY_DATADOG_ENABLED =
      "observability.config.metrics.datadog.enabled";
  private static final String OBSERVABILITY_OVERRIDE_PRIMARY =
      "observability.config.override-primary-registry";
  private static final String MANAGEMENT_DATADOG_ENABLED =
      "management.metrics.export.datadog.enabled";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    // No-op unless observability is explicitly enabled.
    if (!Boolean.parseBoolean(environment.getProperty(OBSERVABILITY_ENABLED, "false"))) {
      return;
    }

    // Respect explicit management.metrics settings from the application/operator.
    if (environment.containsProperty(MANAGEMENT_DATADOG_ENABLED)) {
      return;
    }

    MutablePropertySources propertySources = environment.getPropertySources();
    Properties props = new Properties();

    boolean datadogEnabled =
        Boolean.parseBoolean(environment.getProperty(OBSERVABILITY_DATADOG_ENABLED, "false"));

    // If the composite registry override is active (default), ensure Boot's Datadog exporter is
    // disabled to avoid duplicate registries/exports. When explicitly opting out of the composite,
    // mirror the observability Datadog enable flag to Boot's property so Boot can own the exporter.
    boolean overridePrimary =
        Boolean.parseBoolean(environment.getProperty(OBSERVABILITY_OVERRIDE_PRIMARY, "true"));

    boolean bootDatadogEnabled = !overridePrimary && datadogEnabled;

    // Ensure management.metrics.export.datadog.enabled is set before Spring interprets it
    props.setProperty(MANAGEMENT_DATADOG_ENABLED, String.valueOf(bootDatadogEnabled));

    propertySources.addFirst(new PropertiesPropertySource(PROPERTY_SOURCE_NAME, props));
  }
}
