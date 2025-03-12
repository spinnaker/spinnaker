/*
 * Copyright 2018 Netflix, Inc.
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
package com.netflix.spinnaker.kork.dynamicconfig;

import static java.lang.String.format;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.function.Supplier;
import javax.annotation.Nonnull;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;

/** The SpringDynamicConfigService directly interacts with the Spring Environment. */
public class SpringDynamicConfigService implements DynamicConfigService, EnvironmentAware {

  private Environment environment;

  @Override
  public <T> T getConfig(
      @Nonnull Class<T> configType, @Nonnull String configName, @Nonnull T defaultValue) {
    if (environment == null) {
      return defaultValue;
    }
    return environment.getProperty(configName, configType, defaultValue);
  }

  @Override
  public boolean isEnabled(@Nonnull String flagName, boolean defaultValue) {
    if (environment == null) {
      return defaultValue;
    }
    return environment.getProperty(flagPropertyName(flagName), Boolean.class, defaultValue);
  }

  @Override
  public boolean isEnabled(
      @Nonnull String flagName, boolean defaultValue, @Nonnull ScopedCriteria criteria) {
    if (environment == null) {
      return defaultValue;
    }
    Boolean value =
        chainedFlagSupplier(
            new LinkedList<>(
                Arrays.asList(
                    booleanSupplier(flagName, "region", criteria.region),
                    booleanSupplier(flagName, "account", criteria.account),
                    booleanSupplier(flagName, "cloudProvider", criteria.cloudProvider),
                    booleanSupplier(flagName, "application", criteria.application),
                    () -> environment.getProperty(flagPropertyName(flagName), Boolean.class))));
    return (value == null) ? defaultValue : value;
  }

  private Boolean chainedFlagSupplier(LinkedList<Supplier<Boolean>> chain) {
    if (chain.isEmpty()) {
      return null;
    }
    Boolean value = chain.removeFirst().get();
    return (value != null) ? value : chainedFlagSupplier(chain);
  }

  private Supplier<Boolean> booleanSupplier(
      String configName, String criteriaName, String criteria) {
    return () ->
        (configName == null)
            ? null
            : environment.getProperty(
                format("%s.%s.%s", configName, criteriaName, criteria), Boolean.class);
  }

  @Override
  public void setEnvironment(Environment environment) {
    this.environment = environment;
  }

  private static String flagPropertyName(String flagName) {
    return flagName.endsWith(".enabled") ? flagName : format("%s.enabled", flagName);
  }
}
