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

package com.netflix.spinnaker.kork.archaius;

import com.netflix.config.PollResult;
import com.netflix.config.PolledConfigurationSource;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;

public class SpringEnvironmentPolledConfigurationSource implements PolledConfigurationSource {

  private final ConfigurableEnvironment environment;

  public SpringEnvironmentPolledConfigurationSource(ConfigurableEnvironment environment) {
    this.environment = Objects.requireNonNull(environment, "environment");
  }

  @Override
  public PollResult poll(boolean initial, Object checkPoint) throws Exception {
    Map<String, Object> result = new HashMap<>();
    environment
        .getPropertySources()
        .iterator()
        .forEachRemaining(
            source -> {
              if (source instanceof EnumerablePropertySource) {
                EnumerablePropertySource<?> enumerable = (EnumerablePropertySource<?>) source;
                for (String key : enumerable.getPropertyNames()) {
                  result.putIfAbsent(key, enumerable.getProperty(key));
                }
              }
            });
    return PollResult.createFull(result);
  }
}
