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
 *
 */

package com.netflix.spinnaker.kork.actuator;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@Order(Ordered.HIGHEST_PRECEDENCE + 67)
@EnableWebSecurity
public class ActuatorEndpointsConfiguration {

  @Bean
  public SecurityFilterChain configure(HttpSecurity http) throws Exception {
    // The health endpoint should always be exposed without auth.
    http.securityMatcher(EndpointRequest.to(HealthEndpoint.class));
    http.authorizeHttpRequests()
        .requestMatchers(EndpointRequest.to(HealthEndpoint.class))
        .permitAll()
        .anyRequest()
        .authenticated();
    return http.build();
  }
}
