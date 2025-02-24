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
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertiesPropertySource;

public class DatadogMetricsPostProcessor implements EnvironmentPostProcessor {
  private static final String PROPERTY_SOURCE_NAME = "datadog-metrics-defaults";

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, org.springframework.boot.SpringApplication application) {
    MutablePropertySources propertySources = environment.getPropertySources();
    Properties props = new Properties();

    boolean datadogEnabled =
        Boolean.parseBoolean(
            environment.getProperty("observability.config.metrics.datadog", "false"));

    // Ensure management.metrics.export.datadog.enabled is set before Spring interprets it
    props.setProperty("management.metrics.export.datadog.enabled", String.valueOf(datadogEnabled));

    propertySources.addFirst(new PropertiesPropertySource(PROPERTY_SOURCE_NAME, props));
  }
}
