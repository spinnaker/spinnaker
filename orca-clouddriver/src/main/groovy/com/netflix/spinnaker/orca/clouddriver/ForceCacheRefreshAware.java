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
 */
package com.netflix.spinnaker.orca.clouddriver;

import com.google.common.base.CaseFormat;
import com.netflix.spinnaker.kork.dynamicconfig.DynamicConfigService;
import org.springframework.core.env.Environment;

/**
 * Provides convenience methods for stages that are aware of force cache refresh operations.
 *
 * <p>TODO(rz): This was ported out of StageDefinitionBuilder. There's no reason this should've ever
 * been on the interface to begin with. Convert to a utility class instead - it's strictly used
 * internally within a builder.
 */
public interface ForceCacheRefreshAware {

  default boolean isForceCacheRefreshEnabled(Environment environment) {
    try {
      return environment.getProperty(propertyName(), Boolean.class, true);
    } catch (Exception e) {
      return true;
    }
  }

  default boolean isForceCacheRefreshEnabled(DynamicConfigService dynamicConfigService) {
    try {
      return dynamicConfigService.isEnabled(propertyName(), true);
    } catch (Exception e) {
      return true;
    }
  }

  private String propertyName() {
    String className = getClass().getSimpleName();
    return String.format(
        "stages.%s.force-cache-refresh.enabled",
        CaseFormat.LOWER_CAMEL.to(
            CaseFormat.LOWER_HYPHEN,
            Character.toLowerCase(className.charAt(0)) + className.substring(1)));
  }
}
