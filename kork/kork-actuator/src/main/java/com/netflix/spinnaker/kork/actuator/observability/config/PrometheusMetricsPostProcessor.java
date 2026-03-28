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

public class PrometheusMetricsPostProcessor implements EnvironmentPostProcessor {
  private static final String PROPERTY_SOURCE_NAME = "prometheus-metrics-defaults";
  private static final String OBSERVABILITY_ENABLED = "observability.enabled";
  private static final String OBSERVABILITY_PROMETHEUS_ENABLED =
      "observability.config.metrics.prometheus.enabled";
  private static final String OBSERVABILITY_OVERRIDE_PRIMARY =
      "observability.config.override-primary-registry";
  private static final String MANAGEMENT_PROMETHEUS_ENABLED =
      "management.metrics.export.prometheus.enabled";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    // Respect explicit management.metrics settings from the application/operator.
    if (environment.containsProperty(MANAGEMENT_PROMETHEUS_ENABLED)) {
      return;
    }

    MutablePropertySources propertySources = environment.getPropertySources();
    Properties props = new Properties();

    // When observability is not enabled, explicitly disable Boot's Prometheus exporter to prevent
    // auto-configuration from activating just because the micrometer-registry-prometheus JAR is on
    // the classpath (it ships transitively via kork-actuator).
    if (!Boolean.parseBoolean(environment.getProperty(OBSERVABILITY_ENABLED, "false"))) {
      props.setProperty(MANAGEMENT_PROMETHEUS_ENABLED, "false");
      propertySources.addFirst(new PropertiesPropertySource(PROPERTY_SOURCE_NAME, props));
      return;
    }

    boolean promEnabled =
        Boolean.parseBoolean(environment.getProperty(OBSERVABILITY_PROMETHEUS_ENABLED, "false"));

    boolean overridePrimary =
        Boolean.parseBoolean(environment.getProperty(OBSERVABILITY_OVERRIDE_PRIMARY, "true"));

    boolean bootPrometheusEnabled = !overridePrimary && promEnabled;

    props.setProperty(MANAGEMENT_PROMETHEUS_ENABLED, String.valueOf(bootPrometheusEnabled));

    propertySources.addFirst(new PropertiesPropertySource(PROPERTY_SOURCE_NAME, props));
  }
}
