/*
 * Copyright 2016 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License")
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

package org.springframework.boot.actuate.endpoint

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.env.ConfigurableEnvironment
import org.springframework.core.env.EnumerablePropertySource
import org.springframework.core.env.Environment
import org.springframework.core.env.MutablePropertySources
import org.springframework.core.env.StandardEnvironment

@ConfigurationProperties(prefix = "endpoints.resolvedEnv")
class ResolvedEnvironmentEndpoint extends AbstractEndpoint<Map<String, Object>> {

  private final Sanitizer sanitizer = new Sanitizer();

  public ResolvedEnvironmentEndpoint() {
    super("resolvedEnv")
  }

  @Override
  Map<String, Object> invoke() {
    Environment environment = getEnvironment()
    return getPropertyKeys().collectEntries {
      try {
        [(it): sanitizer.sanitize(it, environment.getProperty(it))]
      } catch (Exception e) {
        [(it): "Exception occurred: " + e.getMessage()]
      }
    }
  }

  /**
   * Impl partially copied from
   * {@link org.springframework.boot.actuate.endpoint.EnvironmentEndpoint}
   *
   * This gathers all defined properties in the system (no matter the source)
   */
  private SortedSet<String> getPropertyKeys() {
    SortedSet<String> result = new TreeSet<String>()
    Environment environment = getEnvironment()

    MutablePropertySources sources
    if (environment && environment instanceof ConfigurableEnvironment) {
      sources = ((ConfigurableEnvironment) environment).getPropertySources()
    } else {
      sources = new StandardEnvironment().getPropertySources()
    }

    sources.each {
      if (it instanceof EnumerablePropertySource) {
        ((EnumerablePropertySource) it).propertyNames.each {
          result.add(it)
        }
      }
    }

    return result
  }

  public void setKeysToSanitize(String... keysToSanitize) {
    this.sanitizer.setKeysToSanitize(keysToSanitize);
  }
}
