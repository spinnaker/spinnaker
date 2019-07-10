/*
 * Copyright 2019 Pivotal, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.spinnaker.kork.boot;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class DefaultPropertiesBuilder {
  private final Map<String, Object> defaults;

  public DefaultPropertiesBuilder() {
    defaults = new HashMap<>();
    defaults.put("netflix.environment", "test");
    defaults.put("netflix.account", "${netflix.environment}");
    defaults.put("netflix.stack", "test");
    defaults.put("spring.config.name", "spinnaker,${spring.application.name}");
    defaults.put("spring.config.additional-location", "${user.home}/.spinnaker/");
    defaults.put("spring.profiles.active", "${netflix.environment},local");
    // add the Spring Cloud Config "composite" profile to default to a configuration
    // source that won't prevent app startup if custom configuration is not provided
    defaults.put("spring.profiles.include", "composite");
  }

  public DefaultPropertiesBuilder property(String key, Object value) {
    defaults.put(key, value);
    return this;
  }

  public Map<String, Object> build() {
    return Collections.unmodifiableMap(defaults);
  }
}
