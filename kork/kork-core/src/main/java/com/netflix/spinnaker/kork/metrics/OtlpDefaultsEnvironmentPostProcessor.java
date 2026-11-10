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

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.EnvironmentPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.DefaultPropertiesPropertySource;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;

/**
 * Turns OTLP metrics export and the OpenTelemetry SDK off unless configured otherwise.
 *
 * <p>Both are on by default in Spring Boot once their libraries are on the classpath, and export
 * would push to {@code localhost:4318} with no collector there. Enable export with {@code
 * management.otlp.metrics.export.enabled=true}; metrics export doesn't need the SDK.
 *
 * <p>The defaults are merged into Spring Boot's {@code defaultProperties} source without replacing
 * keys already there. A separate source would end up above {@code defaultProperties}, because Boot
 * moves that source to the end after post-processors run, and would override the defaults services
 * set through {@code SpringApplication} default properties.
 */
public class OtlpDefaultsEnvironmentPostProcessor implements EnvironmentPostProcessor {

  private static final Map<String, Object> DEFAULTS =
      Map.of(
          "management.otlp.metrics.export.enabled", "false",
          "management.opentelemetry.enabled", "false");

  @Override
  public void postProcessEnvironment(
      ConfigurableEnvironment environment, SpringApplication application) {
    MutablePropertySources sources = environment.getPropertySources();
    PropertySource<?> existing = sources.get(DefaultPropertiesPropertySource.NAME);

    Map<String, Object> merged = new HashMap<>(DEFAULTS);
    if (existing instanceof MapPropertySource map) {
      merged.putAll(map.getSource());
      sources.replace(
          DefaultPropertiesPropertySource.NAME, new DefaultPropertiesPropertySource(merged));
    } else if (existing == null) {
      sources.addLast(new DefaultPropertiesPropertySource(merged));
    }
  }
}
