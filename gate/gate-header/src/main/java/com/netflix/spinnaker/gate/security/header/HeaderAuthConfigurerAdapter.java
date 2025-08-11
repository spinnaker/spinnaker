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
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.RequestHeaderAuthenticationFilter;

/**
 * In combination with HeaderAuthConfigurerAdapter, authenticate the X-SPINNAKER-USER header using
 * permissions obtained from fiat.
 */
@ConditionalOnProperty("header.enabled")
@SpinnakerAuthConfig
@EnableWebSecurity
public class HeaderAuthConfigurerAdapter {
  @Autowired AuthConfig authConfig;

  @Autowired RequestHeaderAuthenticationFilter requestHeaderAuthenticationFilter;

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    authConfig.configure(http);
    http.addFilter(requestHeaderAuthenticationFilter);

    // Save the work to read and write session information.  Each request
    // provides X-SPINNAKER-USER, and gate caches information from fiat, so
    // there's no need for callers to support session cookies, and dealing with
    // expiration, etc.
    //
    // With this, when services.fiat.legacyFallback is false, FiatSessionFilter
    // doesn't ever do meaningful work because request.getSession() always returns
    // null, so save some cycles by setting fiat.session-filter.enabled to false.
    //
    // When services.fiat.legacyFallback is true, FiatSessionFilter still
    // invalidates the cache for the user.
    http.sessionManagement(
        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
    return http.build();
  }
}
