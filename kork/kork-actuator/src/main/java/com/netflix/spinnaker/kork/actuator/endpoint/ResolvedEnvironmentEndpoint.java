/*
 * Copyright 2020 Netflix, Inc.
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
 *
 */
package com.netflix.spinnaker.kork.actuator.endpoint;

import static java.lang.String.format;

import com.netflix.spinnaker.kork.actuator.ActuatorSanitizingFunction;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.Sanitizer;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.core.env.*;

@Endpoint(id = "resolvedEnv")
public class ResolvedEnvironmentEndpoint {

  private final Sanitizer sanitizer;
  private final ActuatorSanitizingFunction actuatorSanitizingFunction =
      new ActuatorSanitizingFunction();
  private final Environment environment;

  @Autowired
  public ResolvedEnvironmentEndpoint(
      Environment environment, ResolvedEnvironmentConfigurationProperties properties) {
    this.environment = environment;
    Optional.ofNullable(properties.getKeysToSanitize())
        .map(p -> p.toArray(new String[0]))
        .ifPresent(actuatorSanitizingFunction::setKeysToSanitize);
    sanitizer = new Sanitizer(List.of(actuatorSanitizingFunction));
  }

  @ReadOperation
  public Map<String, Object> resolvedEnv() {
    return getPropertyKeys().stream()
        .collect(
            Collectors.toMap(
                property -> property,
                property -> {
                  try {
                    return sanitizer.sanitize(
                        new SanitizableData(null, property, environment.getProperty(property)),
                        true);
                  } catch (Exception e) {
                    return format("Exception occurred: %s", e.getMessage());
                  }
                }));
  }

  /** This gathers all defined properties in the system (no matter the source) */
  private SortedSet<String> getPropertyKeys() {
    SortedSet<String> result = new TreeSet<>();
    MutablePropertySources sources;

    if (environment instanceof ConfigurableEnvironment) {
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
