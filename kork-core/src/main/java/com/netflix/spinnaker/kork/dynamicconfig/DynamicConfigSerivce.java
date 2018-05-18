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

import javax.annotation.Nonnull;
import java.util.function.Supplier;

/**
 * A simple interface for interacting with dynamic Spring properties in the scope of feature flags.
 */
public interface DynamicConfigSerivce {

  <T> T getConfig(@Nonnull Class<T> configType, @Nonnull String configName, @Nonnull T defaultValue);

  default <T> T getConfig(@Nonnull Class<T> configType, @Nonnull String configName, @Nonnull T defaultValue, @Nonnull Supplier<Boolean> predicate) {
    if (predicate.get()) {
      return getConfig(configType, configName, defaultValue);
    }
    return defaultValue;
  }

  boolean isEnabled(@Nonnull String flagName, boolean defaultValue);

  default boolean isEnabled(@Nonnull String flagName, boolean defaultValue, @Nonnull Supplier<Boolean> predicate) {
    if (predicate.get()) {
      return isEnabled(flagName, defaultValue);
    }
    return defaultValue;
  }

  boolean isEnabled(@Nonnull String flagName, boolean defaultValue, @Nonnull ScopedCriteria criteria);
}
