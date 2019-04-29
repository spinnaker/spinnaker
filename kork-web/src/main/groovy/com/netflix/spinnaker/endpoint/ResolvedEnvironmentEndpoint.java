/*
 * Copyright 2019 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.netflix.spinnaker.endpoint;

import static java.lang.String.format;

import com.netflix.spinnaker.config.ResolvedEnvironmentConfigurationProperties;
import java.util.Arrays;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.boot.actuate.endpoint.Sanitizer;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.core.env.*;
import org.springframework.stereotype.Component;

@Component
@Endpoint(id = "resolvedEnv")
public class ResolvedEnvironmentEndpoint {

  private final Sanitizer sanitizer = new Sanitizer();
  private final Environment environment;

  public ResolvedEnvironmentEndpoint(
      Environment environment, ResolvedEnvironmentConfigurationProperties properties) {
    this.environment = environment;
    sanitizer.setKeysToSanitize(properties.getKeysToSanitize().toArray(new String[0]));
  }

  @ReadOperation
  public Map<String, Object> resolvedEnv() {
    return getPropertyKeys().stream()
        .collect(
            Collectors.toMap(
                property -> property,
                property -> {
                  try {
                    return sanitizer.sanitize(property, environment.getProperty(property));
                  } catch (Exception e) {
                    return format("Exception occurred: %s", e.getMessage());
                  }
                }));
  }

  /** This gathers all defined properties in the system (no matter the source) */
  private SortedSet<String> getPropertyKeys() {
    SortedSet<String> result = new TreeSet<>();
    MutablePropertySources sources;

    if (environment != null && environment instanceof ConfigurableEnvironment) {
      sources = ((ConfigurableEnvironment) environment).getPropertySources();
    } else {
      sources = new StandardEnvironment().getPropertySources();
    }

    sources.forEach(
        source -> {
          if (source instanceof EnumerablePropertySource) {
            result.addAll(Arrays.asList(((EnumerablePropertySource<?>) source).getPropertyNames()));
          }
        });

    return result;
  }
}
