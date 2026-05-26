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
import javax.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.util.matcher.RequestMatcher;

/**
 * Dedicated {@link WebSecurityConfigurerAdapter} for API-token requests: builds a {@link
 * SessionCreationPolicy#STATELESS STATELESS} chain whose request matcher only matches Spinnaker
 * token-bearing requests, so token auth never writes to {@code HttpSession} and non-token requests
 * fall through to the default chain. Ordered before {@code OAuth2SsoConfig} and friends.
 */
@ConditionalOnProperty("api-tokens.enabled")
@SpinnakerAuthConfig
@EnableWebSecurity
@Order(50)
public class ApiTokenAuthConfigurerAdapter extends WebSecurityConfigurerAdapter {

  @Autowired AuthConfig authConfig;
  @Autowired ApiTokenAuthenticationFilter apiTokenAuthenticationFilter;
  @Autowired ApiTokenProperties properties;

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    // AuthConfig.configure() sets AnyRequestMatcher; call it first and then narrow.
    authConfig.configure(http);

    http.requestMatcher(new ApiTokenRequestMatcher(properties.getTokenPrefix()))
        .addFilterBefore(apiTokenAuthenticationFilter, BasicAuthenticationFilter.class)
        .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        // API clients want a clean 401, not a 302 to /login from the inherited browser entrypoint.
        .exceptionHandling(
            eh -> eh.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)));
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
