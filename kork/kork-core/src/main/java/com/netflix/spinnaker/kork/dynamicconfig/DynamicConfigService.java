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

import java.util.function.Supplier;
import javax.annotation.Nonnull;

/**
 * A simple interface for interacting with dynamic Spring properties in the scope of feature flags.
 */
public interface DynamicConfigService {

  /**
   * A noop implementation of DynamicConfigService that just falls back to default values.
   *
   * <p>Primarily useful as a default when an alternative implementation is not available or in unit
   * tests or other scenarios where dynamic configuration is not important.
   */
  class NoopDynamicConfig implements DynamicConfigService {
    @Override
    public <T> T getConfig(
        @Nonnull Class<T> configType, @Nonnull String configName, @Nonnull T defaultValue) {
      return defaultValue;
    }

    @Override
    public boolean isEnabled(@Nonnull String flagName, boolean defaultValue) {
      return defaultValue;
    }

    @Override
    public boolean isEnabled(
        @Nonnull String flagName, boolean defaultValue, @Nonnull ScopedCriteria criteria) {
      return defaultValue;
    }
  }

  DynamicConfigService NOOP = new NoopDynamicConfig();

  <T> T getConfig(
      @Nonnull Class<T> configType, @Nonnull String configName, @Nonnull T defaultValue);

  default <T> T getConfig(
      @Nonnull Class<T> configType,
      @Nonnull String configName,
      @Nonnull T defaultValue,
      @Nonnull Supplier<Boolean> predicate) {
    if (predicate.get()) {
      return getConfig(configType, configName, defaultValue);
    }
    return defaultValue;
  }

  boolean isEnabled(@Nonnull String flagName, boolean defaultValue);

  default boolean isEnabled(
      @Nonnull String flagName, boolean defaultValue, @Nonnull Supplier<Boolean> predicate) {
    if (predicate.get()) {
      return isEnabled(flagName, defaultValue);
    }
    return defaultValue;
  }

  boolean isEnabled(
      @Nonnull String flagName, boolean defaultValue, @Nonnull ScopedCriteria criteria);
}
