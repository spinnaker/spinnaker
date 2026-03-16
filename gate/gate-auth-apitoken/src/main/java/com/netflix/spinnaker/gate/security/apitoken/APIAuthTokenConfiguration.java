/*
 * Copyright 2023 Apple, Inc.
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

package com.netflix.spinnaker.gate.security.apitoken;

import com.netflix.spinnaker.gate.security.SpinnakerAuthConfig;
import lombok.AllArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@ConditionalOnProperty("auth.apitokens.enabled")
@EnableWebSecurity
@SpinnakerAuthConfig
@Configuration
@AllArgsConstructor
public class APIAuthTokenConfiguration {

  private final APIAuthTokenFilter filter;

  @Bean
  public SecurityFilterChain tokenFilterHandler(HttpSecurity http) throws Exception {
    http.addFilterBefore(externalAuthTokenFilter, OAuth2LoginAuthenticationFilter.class);
    return http.build();
  }
}
