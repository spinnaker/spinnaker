/*
 * Copyright 2025 Salesforce, Inc.
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

package com.netflix.spinnaker.gate.security.header;

import com.netflix.spinnaker.gate.config.AuthConfig;
import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;

/** Authenticates the {@code X-SPINNAKER-USER} header using roles resolved at login. */
@ConditionalOnProperty("header.enabled")
@Configuration
@SpinnakerAuthConfig
@EnableWebSecurity
public class HeaderAuthConfigurerAdapter {
  @Autowired AuthConfig authConfig;

  @Autowired RequestHeaderAuthenticationFilter requestHeaderAuthenticationFilter;

  /**
   * Ordered ahead of X509Config's catch-all chain ({@code @Order(3)}) and after the API-token chain
   * ({@code @Order(0)}) — see {@code ApiTokenAuthConfigurerAdapter}. The chain is scoped by the
   * request matcher {@link AuthConfig#configure} installs.
   */
  @Bean
  @Order(2)
  public SecurityFilterChain headerSecurityFilterChain(HttpSecurity http) throws Exception {
    authConfig.configure(http);
    http.addFilter(requestHeaderAuthenticationFilter);

    // Header auth is stateless: each request provides X-SPINNAKER-USER (and a signed identity
    // token), so there's no need for callers to support session cookies or to persist a security
    // context between requests.
    http.sessionManagement(
        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    return http.build();
  }
}
