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
 */

package com.netflix.spinnaker.gate.config;

import static org.springframework.core.Ordered.HIGHEST_PRECEDENCE;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@Order(HIGHEST_PRECEDENCE + 23)
public class ManagedDeliverySchemaEndpointConfiguration {

  @Bean
  @Order(1)
  public SecurityFilterChain schemaSecurityFilterChain(HttpSecurity http) throws Exception {
    return http.securityMatcher(new AntPathRequestMatcher("/managed/delivery-configs/schema"))
        .authorizeRequests()
        .anyRequest()
        .permitAll()
        .and()
        .build();
  }
}
