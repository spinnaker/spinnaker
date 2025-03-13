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

/**
 * This class implements the {@link Condition} interface to check if OAuth2 configuration is enabled
 * in the Spring environment based on the presence of a client ID property. It inspects the
 * environment's property sources for any property matching the regular expression for OAuth2 client
 * registration.
 *
 * <p>The condition matches if there is any property key that matches the pattern:
 * "spring.security.oauth2.client.registration.<client-name>.client-id". If such a property exists,
 * the condition returns {@code true}, indicating that OAuth2 configuration is enabled. Otherwise,
 * it returns {@code false}.
 *
 * <p>This condition can be used in Spring's {@link
 * org.springframework.context.annotation.Conditional} annotations to conditionally enable or
 * disable beans based on the presence of OAuth2 client properties in the application's
 * configuration.
 *
 * <p>Example:
 *
 * <pre>
 * &#64;Configuration
 * &#64;Conditional(OAuthConfigEnabled.class)
 * public class OAuth2SsoConfig {
 *     // Bean definitions for OAuth2 configuration
 * }
 * </pre>
 *
 * <p>Note: The condition looks for the client ID property in the environment, which is a standard
 * property for configuring OAuth2 client registration in Spring Security.
 */
@Slf4j
public class OAuthConfigEnabled implements Condition {
  private static final String SPRING_SECURITY_OAUTH2_REGEX =
      "spring\\.security\\.oauth2\\.client\\.registration\\..*\\.client-id";
  private static final Pattern SPRING_SECURITY_OAUTH2_PATTERN =
      Pattern.compile(SPRING_SECURITY_OAUTH2_REGEX);

  /**
   * Evaluates whether the condition matches based on the presence of OAuth2 client registration
   * properties in the Spring environment.
   *
   * <p>This method checks if the application's {@link ConfigurableEnvironment} contains any
   * property names that match the pattern <code>
   * spring.security.oauth2.client.registration.&lt;client-name&gt;.client-id</code>. If at least
   * one such property exists, the method returns {@code true}, indicating that OAuth2 configuration
   * is enabled. Otherwise, it returns {@code false}.
   *
   * @param context The {@link ConditionContext}, which provides access to the Spring environment
   *     and application context.
   * @param metadata The {@link AnnotatedTypeMetadata} of the annotated component. (Not used in this
   *     implementation.)
   * @return {@code true} if at least one OAuth2 client registration property is found, {@code
   *     false} otherwise.
   */
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
