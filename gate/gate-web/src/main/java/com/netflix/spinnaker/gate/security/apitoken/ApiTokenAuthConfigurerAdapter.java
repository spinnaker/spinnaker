/*
 * Copyright 2026 DoorDash, Inc.
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

package com.netflix.spinnaker.gate.security.apitoken;

import com.netflix.spinnaker.gate.config.AuthConfig;
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Dedicated Spring Security chain for API-token requests: builds a {@link
 * SessionCreationPolicy#STATELESS STATELESS} chain whose security matcher only matches Spinnaker
 * token-bearing requests, so token auth never writes to {@code HttpSession} and non-token requests
 * fall through to whichever SSO chain is configured.
 *
 * <p>Ordered ahead of the SSO/header/x509 chains so a token-bearing request is always handled by
 * this chain, even in mixed-mode deployments.
 */
@ConditionalOnProperty("api-tokens.enabled")
@SpinnakerAuthConfig
@EnableWebSecurity
@Configuration
public class ApiTokenAuthConfigurerAdapter {

  @Autowired AuthConfig authConfig;
  @Autowired ApiTokenAuthenticationFilter apiTokenAuthenticationFilter;
  @Autowired ApiTokenProperties properties;

  @Bean
  // Run before HeaderAuthConfigurerAdapter (@Order(2)) and X509Config (@Order(3)) so
  // token-bearing requests are claimed by this chain in mixed-mode deployments.
  @Order(0)
  SecurityFilterChain apiTokenSecurityFilterChain(HttpSecurity http) throws Exception {
    // AuthConfig.configure() installs the default permit/authenticated rules and the
    // requestMatcherProvider's matcher (AnyRequestMatcher by default). Narrow that matcher to
    // only token-bearing requests so non-token traffic falls through to the next chain.
    authConfig.configure(http);

    return http.securityMatcher(new ApiTokenRequestMatcher(properties.getTokenPrefix()))
        .addFilterBefore(apiTokenAuthenticationFilter, BasicAuthenticationFilter.class)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // API clients want a clean 401, not a 302 to /login from the inherited browser entrypoint.
        .exceptionHandling(
            eh -> eh.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
        .build();
  }

  /**
   * Matches requests bearing a Spinnaker API token in {@code X-Spinnaker-Token} or {@code Bearer}.
   */
  static final class ApiTokenRequestMatcher implements RequestMatcher {
    private final String tokenPrefix;
    private final String bearerPrefix;

    ApiTokenRequestMatcher(String tokenPrefix) {
      this.tokenPrefix = tokenPrefix;
      this.bearerPrefix = "Bearer " + tokenPrefix;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
      String xToken = request.getHeader(ApiTokenAuthenticationFilter.HEADER_X_SPINNAKER_TOKEN);
      if (xToken != null && xToken.startsWith(tokenPrefix)) {
        return true;
      }
      String auth = request.getHeader("Authorization");
      return auth != null && auth.startsWith(bearerPrefix);
    }
  }
}
