/*
 * Copyright 2025 OpsMx, Inc.
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

package com.netflix.spinnaker.gate.security.oauth2;

import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.type.AnnotatedTypeMetadata;

@Slf4j
public class OAuthConfigEnabled implements Condition {
  private static final String SPRING_SECURITY_OAUTH2_REGEX =
      "spring\\.security\\.oauth2\\.client\\.registration\\..*\\.client-id";
  private static final Pattern SPRING_SECURITY_OAUTH2_PATTERN =
      Pattern.compile(SPRING_SECURITY_OAUTH2_REGEX);

  @Override
  public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
    if (!(context.getEnvironment() instanceof ConfigurableEnvironment)) {
      return false;
    }

    ConfigurableEnvironment env = (ConfigurableEnvironment) context.getEnvironment();

    for (PropertySource<?> propertySource : env.getPropertySources()) {
      if (propertySource instanceof EnumerablePropertySource) {
        for (String propertyName :
            ((EnumerablePropertySource<?>) propertySource).getPropertyNames()) {
          if (SPRING_SECURITY_OAUTH2_PATTERN.matcher(propertyName).matches()) {
            return true; // If any property matches, load the configuration
          }
        }
      }
    }
    return false; // Skip configuration if no matching properties found
  }
}
