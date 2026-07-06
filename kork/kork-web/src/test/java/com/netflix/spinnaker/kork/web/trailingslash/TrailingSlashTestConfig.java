/*
 * Copyright 2026 Harness, Inc.
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
package com.netflix.spinnaker.kork.web.trailingslash;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Shared test configuration for trailing slash handling tests. Enables auto-configuration (so the
 * production {@code UrlHandlerFilterAutoConfiguration} is applied) and permits all requests so the
 * tests exercise routing rather than security.
 */
@Configuration
@EnableAutoConfiguration
@EnableWebSecurity
class TrailingSlashTestConfig {

  @Bean
  SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
    http.csrf(csrf -> csrf.disable()).headers(headers -> headers.disable());
    http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
    http.anonymous(anon -> anon.disable());
    return http.build();
  }
}
