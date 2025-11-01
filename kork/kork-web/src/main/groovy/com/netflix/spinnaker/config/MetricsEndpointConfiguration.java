/*
 * Copyright 2019 Netflix, Inc.
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
package com.netflix.spinnaker.config;

import com.netflix.spectator.api.Registry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;

@Configuration
@ConditionalOnClass(Registry.class)
@ComponentScan(basePackages = "com.netflix.spectator.controllers")
@Order(Ordered.HIGHEST_PRECEDENCE + 101)
@EnableWebSecurity
public class MetricsEndpointConfiguration {

  @Bean
  public SecurityFilterChain configure(HttpSecurity http) throws Exception {
    // Allow anyone to access the spectator metrics endpoint using Ant-style matcher
    http.securityMatcher(new AntPathRequestMatcher("/spectator/metrics"));
    http.authorizeHttpRequests()
        .requestMatchers(new AntPathRequestMatcher("/spectator/metrics"))
        .permitAll()
        .anyRequest()
        .authenticated();
    return http.build();
  }
}
